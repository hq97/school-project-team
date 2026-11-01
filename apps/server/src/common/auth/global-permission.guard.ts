import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { BizException } from '../errors/biz-exception';
import {
  REQUIRED_BUTTON_METADATA,
  REQUIRED_MENU_METADATA,
} from '../decorators/require.decorator';
import { AuthedRequest } from '../auth/global-auth.guard';

/**
 * 第一层（续）：菜单/按钮权限全局 Guard。
 * - 菜单权限不足 → 40301
 * - 按钮/操作权限不足 → 40302
 *
 * 前端 v-auth 隐藏按钮只是体验优化，这里的校验才是安全边界（docs/01 AC-E5-1、docs/05 关键边界 2）。
 */
@Injectable()
export class GlobalPermissionGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<AuthedRequest>();
    const principal = req.principal;
    if (!principal) throw new BizException(401);

    const menuCode = this.reflector.getAllAndOverride<string | undefined>(
      REQUIRED_MENU_METADATA,
      [context.getHandler(), context.getClass()],
    );
    if (menuCode && !principal.menuCodes.includes(menuCode)) {
      throw BizException.menuDenied();
    }

    const buttonCode = this.reflector.getAllAndOverride<string | undefined>(
      REQUIRED_BUTTON_METADATA,
      [context.getHandler(), context.getClass()],
    );
    if (buttonCode && !principal.buttonCodes.includes(buttonCode)) {
      throw BizException.actionDenied();
    }

    return true;
  }
}
