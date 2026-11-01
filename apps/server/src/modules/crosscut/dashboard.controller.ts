import { Controller, Get, Query, Req } from '@nestjs/common';
import { Menu } from '../../common/decorators/require.decorator';
import { DataScope, PublicDataScope } from '../../common/decorators/data-scope.decorator';
import { ScopeRequest } from '../../common/auth/data-scope.guard';
import { effectiveScopeOf } from '../../common/scope/data-scope.options';

/** 角色 → 大屏模板 key（docs/14 §一/§五 + docs/03 注 6） */
const TEMPLATE_BY_ROLE: Record<string, string> = {
  'R-PRINCIPAL': 'PRINCIPAL',
  'R-VP': 'VP',
  'R-DEAN': 'DEAN',
  'R-LOGISTICS': 'LOGISTICS',
  'R-MORAL': 'MORAL',
  'R-PARTY': 'PARTY',
  'R-GRADE': 'GRADE',
  'R-SUBJECT': 'SUBJECT',
};

export type StatDim = 'TERM' | 'MONTH';

/**
 * 横切 · 数据大屏（M15）
 *
 * 架构红线（ADR §4.7）：
 *  - templateKey 只是「布局指针」，不是授权：教师/学生/家长无 M15 菜单 → 全局 PermissionGuard 直接 40301；
 *    NONE 仅服务「有 M15 菜单但无预置模板」的自建角色（R-CUSTOM-*）。
 *  - 服务端聚合 + 数据范围过滤，前端模板表不得含任何口径/过滤/角色分支。
 *  - dim 默认 TERM；MONTH 仅在服务端完成学期聚合与鉴权后再做月粒度重排（AC-DD1-2）。
 */
@Controller('api/dashboard')
export class DashboardController {
  @Get('overview')
  @Menu('M15')
  @DataScope({ resource: 'dashboard_metric', ownership: 'ORG' })
  async overview(@Req() req: ScopeRequest, @Query('dim') dim: string = 'TERM') {
    const principal = req.principal!;
    const statDim: StatDim = dim === 'MONTH' ? 'MONTH' : 'TERM'; // 默认学期，禁止 MONTH 作默认
    const templateKey =
      principal.roleCodes.map((r) => TEMPLATE_BY_ROLE[r]).find(Boolean) ?? 'NONE';

    // 展示给用户的范围必须是**有效范围**（角色配置不完整时按 SELF 兜底），
    // 否则会出现"显示全校、实际只给本人"的口径错位（AC-RBAC-2）
    const scope = effectiveScopeOf(principal);

    return {
      roleCode: principal.roleCodes[0] ?? null,
      templateKey,
      dataAsOf: new Date().toISOString(),
      // 骨架：指标聚合由大屏聚合服务实现（本片只落权限与口径骨架）
      kpis: [],
      charts: [],
      alerts: [],
      scope,
      scopeText: { SELF: '本人', GRADE: '本年级', ALL: '全校' }[scope],
      currentTerm: principal.currentTermId,
      dim: statDim,
    };
  }

  /** 下钻：明细仍过 ScopeQueryBuilder，drillTo 由服务端下发但不构成授权 */
  @Get('drill')
  @Menu('M15')
  @DataScope({ resource: 'dashboard_metric', ownership: 'ORG' })
  async drill(
    @Req() req: ScopeRequest,
    @Query('kpiCode') kpiCode: string,
    @Query('dim') dim: string = 'TERM',
  ) {
    const where = req.scopeQuery!.buildWhere();
    // 门禁4 审计修正：where 内含 visibleDeptIds / ownerId，**不得回显前端**（会泄露部门树结构）。
    // 范围信息只下发 scope 文本（本人/本年级/全校），明细仍按 where 取数，只是不回传条件本身。
    void where;
    const scope = effectiveScopeOf(req.principal!);
    return {
      kpiCode,
      dim: dim === 'MONTH' ? 'MONTH' : 'TERM',
      scope,
      scopeText: { SELF: '本人', GRADE: '本年级', ALL: '全校' }[scope],
      items: [],
    };
  }

  /** 当前学期/周次：不读业务数据，显式豁免 DataScope 声明 */
  @Get('term/current')
  @Menu('M15')
  @PublicDataScope('仅下发当前学期与当前周次字典，不读业务数据')
  async termCurrent(@Req() req: ScopeRequest) {
    return { termId: req.principal!.currentTermId };
  }
}
