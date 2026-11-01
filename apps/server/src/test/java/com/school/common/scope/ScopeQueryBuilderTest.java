package com.school.common.scope;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScopeQueryBuilder 核心逻辑单测（TS 语义来源：scope-query-builder.ts）。
 *
 * <p>覆盖：SELF / GRADE / ALL / RELATION / ROLE_GRANT / PUBLIC / forceSelf /
 * 班级载体（classField）/ E3 换届快照（按记录 termId 选班级）/ effectiveScope 兜底 /
 * forceOwner 归属人覆盖 / satisfies / buildWhere B3 归属条件后置覆盖。</p>
 */
class ScopeQueryBuilderTest {

    private static final String U1 = "u1";

    // ---- 构造辅助 ----

    private static Principal principal(ScopeLevel scope, boolean scopeComplete) {
        return Principal.builder().userId(U1).scope(scope).scopeComplete(scopeComplete).build();
    }

    private static DataScopeOptions opts(OwnershipType ownership) {
        return new DataScopeOptions("test", ownership, null, "ownerId", "deptId", "", "", false, false);
    }

    private static DataScopeOptions optsWithClassField(OwnershipType ownership, String classField, String termField) {
        return new DataScopeOptions("test", ownership, null, "ownerId", "deptId", classField, termField, false, false);
    }

    // ---- 1. matchRecord：PUBLIC ----

