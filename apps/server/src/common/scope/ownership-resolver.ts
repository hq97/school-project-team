import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { Principal, ScopeLevel } from './scope.types';
import { maxScope } from './data-scope.options';

/**
 * 请求主体解析器：每次请求实时读库计算，不做授权快照缓存（ADR-05 / docs/04 §2.2）。
 *
 * 判定算法 A/B/C（docs/04 §4.2）所需的全部载体在这里一次联查取齐：
 *  - 组织型：部门挂靠 + 向上找 GRADE 节点 + 可见子树（含 CLASS/SUBJECT）
 *  - 本班载体：head_teacher.classId（按学期，换届快照 E3）
 *  - 关系型：student_parent 子女并集
 */
@Injectable()
export class OwnershipResolver {
  private readonly logger = new Logger('OwnershipResolver');

  constructor(private readonly prisma: PrismaService) {}

  /** 账号状态实时校验：停用账号持旧 token 一律拒绝（docs/04 §六场景 8） */
  async isActive(userId: string): Promise<boolean> {
    const user = await this.prisma.sys_user.findUnique({
      where: { id: userId },
      select: { accountStatus: true },
    });
    return !!user && user.accountStatus === 'ACTIVE';
  }

  async resolve(userId: string): Promise<Principal> {
    const [user, currentTerm] = await Promise.all([
      this.prisma.sys_user.findUnique({
        where: { id: userId },
        include: {
          depts: true,
          roles: { include: { role: true } },
          headTeacher: true,
          parentLinks: true,
        },
      }),
      this.prisma.sys_dict.findFirst({
        where: { dictCode: 'DICT-TERM', isCurrent: true, enabled: true },
      }),
    ]);

    if (!user) throw new Error(`用户不存在: ${userId}`);

    const roleCodes = user.roles.map((ur) => ur.role.code);
    const menuCodes = (
      await this.prisma.role_menu.findMany({
        where: { role: { code: { in: roleCodes } } },
        include: { menu: true },
      })
    ).map((rm) => rm.menu.code);
    const buttonCodes = (
      await this.prisma.role_button.findMany({
        where: { role: { code: { in: roleCodes } } },
        include: { button: true },
      })
    ).map((rb) => rb.button.code);

    // 有效数据范围 = MAX(各角色范围)（docs/04 §2.2 取并集）
    let scope: ScopeLevel = 'SELF';
    let scopeComplete = true;
    for (const ur of user.roles) {
      const rs = ur.role.dataScope as ScopeLevel | null;
      if (!rs || !ur.role.scopeComplete) {
        // AC-RBAC-2：缺数据范围的角色 → 运行时按最小权限 SELF 兜底 + 告警
        scopeComplete = false;
        this.logger.warn(
          `[AC-RBAC-2] 角色 ${ur.role.code} 数据范围不完整，按最小权限 SELF 兜底（userId=${userId}）`,
        );
        continue;
      }
      scope = maxScope(scope, rs);
    }

    const deptIds = user.depts.map((d) => d.deptId);
    const gradeNodeIds = await this.resolveGradeNodes(deptIds);
    const visibleDeptIds = await this.resolveSubtree(gradeNodeIds, deptIds);

    const termId = currentTerm?.itemCode ?? '';
    // E3 换届快照：全量返回（含历史学期），由 ScopeQueryBuilder 按记录的 termId 选取，
    // 避免查历史学期时班级载体为空 → 班主任退化成看全年级（门禁4 审计修正）
    const headTeacherTerms = user.headTeacher.map((h) => ({
      classId: h.classId,
      termId: h.termId,
    }));

    return {
      userId: user.id,
      realName: user.realName,
      roleCodes,
      scope,
      scopeComplete,
      deptIds,
      gradeNodeIds,
      visibleDeptIds,
      headTeacherTerms,
      studentId: user.studentId ?? undefined,
      childStudentIds: user.parentLinks.map((p) => p.studentId),
      menuCodes,
      buttonCodes,
      currentTermId: termId,
    };
  }

  /**
   * 沿部门树向上找第一个 deptType=GRADE 节点（docs/04 §4.2-A 步骤 4）
   *
   * 门禁4 审计修正：原实现在 for 循环内 `findFirst`（N+1，每请求 8–15 次 DB 往返，
   * 是校长端并发 P95 的头号风险）。改为：**一次批量查回全部祖先节点**，在内存里
   * 按物化 path 自下而上取第一个 GRADE。仍然每次请求实时计算（ADR-05），只减往返次数。
   */
  private async resolveGradeNodes(deptIds: string[]): Promise<string[]> {
    if (deptIds.length === 0) return [];
    const depts = await this.prisma.sys_dept.findMany({
      where: { id: { in: deptIds } },
      select: { id: true, deptType: true, path: true },
    });

    // 收集全部待查祖先 id（去重），一次查回
    const ancestorIds = new Set<string>();
    for (const d of depts) {
      for (const a of d.path.split('/').filter(Boolean)) ancestorIds.add(a);
    }
    const ancestors =
      ancestorIds.size === 0
        ? []
        : await this.prisma.sys_dept.findMany({
            where: { id: { in: [...ancestorIds] } },
            select: { id: true, deptType: true },
          });
    const typeById = new Map(ancestors.map((a) => [a.id, a.deptType]));

    const result = new Set<string>();
    for (const d of depts) {
      if (d.deptType === 'GRADE') {
        result.add(d.id);
        continue;
      }
      // 物化 path 有序：/root/gradeN/classM → 自下而上（逆序）取第一个 GRADE
      const chain = d.path.split('/').filter(Boolean).reverse();
      const found = chain.find((id) => typeById.get(id) === 'GRADE');
      if (found) result.add(found);
    }
    return [...result];
  }

  /** 可见子树：GRADE 节点及其全部子孙（CLASS/SUBJECT 在内） */
  private async resolveSubtree(gradeNodeIds: string[], ownDeptIds: string[]): Promise<string[]> {
    if (gradeNodeIds.length === 0) return [...ownDeptIds];
    const base = await this.prisma.sys_dept.findMany({
      where: { id: { in: gradeNodeIds } },
      select: { path: true },
    });
    const nodes = await this.prisma.sys_dept.findMany({
      where: { OR: base.map((b) => ({ path: { startsWith: `${b.path}/` } })) },
      select: { id: true },
    });
    return [...new Set([...gradeNodeIds, ...ownDeptIds, ...nodes.map((n) => n.id)])];
  }
}
