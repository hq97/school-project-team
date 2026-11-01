package com.school.common.scope;

/**
 * 班主任身份（换届快照 E3）：{@code classId + termId}。
 *
 * <p>TS 语义来源：scope.types.ts 的 {@code headTeacherTerms: {classId, termId}[]}。
 * 全量返回（含历史学期），由 {@link ScopeQueryBuilder#classIdsFor(Object)} 按记录 termId 选取对应学期班级。</p>
 */
public record HeadTeacherTerm(String classId, String termId) {
}
