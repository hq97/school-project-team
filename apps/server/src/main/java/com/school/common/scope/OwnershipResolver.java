package com.school.common.scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请求主体解析器（TS 语义来源：ownership-resolver.ts）。
 *
 * <p>每次请求实时读库计算，不做授权快照缓存（ADR-05 / docs/04 §2.2）。判定算法 A/B/C（docs/04 §4.2）
 * 所需的全部载体在这里一次联查取齐。</p>
 *
 * <p><b>数据访问约定</b>：本类位于 {@code common/scope}，是数据层唯一出口的豁免区，允许用
 * {@link NamedParameterJdbcTemplate} 精确控制 SQL（ArchUnit 规则 ① 只封堵 domains/platform/crosscut，
 * 不封 common/scope）。编译期不连库，SQL 正确性留门禁 4 Step 4 装 PG 后验证。</p>
 */
@Component
public class OwnershipResolver {

    private static final Logger log = LoggerFactory.getLogger(OwnershipResolver.class);

    private final NamedParameterJdbcTemplate jdbc;

    public OwnershipResolver(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 账号状态实时校验：停用账号持旧 token 一律拒绝（docs/04 §六场景 8）。 */
    public boolean isActive(String userId) {
        List<String> statuses = jdbc.queryForList(
                "SELECT \"accountStatus\" FROM \"sys_user\" WHERE \"id\" = :id",
                Map.of("id", userId), String.class);
        return !statuses.isEmpty() && "ACTIVE".equals(statuses.get(0));
    }

    public Principal resolve(String userId) {
        // 1. 用户 + 当前学期（实时）
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT \"realName\", \"accountStatus\", \"studentId\" FROM \"sys_user\" WHERE \"id\" = :id",
                Map.of("id", userId));
        if (users.isEmpty()) {
            throw new IllegalStateException("用户不存在: " + userId);
        }
        Map<String, Object> user = users.get(0);

        List<Map<String, Object>> terms = jdbc.queryForList(
                "SELECT \"itemCode\" FROM \"sys_dict\" "
                        + "WHERE \"dictCode\" = 'DICT-TERM' AND \"isCurrent\" = true AND \"enabled\" = true LIMIT 1",
                Map.of());
        String termId = terms.isEmpty() ? "" : (String) terms.get(0).get("itemCode");

        // 2. 角色（实时取角色，不做授权快照缓存）
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT r.\"code\", r.\"dataScope\", r.\"scopeComplete\" "
                        + "FROM \"user_role\" ur JOIN \"sys_role\" r ON r.\"id\" = ur.\"roleId\" "
                        + "WHERE ur.\"userId\" = :id",
                Map.of("id", userId));
        List<String> roleCodes = new ArrayList<>();
        for (Map<String, Object> r : roles) {
            roleCodes.add((String) r.get("code"));
        }
        List<String> roleIds = jdbc.queryForList(
                "SELECT \"roleId\" FROM \"user_role\" WHERE \"userId\" = :id",
                Map.of("id", userId), String.class);

        // 3. 菜单/按钮权限点
        List<String> menuCodes = roleIds.isEmpty() ? List.of() : jdbc.queryForList(
                "SELECT DISTINCT m.\"code\" FROM \"role_menu\" rm JOIN \"sys_menu\" m ON m.\"id\" = rm.\"menuId\" "
                        + "WHERE rm.\"roleId\" IN (:ids)",
                Map.of("ids", roleIds), String.class);
        List<String> buttonCodes = roleIds.isEmpty() ? List.of() : jdbc.queryForList(
                "SELECT DISTINCT b.\"code\" FROM \"role_button\" rb JOIN \"sys_button\" b ON b.\"id\" = rb.\"buttonId\" "
                        + "WHERE rb.\"roleId\" IN (:ids)",
                Map.of("ids", roleIds), String.class);

        // 4. 有效数据范围 = MAX(各角色范围)；缺数据范围的角色 → 运行时按 SELF 兜底 + 告警（AC-RBAC-2）
        ScopeLevel scope = ScopeLevel.SELF;
        boolean scopeComplete = true;
        for (Map<String, Object> r : roles) {
            String ds = (String) r.get("dataScope");
            Boolean sc = (Boolean) r.get("scopeComplete");
            if (ds == null || !Boolean.TRUE.equals(sc)) {
                scopeComplete = false;
                log.warn("[AC-RBAC-2] 角色 {} 数据范围不完整，按最小权限 SELF 兜底（userId={}）", r.get("code"), userId);
                continue;
            }
            scope = ScopeLevel.max(scope, ScopeLevel.valueOf(ds));
        }

        // 5. 部门挂靠
        List<String> deptIds = jdbc.queryForList(
                "SELECT \"deptId\" FROM \"user_dept\" WHERE \"userId\" = :id",
                Map.of("id", userId), String.class);

        // 6. GRADE 节点 + 可见子树
        List<String> gradeNodeIds = resolveGradeNodes(deptIds);
        List<String> visibleDeptIds = resolveSubtree(gradeNodeIds, deptIds);

