import { Body, Controller, Delete, Param, Post } from '@nestjs/common';
import { Button } from '../../common/decorators/require.decorator';
import { Menu } from '../../common/decorators/require.decorator';
import { DataScope } from '../../common/decorators/data-scope.decorator';
import { BizException } from '../../common/errors/biz-exception';
import { RoleService, SaveRoleDto } from './roles.service';

/**
 * 系统管理 · 角色管理（M0502）
 * 系统管理对象不套三级，仅由菜单/按钮权限控制（docs/04 §3.8）
 */
@Controller('api/system/roles')
export class RolesController {
  constructor(private readonly roles: RoleService) {}

  /** 角色保存：三项齐全校验在 RoleService（AC-RBAC-1/2） */
  @Post()
  @Menu('M0502')
  @Button('BTN-SYS-ROLE-EDIT')
  @DataScope({ resource: 'sys_role', ownership: 'PUBLIC', requiredScope: 'ALL' })
  async save(@Body() dto: SaveRoleDto) {
    try {
      return await this.roles.save(dto);
    } catch (e) {
      if (e instanceof BizException) throw e;
      throw e;
    }
  }

  /** 角色删除：预置 R-* 拒绝（AC-RBAC-3） */
  @Delete(':code')
  @Menu('M0502')
  @Button('BTN-SYS-ROLE-DEL')
  @DataScope({ resource: 'sys_role', ownership: 'PUBLIC', requiredScope: 'ALL' })
  async remove(@Param('code') code: string) {
    await this.roles.remove(code);
    return { deleted: true, code };
  }
}
