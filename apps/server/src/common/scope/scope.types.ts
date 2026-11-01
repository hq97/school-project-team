/**
 * 归属类型（docs/04 §1.5/§1.6/§1.7）：
 *  ORG        组织型：部门树承载 SELF / GRADE / ALL（含 CLASS 班级载体、SUBJECT 教研组载体）
 *  RELATION   关系型：家长-子女 student_parent
 *  ROLE_GRANT 角色授权型：公告 notice（命中可见角色 + 菜单权限）
 *  PUBLIC     全校共享/公共对象：不做记录级过滤，仅由菜单/按钮权限控制（如 asset 可借列表）
 */
export type OwnershipType = 'ORG' | 'RELATION' | 'ROLE_GRANT' | 'PUBLIC';

export type ScopeLevel = 'SELF' | 'GRADE' | 'ALL';

/** 请求主体：每次请求实时从库计算，不做授权快照缓存（ADR-05 / docs/04 §2.2） */
export interface Principal {
  userId: string;
  realName: string;
  roleCodes: string[];
  /** 有效数据范围 = MAX(各角色范围)，SELF < GRADE < ALL */
  scope: ScopeLevel;
  /** 角色数据范围是否配置完整；false → 运行时按 SELF 兜底 + 告警（AC-RBAC-2） */
  scopeComplete: boolean;
  deptIds: string[]; // 挂靠部门节点
  gradeNodeIds: string[]; // 向上找到的 GRADE 节点（跨年级兼课可取多个，取并集）
  /** 可见部门子树：GRADE 范围下的全部子孙节点（CLASS/SUBJECT 在内） */
  visibleDeptIds: string[];
  /**
   * 班主任身份（换届快照 E3）：**全量返回** `{classId, termId}`，不按当前学期过滤。
   *
   * 门禁4 审计修正：此前只取当前学期的 head_teacher 行，查历史学期时班级载体为空，
   * 班主任会退化成"看全年级历史数据"（越权）。改由 ScopeQueryBuilder 按**记录的 termId**
   * 选取对应学期的班级（见 `classIdsFor`）。
   */
  headTeacherTerms: { classId: string; termId: string }[];
  studentId?: string;
  /** 家长关系型：关联子女并集 */
  childStudentIds: string[];
  menuCodes: string[];
  buttonCodes: string[];
  currentTermId: string;
}