        // 7. 班主任身份（换届快照 E3）：全量返回（含历史学期），由 ScopeQueryBuilder 按记录 termId 选取
        List<Map<String, Object>> htRows = jdbc.queryForList(
                "SELECT \"classId\", \"termId\" FROM \"head_teacher\" WHERE \"teacherId\" = :id",
                Map.of("id", userId));
        List<HeadTeacherTerm> headTeacherTerms = new ArrayList<>();
        for (Map<String, Object> h : htRows) {
            headTeacherTerms.add(new HeadTeacherTerm((String) h.get("classId"), (String) h.get("termId")));
        }

        // 8. 关系型：家长-子女并集
        List<String> childStudentIds = jdbc.queryForList(
                "SELECT \"studentId\" FROM \"student_parent\" WHERE \"parentUserId\" = :id",
                Map.of("id", userId), String.class);

        return Principal.builder()
                .userId(userId)
                .realName((String) user.get("realName"))
                .roleCodes(roleCodes)
                .scope(scope)
                .scopeComplete(scopeComplete)
                .deptIds(deptIds)
                .gradeNodeIds(gradeNodeIds)
                .visibleDeptIds(visibleDeptIds)
                .headTeacherTerms(headTeacherTerms)
                .studentId((String) user.get("studentId"))
                .childStudentIds(childStudentIds)
                .menuCodes(menuCodes)
                .buttonCodes(buttonCodes)
                .currentTermId(termId)
                .build();
    }

    /**
     * 沿部门树向上找第一个 deptType=GRADE 节点（docs/04 §4.2-A 步骤 4）。
     *
     * <p><b>门禁4 审计 H8（反 N+1）</b>：TS 原实现逐部门 findFirst（每请求 8–15 次 DB 往返，
     * 是校长端并发 P95 的头号风险）。这里改为：一次批量查回全部祖先节点，在内存里按物化 path
     * 自下而上取第一个 GRADE。仍每次请求实时计算（不违反 ADR-05），只减往返次数。</p>
     */
    private List<String> resolveGradeNodes(List<String> deptIds) {
        if (deptIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> depts = jdbc.queryForList(
                "SELECT \"id\", \"deptType\", \"path\" FROM \"sys_dept\" WHERE \"id\" IN (:ids)",
                Map.of("ids", deptIds));

        // 收集全部待查祖先 id（去重），一次查回
        Set<String> ancestorIds = new LinkedHashSet<>();
        for (Map<String, Object> d : depts) {
            String path = (String) d.get("path");
            if (path == null) {
                continue;
            }
            for (String a : path.split("/")) {
                if (!a.isEmpty()) {
                    ancestorIds.add(a);
                }
            }
        }
        Map<String, String> typeById = new HashMap<>();
        if (!ancestorIds.isEmpty()) {
            List<Map<String, Object>> ancestors = jdbc.queryForList(
                    "SELECT \"id\", \"deptType\" FROM \"sys_dept\" WHERE \"id\" IN (:ids)",
                    Map.of("ids", new ArrayList<>(ancestorIds)));
            for (Map<String, Object> a : ancestors) {
                typeById.put((String) a.get("id"), (String) a.get("deptType"));
            }
        }

        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> d : depts) {
            String id = (String) d.get("id");
            String deptType = (String) d.get("deptType");
            if ("GRADE".equals(deptType)) {
                result.add(id);
                continue;
            }
            String path = (String) d.get("path");
            if (path == null) {
                continue;
            }
            // 物化 path 有序：/root/gradeN/classM → 自下而上（逆序）取第一个 GRADE
            List<String> chain = new ArrayList<>();
            for (String s : path.split("/")) {
                if (!s.isEmpty()) {
                    chain.add(s);
                }
            }
            Collections.reverse(chain);
            String found = null;
            for (String cid : chain) {
                if ("GRADE".equals(typeById.get(cid))) {
                    found = cid;
                    break;
                }
            }
            if (found != null) {
                result.add(found);
            }
        }
        return new ArrayList<>(result);
    }

    /** 可见子树：GRADE 节点及其全部子孙（CLASS/SUBJECT 在内）。 */
    private List<String> resolveSubtree(List<String> gradeNodeIds, List<String> ownDeptIds) {
        if (gradeNodeIds.isEmpty()) {
            return new ArrayList<>(ownDeptIds);
        }
        List<Map<String, Object>> base = jdbc.queryForList(
                "SELECT \"path\" FROM \"sys_dept\" WHERE \"id\" IN (:ids)",
                Map.of("ids", gradeNodeIds));

        List<String> nodeIds = new ArrayList<>();
        if (!base.isEmpty()) {
            // 单次查询 OR LIKE（对齐 TS 的 OR startsWith，避免逐 base N+1）
            StringBuilder sb = new StringBuilder("SELECT \"id\" FROM \"sys_dept\" WHERE ");
            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < base.size(); i++) {
                String path = (String) base.get(i).get("path");
                if (i > 0) {
                    sb.append(" OR ");
                }
                sb.append("\"path\" LIKE :p").append(i);
                params.put("p" + i, (path == null ? "" : path) + "/%");
            }
            List<Map<String, Object>> nodes = jdbc.queryForList(sb.toString(), params);
            for (Map<String, Object> n : nodes) {
                nodeIds.add((String) n.get("id"));
            }
        }

        Set<String> set = new LinkedHashSet<>(gradeNodeIds);
        set.addAll(ownDeptIds);
        set.addAll(nodeIds);
        return new ArrayList<>(set);
    }
}
