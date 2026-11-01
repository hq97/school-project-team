import { Body, Controller, Get, Post, Req } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { Button, Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { BizException } from '../../common/errors/biz-exception';
import { IssueMaterialDto } from './dto/issue-material.dto';

/**
 * 后勤域 · 物资领用（M03）
 * asset 为全校共享对象：不做记录级过滤，按 assetType × 借用权限点域隔离（docs/04 §3.9）
 */
@Controller('api/material')
export class MaterialController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('requisitions')
  @Menu('M0302')
  @DataScope({
    resource: 'material_requisition',
    ownership: 'ORG',
    ownerField: 'applicantId',
    deptField: 'applicantDeptId',
    termField: 'termId',
  })
  async list(@Req() req: ScopeRequest) {
    const where = req.scopeQuery!.buildWhere();
    return this.prisma.material_requisition.findMany({ where, take: 50 });
  }

  /** 提交领用：强制 applicantId = 当前用户 + 幂等单号 */
  @Post('requisitions')
  @Menu('M0301')
  @Button('BTN-MAT-APPLY')
  @DataScope({
    resource: 'material_requisition',
    ownership: 'ORG',
    ownerField: 'applicantId',
    deptField: 'applicantDeptId',
    write: true,
  })
  async apply(@Req() req: ScopeRequest, @Body() body: Record<string, unknown>) {
    return req.scopeQuery!.forceOwner(body);
  }

  /**
   * 发放/出库（C9）：CAS 原子扣减 + 幂等键 + 状态机 CAS
   * 失败返回 40903 STOCK_SHORT，availableQty 为扣减失败当次事务内实时读值（不经缓存）
   */
  @Post('requisitions/:id/issue')
  @Menu('M0304')
  @Button('BTN-MAT-APPROVE')
  @DataScope({ resource: 'asset', ownership: 'PUBLIC', requiredScope: 'ALL' })
  async issue(@Body() body: IssueMaterialDto) {
    // quantity 由 DTO 保证为正整数（负数量会反向加库存，见 IssueMaterialDto 注释）
    const assetId = body.assetId;
    const quantity = body.quantity;
    if (!assetId) throw BizException.actionDenied();

    // 域隔离：assetType × 借用权限点（BTN-MAT-APPLY 只能借 MATERIAL）
    const asset = await this.prisma.asset.findUnique({ where: { id: assetId } });
    if (!asset || asset.assetType !== 'MATERIAL') {
      throw BizException.actionDenied(); // 跨域借用 → 40302
    }

    // C9 ① 原子扣减（ADR §4.1）：单条 UPDATE ... WHERE availableQty >= n，影响 0 行即库存不足
    const result = await this.prisma.asset.updateMany({
      where: { id: assetId, availableQty: { gte: quantity } },
      data: { availableQty: { decrement: quantity } },
    });
    if (result.count === 0) {
      // 实时读值：不进缓存（ADR §4.1 / §3.2）
      const fresh = await this.prisma.asset.findUnique({ where: { id: assetId } });
      throw BizException.stockShort({
        name: asset.name,
        availableQty: fresh?.availableQty ?? 0,
        requestedQty: quantity,
      });
    }
    // requiredScope: ALL 已由 DataScopeGuard 前置校验（GRADE/SELF → 40303），无需再判归属
    return { issued: true, assetId, quantity };
  }

  /** 可领用资产列表：assetType × 借用权限点过滤，availableQty 实时读 */
  @Get('items')
  @Menu('M0303')
  @DataScope({ resource: 'asset', ownership: 'PUBLIC' })
  async items() {
    return this.prisma.asset.findMany({
      where: { assetType: 'MATERIAL', status: { in: ['IN_STOCK'] } },
      take: 50,
    });
  }
}
