import {
  CanActivate,
  ExecutionContext,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import * as jwt from 'jsonwebtoken';
import { OwnershipResolver } from '../scope/ownership-resolver';
import { Principal } from '../scope/scope.types';
import { BizException } from '../errors/biz-exception';

export interface AuthedRequest extends Request {
  principal?: Principal;
  traceId?: string;
}

/**
 * 第一层：全局认证 Guard（NestJS 请求管道强制环节，任何请求不经过它无法到达 Controller）。
 * - 解析 JWT → 实时取角色/数据范围/部门挂靠（ADR-05，不做授权快照缓存）
 * - 停用账号持旧 token 一律 401（docs/04 §六场景 8）
 *
 * 门禁4 审计修正：
 * - `verify` 显式 `algorithms: ['HS256']`——不显式声明时 jwt 库接受任意算法（含 none/RS 公钥错配攻击面）。
 * - 生产环境缺 `JWT_SECRET` 直接**拒绝启动**（process.exit(1)），不再静默用 dev-secret 兜底
 *   （否则任何知道兜底值的人都能伪造任意用户 token）。
 */
@Injectable()
export class GlobalAuthGuard implements CanActivate {
  private readonly logger = new Logger('GlobalAuthGuard');

  constructor(private readonly resolver: OwnershipResolver) {}

  /** 启动期强校验（main.ts 调用）：生产缺密钥即拒绝启动，fail-closed */
  static assertJwtSecretOrExit(): void {
    const logger = new Logger('GlobalAuthGuard');
    if (!process.env.JWT_SECRET) {
      if ((process.env.NODE_ENV ?? '') === 'production') {
        logger.error(
          '拒绝启动：生产环境必须注入 JWT_SECRET 环境变量（缺失时可伪造任意 token）。',
        );
        process.exit(1);
      }
      logger.warn(
        'JWT_SECRET 未注入，开发环境使用 dev-secret 兜底——仅限本地调试，生产启动会拒绝。',
      );
    }
  }

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const req = context.switchToHttp().getRequest<AuthedRequest>();
    req.traceId = `t-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

    const token = this.extractToken(req);
    if (!token) throw new BizException(401);

    let userId: string;
    try {
      const payload = jwt.verify(token, this.secret(), { algorithms: ['HS256'] }) as {
        sub: string;
      };
      userId = payload.sub;
    } catch {
      throw new BizException(401);
    }

    // 账号状态实时校验（停用账号持旧 token 一律 401，docs/04 §六场景 8）
    const active = await this.resolver.isActive(userId);
    if (!active) throw new BizException(401);

    req.principal = await this.resolver.resolve(userId);
    return true;
  }

  private extractToken(req: AuthedRequest): string | undefined {
    const header = (req.headers as Record<string, string>)?.authorization;
    if (header?.startsWith('Bearer ')) return header.slice(7);
    return undefined;
  }

  /** 密钥解析：生产由启动断言保证必填；此处兜底只为让类型收窄 */
  private secret(): string {
    return process.env.JWT_SECRET ?? 'dev-secret';
  }
}
