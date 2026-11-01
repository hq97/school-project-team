import { Logger } from '@nestjs/common';
import { DataScopeOptions } from './data-scope.options';
import { covers, effectiveScopeOf } from './data-scope.options';
import { Principal } from './scope.types';

/**
 * 第三层：数据层唯一查询出口（ADR §1.5 ③）。
 *
 * 所有列表 / 详情 / 导出 / 大屏聚合 / 下钻查询必须经它注入范围条件，
 * 业务模块禁止裸 prisma 查询（由 eslint-rules/no-raw-sql.js 静态封堵 + 门禁5 Review 复核）。
 *
 * 三种归属类型全实现（docs/04 §4.2 算法 A/B/C/E）：
 *  - ORG 组织型：SELF / GRADE / ALL，含 CLASS 班级载体（班主任 head_teacher.classId 收窄）
 *  - RELATION 关系型：家长-子女 childStudentIds 并集
 *  - ROLE_GRANT 角色授权型：公告 visibleRoles ∩ 用户角色
 *  - PUBLIC 公共对象：仅菜单/按钮控制，不做记录级过滤
 */
export class ScopeQueryBuilder {
  private readonly logger = new Logger('ScopeQueryBuilder');
  private where: Record<string, unknown> = {};

  private constructor(
    private readonly principal: Principal,
    private readonly opts: DataScopeOptions,
  ) {}

  static for(principal: Principal, opts: DataScopeOptions): ScopeQueryBuilder {
    return new ScopeQueryBuilder(principal, opts);
  }

  /**
   * 生成列表/聚合用的 where 条件（Prisma 风格片段）
   *
   * 门禁4 审计修正：展开顺序必须是 `{ ...extra, ...归属条件 }`——归属条件**后置覆盖**。
   * 原写法 `{ ...归属条件, ...extra }` 允许调用方用 `extra` 整体替换掉范围条件，
   * 导出类接口一旦按常规写 `buildWhere({ id: { in: ids } })` 就越权（docs/04 §五.5）。
   * 归属是安全边界，永远最后写入、不可被调用方覆盖。
   */
  buildWhere(extra: Record<string, unknown> = {}): Record<string, unknown> {
    this.where = { ...extra, ...this.applyOwnership() };
    this.applyTerm();
    return this.where;
  }

  /** 单条记录判定：不通过 → 调用方抛 40304（防枚举，docs/04 §4.3） */
  matchRecord(record: Record<string, unknown>): boolean {
    // 接口恒本人（站内信等）：优先于 PUBLIC，且不随角色范围放大（AC-MS7-1）
    if (this.opts.forceSelf) {
      return record[this.opts.ownerField ?? 'ownerId'] === this.principal.userId;
    }
    if (this.opts.ownership === 'PUBLIC') return true;

    if (this.opts.ownership === 'RELATION') {
      const studentId = record['studentId'] as string | undefined;
      return !!studentId && this.principal.childStudentIds.includes(studentId);
    }

    if (this.opts.ownership === 'ROLE_GRANT') {
      const visibleRoles = (record['visibleRoles'] as string[]) ?? [];
      return visibleRoles.some((r) => this.principal.roleCodes.includes(r));
    }

    // ORG
    const effScope = this.effectiveScope();
    if (effScope === 'ALL') return true;

    if (effScope === 'SELF') {
      const owner = record[this.opts.ownerField ?? 'ownerId'];
      return owner === this.principal.userId;
    }

    // GRADE
    const deptId = record[this.opts.deptField ?? 'deptId'] as string | undefined;
    const inDept = !!deptId && this.principal.visibleDeptIds.includes(deptId);
    const owner = record[this.opts.ownerField ?? 'ownerId'] as string | undefined;
    const inOwner = owner === this.principal.userId;
    if (!inDept && !inOwner) return false;

    // 本班载体：班主任把「本年级」收窄到该 CLASS（docs/04 §4.2-B）
    // E3 换届快照：按**记录的 termId**选取对应学期的所带班级，避免跨学期失效
    const classIds = this.classIdsFor(record[this.opts.termField ?? 'termId']);
    if (classIds.length > 0 && this.opts.classField) {
      const classId = record[this.opts.classField] as string | undefined;
      return !!classId && classIds.includes(classId);
    }
    return true;
  }

