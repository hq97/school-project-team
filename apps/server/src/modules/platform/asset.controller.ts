import { Body, Controller, Get, Logger, Param, Post, Query, Req } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Button, Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { BizException } from '../../common/errors/biz-exception';
import { AssetType } from '@prisma/client';
import { BorrowAssetDto } from './dto/borrow-asset.dto';

/** 资产类型 → 借用域 → 所需权限点（严格域隔离，docs/04 §3.9 B5） */
const DOMAIN_RULE: Record<AssetType, { domain: string; button: string; menu: string }> = {
  MATERIAL: { domain: 'logistics', button: 'BTN-MAT-APPLY', menu: 'M0301' },
  LAB_EQUIP: { domain: 'science', button: 'BTN-SCI-LAB-BOOK', menu: 'M1302' },
  SPORT_EQUIP: { domain: 'sport', button: 'BTN-SPORT-EQUIP-BORROW', menu: 'M1404' },
};

/** 平台 · 统一资产台账 asset（全校共享 + 域隔离，不套三级） */
@Controller('api/assets')
export class AssetController {
  private readonly logger = new Logger('AssetController');

  constructor(private readonly prisma: PrismaService) {}

  @Get()
  @Menu('M0303')
  @DataScope({ resource: 'asset', ownership: 'PUBLIC' })
  async list(
    @Req() req: ScopeRequest,
    @Query('assetType') assetType?: string,
    @Query('keyword') keyword?: string,
  ) {
    const principal = req.principal!;
    // 白名单 = 当前用户持有对应借用权限点的域
    const visible = (Object.keys(DOMAIN_RULE) as AssetType[]).filter((t) =>
      principal.buttonCodes.includes(DOMAIN_RULE[t].button),
    );
    // 门禁4 审计修正：入参只能**收窄**白名单，不能放大。
    // 原写法 `assetType ? [assetType] : visible` 让入参直接覆盖白名单 → 无该域权限点也能跨域枚举。
    const types = assetType
      ? visible.filter((t) => t === (assetType as AssetType))
      : visible;
    // 显式请求了无权域 → 按无按钮权限处理（40302），而不是静默返回全量
    if (assetType && types.length === 0) throw BizException.actionDenied();
    if (types.length === 0) return [];
    return this.prisma.asset.findMany({
      where: {
        assetType: { in: types },
        ...(keyword ? { name: { contains: keyword } } : {}),
      },
      take: 50,
    });
  }

  /** 借用：assetType × 借用权限点双重校验，跨域 40302；扣减失败 40903 */
  @Post(':id/borrow')
  @Menu('M0301')
  @Button('BTN-MAT-APPLY')
  @DataScope({ resource: 'asset', ownership: 'PUBLIC' })
  async borrow(@Req() req: ScopeRequest, @Param('id') id: string, @Body() body: BorrowAssetDto) {
    const asset = await this.prisma.asset.findUnique({ where: { id } });
    if (!asset) throw BizException.notFound();

    const rule = DOMAIN_RULE[asset.assetType];
    // 域隔离：借用权限点必须匹配该资产所属域，跨域直接拒绝
    if (!req.principal!.buttonCodes.includes(rule.button)) {
      throw BizException.actionDenied();
    }

    // quantity 由 DTO 保证为正整数（@IsInt @Min(1)）：负数量会把 `gte` 恒真 + `decrement` 反向加库存
    const quantity = body.quantity;
    const result = await this.prisma.asset.updateMany({
      where: { id, availableQty: { gte: quantity } },
      data: { availableQty: { decrement: quantity } },
    });
    if (result.count === 0) {
      const fresh = await this.prisma.asset.findUnique({ where: { id } });
      throw BizException.stockShort({
        name: asset.name,
        availableQty: fresh?.availableQty ?? 0, // 实时读值，不经缓存
        requestedQty: quantity,
      });
    }
    return { borrowId: body.applyNo ?? '', assetId: id, quantity };
  }

  /**
   * 归还：仅借用人本人（SELF），回补 availableQty（docs/04 §4.2-D14）
   *
   * 门禁4 审计修正：原实现 `void req` 完全没做归属校验，持有 M0302 的任何人可归还他人借用
   * 并回补库存。现强制：取借用记录 → 归属判定 → 不通过 40304（防枚举）。
   */
  @Post(':id/return')
  @Menu('M0302')
  @DataScope({
    resource: 'asset_borrow',
    ownership: 'ORG',
    ownerField: 'borrowerId',
    requiredScope: 'SELF',
  })
  async returnAsset(@Req() req: ScopeRequest, @Param('id') id: string) {
    const borrow = await this.prisma.asset_borrow.findUnique({ where: { id } });
    if (!borrow || borrow.returnedAt) throw BizException.notFound();

    // 双保险：① ScopeQueryBuilder 归属判定（继承角色数据范围）；② 借用人本人硬校验
    const passed =
      borrow.borrowerId === req.principal!.userId &&
      req.scopeQuery!.matchRecord(borrow as unknown as Record<string, unknown>);
    if (!passed) {
      this.logger.warn(
        `[D14] userId=${req.principal!.userId} 尝试归还他人借用 borrowId=${id}（owner=${borrow.borrowerId}），已拒绝`,
      );
      throw BizException.notFound(); // 40304：与「不存在」同码，防枚举（docs/04 §4.3）
    }

    await this.prisma.asset_borrow.update({
      where: { id },
      data: { returnedAt: new Date() },
    });
    // 回补库存：单条原子 UPDATE（C9 ① 同款写法，不用裸 SQL 以免踩 no-raw-sql 规则）
    await this.prisma.asset.update({
      where: { id: borrow.assetId },
      data: { availableQty: { increment: borrow.quantity } },
    });
    return { returned: true, borrowId: id, assetId: borrow.assetId, quantity: borrow.quantity };
  }
}