    @Test
    void matchRecord_public_alwaysTrue() {
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), opts(OwnershipType.PUBLIC));
        assertTrue(b.matchRecord(Map.of()));
        assertTrue(b.matchRecord(Map.of("ownerId", "someone-else")));
    }

    // ---- 2. matchRecord：forceSelf ----

    @Test
    void matchRecord_forceSelf_onlyOwner() {
        DataScopeOptions o = new DataScopeOptions("msg", OwnershipType.ORG, null, "ownerId", "deptId", "", "", true, false);
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.ALL, true), o);
        // forceSelf 优先于 PUBLIC，且不随 ALL 放大
        assertTrue(b.matchRecord(Map.of("ownerId", U1)));
        assertFalse(b.matchRecord(Map.of("ownerId", "other")));
    }

    // ---- 3. matchRecord：ORG SELF ----

    @Test
    void matchRecord_orgSelf_onlyOwner() {
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), opts(OwnershipType.ORG));
        assertTrue(b.matchRecord(Map.of("ownerId", U1)));
        assertFalse(b.matchRecord(Map.of("ownerId", "other")));
    }

    // ---- 4. matchRecord：ORG ALL ----

    @Test
    void matchRecord_orgAll_alwaysTrue() {
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.ALL, true), opts(OwnershipType.ORG));
        assertTrue(b.matchRecord(Map.of("ownerId", "anyone", "deptId", "any-dept")));
    }

    // ---- 5. matchRecord：ORG GRADE（部门可见 / 本人兜底 / 越权） ----

    @Test
    void matchRecord_orgGrade_deptVisibleOrSelf() {
        Principal p = Principal.builder().userId(U1).scope(ScopeLevel.GRADE).scopeComplete(true)
                .visibleDeptIds(List.of("d1", "d2")).build();
        ScopeQueryBuilder b = ScopeQueryBuilder.of(p, opts(OwnershipType.ORG));
        // 部门可见
        assertTrue(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1")));
        // 本人兜底（部门不可见但归属本人）
        assertTrue(b.matchRecord(Map.of("ownerId", U1, "deptId", "dX")));
        // 越权
        assertFalse(b.matchRecord(Map.of("ownerId", "other", "deptId", "dX")));
    }

    // ---- 6. matchRecord：ORG GRADE + 班级载体（classField） ----

    @Test
    void matchRecord_orgGrade_classCarrierNarrowing() {
        Principal p = Principal.builder().userId(U1).scope(ScopeLevel.GRADE).scopeComplete(true)
                .visibleDeptIds(List.of("d1"))
                .headTeacherTerms(List.of(new HeadTeacherTerm("c1", "t1")))
                .currentTermId("t1").build();
        ScopeQueryBuilder b = ScopeQueryBuilder.of(p, optsWithClassField(OwnershipType.ORG, "classId", "termId"));
        // 部门可见 + 班级命中
        assertTrue(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1", "classId", "c1", "termId", "t1")));
        // 部门可见但班级不命中（班主任被收窄，不得看全年级）
        assertFalse(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1", "classId", "c9", "termId", "t1")));
    }

    // ---- 7. matchRecord：E3 换届快照（按记录 termId 选班级） ----

    @Test
    void matchRecord_e3TermSnapshot_selectClassByRecordTerm() {
        // 班主任：t1 学期带 c1，t2 学期带 c2
        Principal p = Principal.builder().userId(U1).scope(ScopeLevel.GRADE).scopeComplete(true)
                .visibleDeptIds(List.of("d1"))
                .headTeacherTerms(List.of(new HeadTeacherTerm("c1", "t1"), new HeadTeacherTerm("c2", "t2")))
                .currentTermId("t2").build();
        ScopeQueryBuilder b = ScopeQueryBuilder.of(p, optsWithClassField(OwnershipType.ORG, "classId", "termId"));
        // 历史学期 t1：记录 classId=c1 命中（不随当前学期漂移）
        assertTrue(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1", "classId", "c1", "termId", "t1")));
        // 历史学期 t1：记录 classId=c2 不命中（那是 t2 的班）
        assertFalse(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1", "classId", "c2", "termId", "t1")));
        // 记录无 termId：退化为当前学期 t2 的班级
        assertTrue(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1", "classId", "c2")));
        assertFalse(b.matchRecord(Map.of("ownerId", "other", "deptId", "d1", "classId", "c1")));
    }

    // ---- 8. matchRecord：RELATION ----

    @Test
    void matchRecord_relation_childStudentIds() {
        Principal p = Principal.builder().userId(U1).scope(ScopeLevel.SELF).scopeComplete(true)
                .childStudentIds(List.of("s1", "s2")).build();
        ScopeQueryBuilder b = ScopeQueryBuilder.of(p, opts(OwnershipType.RELATION));
        assertTrue(b.matchRecord(Map.of("studentId", "s1")));
        assertFalse(b.matchRecord(Map.of("studentId", "s9")));
    }

    // ---- 9. matchRecord：ROLE_GRANT ----

    @Test
    void matchRecord_roleGrant_visibleRolesIntersection() {
        Principal p = Principal.builder().userId(U1).scope(ScopeLevel.SELF).scopeComplete(true)
                .roleCodes(List.of("R-TEACHER", "R-PARENT")).build();
        ScopeQueryBuilder b = ScopeQueryBuilder.of(p, opts(OwnershipType.ROLE_GRANT));
        assertTrue(b.matchRecord(Map.of("visibleRoles", List.of("R-STUDENT", "R-TEACHER"))));
        assertFalse(b.matchRecord(Map.of("visibleRoles", List.of("R-ADMIN", "R-DEAN"))));
    }

    // ---- 10. effectiveScope：唯一判定源 + SELF 兜底（H2 / AC-RBAC-2） ----

    @Test
    void effectiveScope_fallsBackToSelfWhenIncomplete() {
        ScopeQueryBuilder complete = ScopeQueryBuilder.of(principal(ScopeLevel.ALL, true), opts(OwnershipType.ORG));
        assertSame(ScopeLevel.ALL, complete.effectiveScope());

        ScopeQueryBuilder incomplete = ScopeQueryBuilder.of(principal(ScopeLevel.ALL, false), opts(OwnershipType.ORG));
        assertSame(ScopeLevel.SELF, incomplete.effectiveScope());
    }

    // ---- 11. forceOwner：写操作归属人覆盖 ----

    @Test
    void forceOwner_writeOverridesOwnerField() {
        DataScopeOptions writeOpts = new DataScopeOptions("test", OwnershipType.ORG, null, "creatorId", "deptId", "", "", false, true);
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), writeOpts);
        Map<String, Object> payload = Map.of("creatorId", "attacker", "name", "x");
        Map<String, Object> out = b.forceOwner(payload);
        assertEquals(U1, out.get("creatorId"));
        assertEquals("x", out.get("name"));

        // write=false 时原样返回（payload 的 creatorId 保持 attacker，未被覆盖）
        ScopeQueryBuilder noWrite = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), opts(OwnershipType.ORG));
        assertEquals("attacker", noWrite.forceOwner(payload).get("creatorId"));
    }

    // ---- 12. satisfies：范围覆盖判定 ----

    @Test
    void satisfies_coversRequiredScope() {
        ScopeQueryBuilder pub = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), opts(OwnershipType.PUBLIC));
        assertTrue(pub.satisfies());

        DataScopeOptions requireGrade = new DataScopeOptions("test", OwnershipType.ORG, ScopeLevel.GRADE, "ownerId", "deptId", "", "", false, false);
        assertTrue(ScopeQueryBuilder.of(principal(ScopeLevel.GRADE, true), requireGrade).satisfies());
        assertTrue(ScopeQueryBuilder.of(principal(ScopeLevel.ALL, true), requireGrade).satisfies());
        assertFalse(ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), requireGrade).satisfies());
    }

    // ---- 13. buildWhere：B3 归属条件后置覆盖 ----

    @Test
    void buildWhere_ownershipConditionOverridesExtra() {
        // extra 试图注入 ownerId=attacker，ORG SELF 归属条件必须覆盖为本人
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), opts(OwnershipType.ORG));
        Map<String, Object> where = b.buildWhere(Map.of("ownerId", "attacker"));
        assertEquals(U1, where.get("ownerId"));
    }

    @Test
    void buildWhere_gradeProducesOrAndClassCarrier() {
        Principal p = Principal.builder().userId(U1).scope(ScopeLevel.GRADE).scopeComplete(true)
                .visibleDeptIds(List.of("d1"))
                .headTeacherTerms(List.of(new HeadTeacherTerm("c1", "t1")))
                .currentTermId("t1").build();
        ScopeQueryBuilder b = ScopeQueryBuilder.of(p, optsWithClassField(OwnershipType.ORG, "classId", "termId"));
        Map<String, Object> where = b.buildWhere(Map.of());
        assertTrue(where.containsKey(ScopeQueryBuilder.OR_KEY));
        assertInstanceOf(ScopeQueryBuilder.InValues.class, where.get("classId"));
    }

    @Test
    void buildWhere_relationEmptyChildUsesSentinel() {
        ScopeQueryBuilder b = ScopeQueryBuilder.of(principal(ScopeLevel.SELF, true), opts(OwnershipType.RELATION));
        Map<String, Object> where = b.buildWhere(Map.of());
        ScopeQueryBuilder.InValues in = assertInstanceOf(ScopeQueryBuilder.InValues.class, where.get("studentId"));
        assertEquals(List.of("__NO_CHILD__"), in.values());
    }
}
