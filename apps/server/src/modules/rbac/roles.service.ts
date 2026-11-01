import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../common/prisma/prisma.service';
import { BizException } from '../../common/errors/biz-exception';
import { BizCode } from '../../common/errors/biz-codes';
import { OwnershipType, ScopeLevel } from '../../common/scope/scope.types';

export interface SaveRoleDto {
  code: string;
  name: string;
  menuCodes: string[];
  buttonCodes: string[];
  dataScope?: ScopeLevel;
  ownership?: OwnershipType;
}

const VALID_SCOPES: ScopeLevel[] = ['SELF', 'GRADE', 'ALL'];

/**
 * 角色保存校验器（docs/03 §2.1 服务端硬约束；ADR §3.4 写路径落点）
 *
 * 三项齐全，缺一拒存并指明缺失项（AC-RBAC-1）：
 *   ① 菜单授权 ② 按钮授权 ③ 三级数据范围（含归属类型）
 * 另：数据范围不完整的角色不得被授予「列表/详情/导出」类菜单（AC-RBAC-2 前半）
 *     预置 R-* 编码删除拒绝（AC-RBAC-3）；数据范围不得出现第四级（AC-RBAC-4）
 * 读路径兜底在 DataScopeGuard / ScopeQueryBuilder：按 SELF + 告警（AC-RBAC-2 后半）
 */
@Injectable()
export class RoleService {
  private readonly logger = new Logger('RoleService');

  constructor(private readonly prisma: PrismaService) {}

  async save(dto: SaveRoleDto): Promise<{ id: string; code: string }> {
    const missing: string[] = [];
    if (!dto.menuCodes?.length) missing.push('菜单授权');
    if (!dto.buttonCodes?.length) missing.push('按钮授权');
    if (!dto.dataScope || !dto.ownership) missing.push('三级数据范围（含归属类型）');

    if (missing.length > 0) {
      throw new BizException(BizCode.PARAM_INVALID, { missing } as never, `角色保存被拒：缺少 ${missing.join('、')}`);
    }

    // AC-RBAC-4：不新增第四级
    if (dto.dataScope && !VALID_SCOPES.includes(dto.dataScope)) {
      throw new BizException(
        BizCode.PARAM_INVALID,
        undefined,
        `数据范围只能是 SELF / GRADE / ALL，不接受第四级（收到 ${dto.dataScope}）`,
      );
    }

    // AC-RBAC-2 前半：数据范围不完整的角色不得授予列表/详情/导出类菜单
    const listLikeMenus = await this.prisma.sys_menu.findMany({
      where: { code: { in: dto.menuCodes }, listLike: true },
      select: { code: true, name: true },
    });
    const scopeComplete = !!dto.dataScope && !!dto.ownership;
    if (!scopeComplete && listLikeMenus.length > 0) {
      throw new BizException(
        BizCode.PARAM_INVALID,
        { listLikeMenus: listLikeMenus.map((m) => m.code) } as never,
        `数据范围未配置完整，不得授予列表/详情/导出类菜单：${listLikeMenus.map((m) => m.name).join('、')}`,
      );
    }

    // 自建角色建议 R-CUSTOM-* 前缀（AC-RBAC-3 软约束，仅告警）
    if (!dto.code.startsWith('R-')) {
      this.logger.warn(`[AC-RBAC-3] 角色编码 ${dto.code} 未使用 R-* / R-CUSTOM-* 前缀，建议规范命名`);
    }

    const role = await this.prisma.sys_role.upsert({
      where: { code: dto.code },
      create: {
        code: dto.code,
        name: dto.name,
        dataScope: dto.dataScope ?? null,
        scopeComplete,
        builtin: dto.code.startsWith('R-') && !dto.code.startsWith('R-CUSTOM-'),
      },
      update: {
        name: dto.name,
        dataScope: dto.dataScope ?? null,
        scopeComplete,
      },
    });

    await this.prisma.role_menu.deleteMany({ where: { roleId: role.id } });
    await this.prisma.role_button.deleteMany({ where: { roleId: role.id } });
    await this.prisma.role_menu.createMany({
      data: (
        await this.prisma.sys_menu.findMany({
          where: { code: { in: dto.menuCodes } },
          select: { id: true },
        })
      ).map((m) => ({ roleId: role.id, menuId: m.id })),
    });
    await this.prisma.role_button.createMany({
      data: (
        await this.prisma.sys_button.findMany({
          where: { code: { in: dto.buttonCodes } },
          select: { id: true },
        })
      ).map((b) => ({ roleId: role.id, buttonId: b.id })),
    });

    return { id: role.id, code: role.code };
  }

  /** AC-RBAC-3：预置 R-* 编码不允许删除（DB 另有触发器兜底） */
  async remove(code: string): Promise<void> {
    const role = await this.prisma.sys_role.findUnique({ where: { code } });
    if (!role) throw BizException.notFound();
    if (role.builtin) {
      throw new BizException(
        BizCode.PARAM_INVALID,
        undefined,
        `预置角色编码 ${code} 不允许删除，仅可改名或调整授权（AC-RBAC-3）`,
      );
    }
    await this.prisma.sys_role.delete({ where: { code } });
  }
}
