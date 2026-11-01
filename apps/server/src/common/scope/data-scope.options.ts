import { DataScopeKey, DATA_SCOPE_METADATA } from '../decorators/data-scope.decorator';
import { OwnershipType, Principal, ScopeLevel } from './scope.types';

export { DataScopeKey, DATA_SCOPE_METADATA };

/** @DataScope 声明的解析结果（供 Guard 与 ScopeQueryBuilder 消费） */
export interface DataScopeOptions {
  /** 数据对象键，如 procurement_project / asset / space / notice */
  resource: string;
  ownership: OwnershipType;
  /** 最低所需范围；缺省时按角色有效范围 MAX 判定 */
  requiredScope?: ScopeLevel;
  /** 组织型：归属人字段 / 归属部门字段 / 班级载体字段 */
  ownerField?: string;
  deptField?: string;
  classField?: string;
  /** 学期口径字段：聚合与鉴权一律先按学期+周次（ADR §4.5） */
  termField?: string;
  /**
   * 接口恒定按「本人」过滤，不随角色数据范围放大（如站内信 AC-MS7-1）。
   *
   * 这不是第四级数据范围（docs/03 §2.1-5 明令禁止新增第四级），而是**接口级**约束：
   * 某些对象（站内信、我的待办）语义上就只属于接收人本人，校长也不该看到他人站内信。
   * 不加这个开关时，ALL 角色 `applyOwnership()` 返回 `{}`（可读全校），
   * GRADE 角色又会因该表无 deptId 触发 Prisma 校验异常 → 500（门禁4 审计项）。
   */
  forceSelf?: boolean;
  /** 写操作：强制归属人 = 当前用户，忽略前端传入 */
  write?: boolean;
}

const SCOPE_RANK: Record<ScopeLevel, number> = { SELF: 1, GRADE: 2, ALL: 3 };

export function maxScope(a: ScopeLevel, b: ScopeLevel): ScopeLevel {
  return SCOPE_RANK[a] >= SCOPE_RANK[b] ? a : b;
}

export function covers(userScope: ScopeLevel, required: ScopeLevel): boolean {
  return SCOPE_RANK[userScope] >= SCOPE_RANK[required];
}

/**
 * 有效范围的**唯一判定源**（AC-RBAC-2）：角色数据范围配置不完整 → 一律按最小权限 SELF。
 *
 * 审计发现（门禁4）：此前 `space-occupancy.service` 直接用 `principal.scope` 做脱敏判定，
 * 绕过了 SELF 降级，导致红线脱敏失效。故抽成公共函数——任何"按范围做安全判定"的地方
 * 都必须调用它，禁止再直读 `principal.scope`（由门禁5 Review + 本注释共同约束）。
 */
export function effectiveScopeOf(principal: Principal): ScopeLevel {
  return principal.scopeComplete ? principal.scope : 'SELF';
}
