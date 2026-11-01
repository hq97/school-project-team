import { SetMetadata } from '@nestjs/common';
import { DataScopeOptions } from '../scope/data-scope.options';

export const DATA_SCOPE_METADATA = 'school:data-scope';
export const PUBLIC_DATA_SCOPE_METADATA = 'school:public-data-scope';

export const DataScopeKey = DATA_SCOPE_METADATA;

/**
 * 每个涉及数据读写的接口必须显式声明归属类型与范围要求（ADR §1.5 第二层）。
 *
 * fail-closed：启动期 DataScopeStartupScanner 扫描全部路由，
 * 任一条缺本声明即拒绝启动（除非显式标记 @PublicDataScope，仅用于无数据读写的静态接口）。
 *
 * 前端 v-auth 隐藏按钮不构成任何安全边界；真正的门在这里 + DataScopeGuard + ScopeQueryBuilder。
 */
export const DataScope = (options: DataScopeOptions) =>
  SetMetadata(DATA_SCOPE_METADATA, options);

/** 豁免声明：仅限「不读不写业务数据」的接口（如 /api/dashboard/term/current、健康检查） */
export const PublicDataScope = (reason: string) =>
  SetMetadata(PUBLIC_DATA_SCOPE_METADATA, reason);