  /**
   * 班主任所带班级（按学期取值，E3 换届快照）：
   * termId 为空时（记录无学期维度）退化为「当前学期」的班级集合，仍做收窄。
   */
  private classIdsFor(termId?: unknown): string[] {
    const t = typeof termId === 'string' && termId ? termId : this.principal.currentTermId;
    return this.principal.headTeacherTerms
      .filter((h) => !t || !h.termId || h.termId === t)
      .map((h) => h.classId);
  }

  /** 写操作：强制归属人 = 当前用户，忽略前端传入（防伪造 creatorId/applicantId） */
  forceOwner<T extends Record<string, unknown>>(payload: T): T {
    if (!this.opts.write) return payload;
    const field = this.opts.ownerField ?? 'ownerId';
    return { ...payload, [field]: this.principal.userId };
  }

  /** 有效范围：角色范围不完整 → 按最小权限 SELF 兜底 + 告警（AC-RBAC-2） */
  effectiveScope(): 'SELF' | 'GRADE' | 'ALL' {
    if (!this.principal.scopeComplete) {
      this.logger.warn(
        `[AC-RBAC-2] userId=${this.principal.userId} 数据范围不完整，按 SELF 兜底（resource=${this.opts.resource}）`,
      );
    }
    // 单一判定源：禁止各业务点直读 principal.scope（门禁4 审计修正）
    return effectiveScopeOf(this.principal);
  }

  private applyOwnership(): Record<string, unknown> {
    const o = this.opts;
    // 接口恒本人（站内信等）：不随角色数据范围放大，也不因表无 deptId 而生成非法条件（AC-MS7-1）
    if (o.forceSelf) {
      return { [o.ownerField ?? 'ownerId']: this.principal.userId };
    }
    if (o.ownership === 'PUBLIC') return {};

    if (o.ownership === 'RELATION') {
      const ids = this.principal.childStudentIds;
      return { studentId: ids.length ? { in: ids } : { in: ['__NO_CHILD__'] } };
    }

    if (o.ownership === 'ROLE_GRANT') {
      return { visibleRoles: { hasSome: this.principal.roleCodes } };
    }

    const scope = this.effectiveScope();
    if (scope === 'ALL') return {};
    if (scope === 'SELF') {
      return { [o.ownerField ?? 'ownerId']: this.principal.userId };
    }
    // GRADE
    const cond: Record<string, unknown> = {
      OR: [
        { [o.deptField ?? 'deptId']: { in: this.principal.visibleDeptIds } },
        { [o.ownerField ?? 'ownerId']: this.principal.userId },
      ],
    };
    // 本班载体收窄（班主任）：列表场景按当前学期取所带班级（E3）
    const classIds = this.classIdsFor();
    if (classIds.length > 0 && o.classField) {
      cond[this.opts.classField as string] = { in: classIds };
    }
    return cond;
  }

  /** 学期口径：聚合与鉴权一律先按学期+周次（ADR §4.5），自然月仅展示层重排 */
  private applyTerm(): void {
    if (this.opts.termField && this.principal.currentTermId) {
      this.where[this.opts.termField] = this.principal.currentTermId;
    }
  }

  /** 供 Guard 判定：角色有效范围是否满足接口声明的最低要求 */
  satisfies(): boolean {
    if (this.opts.ownership === 'PUBLIC') return true;
    if (!this.opts.requiredScope) return true;
    return covers(this.effectiveScope(), this.opts.requiredScope);
  }
}
