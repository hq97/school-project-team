import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { BizException } from '../errors/biz-exception';
import { DATA_SCOPE_METADATA, PUBLIC_DATA_SCOPE_METADATA } from '../decorators/data-scope.decorator';
import { DataScopeOptions } from '../scope/data-scope.options';
import { ScopeQueryBuilder } from '../scope/scope-query-builder';
import { AuthedRequest } from './global-auth.guard';

export interface ScopeRequest extends AuthedRequest {
  scopeQuery?: ScopeQueryBuilder;
}

/**
 * 第二层：数据范围 Guard（ADR §1.5 ②）。
 *
 * 启动期已保证「缺 @DataScope 声明 → 拒绝启动」，因此运行到这里必然有声明；
 * 本 Guard 负责：
 *  - 校验角色有效范围是否覆盖接口声明的最低要求 → 40303
 *  - 角色范围不完整 → 按最小权限 SELF 兜底并打告警日志（AC-RBAC-2）
 *  - 把 ScopeQueryBuilder 挂到请求上，业务代码只能用它取数（唯一出口）
 */
@Injectable()
export class DataScopeGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<ScopeRequest>();
    const principal = req.principal;
    if (!principal) throw new BizException(401);

    const opts = this.reflector.getAllAndOverride<DataScopeOptions | undefined>(
      DATA_SCOPE_METADATA,
      [context.getHandler(), context.getClass()],
    );

    // 未声明且未豁免 → 启动期已拦截；这里再兜一次，防运行期动态挂载绕过
    if (!opts) {
      const reason = this.reflector.getAllAndOverride<string | undefined>(
        PUBLIC_DATA_SCOPE_METADATA,
        [context.getHandler(), context.getClass()],
      );
      if (!reason) {
        throw new Error(
          `[DataScopeGuard] 路由缺少 @DataScope() 声明：${
            context.getClass().name
          }#${String(context.getHandler().name)}`,
        );
      }
      return true;
    }

    const builder = ScopeQueryBuilder.for(principal, opts);
    if (!builder.satisfies()) {
      throw BizException.scopeDenied();
    }

    req.scopeQuery = builder;
    return true;
  }
}
