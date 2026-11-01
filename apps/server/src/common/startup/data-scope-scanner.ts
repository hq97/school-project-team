import { INestApplication, Logger } from '@nestjs/common';
import { ModulesContainer } from '@nestjs/core';
import {
  DATA_SCOPE_METADATA,
  PUBLIC_DATA_SCOPE_METADATA,
} from '../decorators/data-scope.decorator';

const PATH_METADATA = 'path';
const METHOD_METADATA = 'method';

export interface ScanReport {
  totalRoutes: number;
  declared: number;
  missing: string[];
}

/**
 * 第二层的 fail-closed 开关：启动期扫描全部路由，
 * **任一条缺 @DataScope() 声明（且未显式 @PublicDataScope 豁免）即拒绝启动**。
 *
 * 这是「漏配 = 裸奔」的根治手段——不是告警，是起不来（ADR §1.5 ②）。
 *
 * 门禁4 审计修正：
 *  - 原实现 `Object.getOwnPropertyNames(proto)` 只取一层原型，**继承基类的 controller 路由会漏扫**；
 *    现沿原型链逐层收集方法（直到 Object.prototype）。
 *  - 原实现允许**类级** `@DataScope` 让全类方法免检——一个 PUBLIC 类声明 = 全类零过滤。
 *    现要求声明必须落在**方法级**：类级声明视为无效（会被记入 missing，拒绝启动），
 *    防止"写在类上以为全类生效、实际语义是全类免检"的安全错觉。
 */
export class DataScopeStartupScanner {
  private static readonly logger = new Logger('DataScopeStartupScanner');

  static scan(app: INestApplication): ScanReport {
    const modules = app.get(ModulesContainer);
    const missing: string[] = [];
    const classLevelOnly: string[] = [];
    let totalRoutes = 0;
    let declared = 0;

    for (const [, moduleRef] of modules) {
      for (const [, wrapper] of moduleRef.controllers) {
        const metatype = wrapper.metatype as
          | { name: string; prototype: unknown }
          | undefined;
        if (!metatype) continue;

        const basePath: string =
          (Reflect.getMetadata(PATH_METADATA, metatype) as string) ?? '/';

        // 沿原型链收集全部自有方法（含继承的基类路由方法）
        const seen = new Set<string>();
        let proto = metatype.prototype as Record<string, unknown> | null;
        while (proto && proto !== Object.prototype) {
          for (const key of Object.getOwnPropertyNames(proto)) {
            if (key === 'constructor' || seen.has(key)) continue;
            seen.add(key);
            const fn = proto[key] as unknown;
            if (typeof fn !== 'function') continue;

            const subPath = Reflect.getMetadata(PATH_METADATA, fn) as string | undefined;
            const method = Reflect.getMetadata(METHOD_METADATA, fn) as number | undefined;
            if (subPath === undefined || method === undefined) continue; // 非路由方法

            totalRoutes++;
            const routeLabel = `${method} ${basePath}${subPath === '/' ? '' : `/${subPath}`} → ${metatype.name}#${key}`;
            // 声明必须落在方法级；类级声明不作为放行依据（见类注释）
            const hasMethodScope = !!Reflect.getMetadata(DATA_SCOPE_METADATA, fn);
            const hasClassScope = !!Reflect.getMetadata(DATA_SCOPE_METADATA, metatype);
            const isPublic = !!Reflect.getMetadata(PUBLIC_DATA_SCOPE_METADATA, fn);

            if (hasMethodScope) {
              declared++;
            } else if (hasClassScope && !isPublic) {
              classLevelOnly.push(routeLabel);
              missing.push(routeLabel);
            } else if (!isPublic) {
              missing.push(routeLabel);
            }
          }
          proto = Object.getPrototypeOf(proto) as Record<string, unknown> | null;
        }
      }
    }

    if (classLevelOnly.length > 0) {
      for (const m of classLevelOnly) {
        this.logger.error(
          `类级 @DataScope() 不生效，必须声明到方法级（防全类免检）：${m}`,
        );
      }
    }

    return { totalRoutes, declared, missing };
  }

  /** 有缺失即终止进程：fail-closed */
  static assertOrExit(app: INestApplication): ScanReport {
    const report = this.scan(app);
    this.logger.log(
      `DataScope 声明扫描：路由 ${report.totalRoutes} 条，已声明 ${report.declared} 条，缺失 ${report.missing.length} 条`,
    );
    if (report.missing.length > 0) {
      for (const m of report.missing) {
        this.logger.error(`缺少 @DataScope() 声明：${m}`);
      }
      this.logger.error(
        '拒绝启动：所有涉及数据读写的路由必须声明 @DataScope()，或显式标记 @PublicDataScope(reason)（ADR §1.5 ②）。',
      );
      process.exit(1);
    }
    return report;
  }
}
