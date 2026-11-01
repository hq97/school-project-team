package com.school.common.scope;

import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第三层：数据层唯一查询出口（ADR §1.5 ③；TS 语义来源：scope-query-builder.ts）。
 *
 * <p>所有列表 / 详情 / 导出 / 大屏聚合 / 下钻查询必须经它注入范围条件，业务模块禁止裸查
 * （由 {@code PermissionArchitectureTest} ArchUnit 静态封堵 + 门禁 5 Review 复核）。</p>
 *
 * <p>三种归属类型全实现（docs/04 §4.2 算法 A/B/C/E）：ORG 组织型（含 CLASS 载体 + 换届快照）、
 * RELATION 关系型、ROLE_GRANT 角色授权型、PUBLIC 公共对象。</p>
 *
 * <p><b>buildWhere 返回的逻辑 where 片段约定</b>（值语义，供业务 Mapper 层翻译为 MyBatis-Plus 条件）：</p>
 * <ul>
 *   <li>String 值 → 列等值</li>
 *   <li>{@link InValues} → SQL {@code IN (...)}</li>
 *   <li>{@link HasSome} → PostgreSQL 数组重叠（{@code visibleRoles && ARRAY[...]}）</li>
 *   <li>{@code "$or"} 键 → {@code List<Map<String,Object>>} OR 分支</li>
 * </ul>
 */
public final class ScopeQueryBuilder {

    private static final Logger log = LoggerFactory.getLogger(ScopeQueryBuilder.class);

    public static final String OR_KEY = "$or";

    /** 家长无子女时的哨兵，等价 TS 的 {@code '__NO_CHILD__'} */
    private static final String NO_CHILD_SENTINEL = "__NO_CHILD__";

    private final Principal principal;
    private final DataScopeOptions opts;

    private ScopeQueryBuilder(Principal principal, DataScopeOptions opts) {
        this.principal = principal;
        this.opts = opts;
    }

    public static ScopeQueryBuilder of(Principal principal, DataScopeOptions opts) {
        return new ScopeQueryBuilder(principal, opts);
    }

    /**
     * 从当前请求重建查询构建器（DataScopeInterceptor 已把 {@link DataScopeOptions} 放入 request attribute）。
     */
    public static ScopeQueryBuilder fromRequest(HttpServletRequest request) {
        Principal p = CurrentPrincipal.get();
        if (p == null) {
            throw BizException.unauthorized();
        }
        Object o = request.getAttribute(DataScopeInterceptor.REQUEST_ATTR_OPTIONS);
        if (!(o instanceof DataScopeOptions opts)) {
            throw new IllegalStateException("请求缺少 DataScopeOptions（DataScopeInterceptor 未执行？）");
        }
        return of(p, opts);
    }

    /**
     * 生成列表/聚合用的 where 片段（逻辑结构，非 SQL）。
     *
     * <p><b>门禁4 审计 B3（归属条件后置覆盖）</b>：展开顺序必须是「先 extra、再归属条件」，
     * 归属条件后写、可覆盖 extra 中同名列。禁止让调用方用 extra 整体替换范围条件——
     * 导出接口若按常规写 {@code {id: in ids}} 就越权（docs/04 §五.5）。归属是安全边界，永远最后写入。</p>
     */
    public Map<String, Object> buildWhere(Map<String, Object> extra) {
        Map<String, Object> where = new LinkedHashMap<>();
        if (extra != null) {
            where.putAll(extra);
        }
        // 归属条件后置覆盖（B3）
        where.putAll(applyOwnership());
        applyTerm(where);
        return where;
    }

    /**
     * 单条记录判定：不通过 → 调用方抛 40304（防枚举，docs/04 §4.3）。
     * 纯逻辑，可脱离 DB 单测。
     */
    public boolean matchRecord(Map<String, Object> record) {
        // 接口恒本人（站内信等）：优先于 PUBLIC，且不随角色范围放大（AC-MS7-1）
        if (opts.forceSelf()) {
            return eq(record.get(ownerField()), principal.userId());
        }
        if (opts.ownership() == OwnershipType.PUBLIC) {
            return true;
        }
        if (opts.ownership() == OwnershipType.RELATION) {
            String studentId = str(record.get("studentId"));
            return studentId != null && principal.childStudentIds().contains(studentId);
        }
        if (opts.ownership() == OwnershipType.ROLE_GRANT) {
            List<String> visibleRoles = asStringList(record.get("visibleRoles"));
            return visibleRoles.stream().anyMatch(principal.roleCodes()::contains);
        }

        // ORG
        ScopeLevel eff = effectiveScope();
        if (eff == ScopeLevel.ALL) {
            return true;
        }
        if (eff == ScopeLevel.SELF) {
            return eq(record.get(ownerField()), principal.userId());
        }

        // GRADE
        String deptId = str(record.get(deptField()));
        boolean inDept = deptId != null && principal.visibleDeptIds().contains(deptId);
        String owner = str(record.get(ownerField()));
        boolean inOwner = eq(owner, principal.userId());
        if (!inDept && !inOwner) {
            return false;
        }

        // 本班载体：班主任把「本年级」收窄到该 CLASS（docs/04 §4.2-B）
        // E3 换届快照：按记录的 termId 选取对应学期所带班级
        List<String> classIds = classIdsFor(record.get(termField()));
        if (!classIds.isEmpty() && hasClassField()) {
            String classId = str(record.get(classField()));
            return classId != null && classIds.contains(classId);
        }
        return true;
    }

    /**
     * 写操作：强制归属人 = 当前用户，忽略前端传入（防伪造 creatorId/applicantId）。
     * 纯逻辑，可脱离 DB 单测。
     */
    public Map<String, Object> forceOwner(Map<String, Object> payload) {
        if (!opts.write()) {
            return payload;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (payload != null) {
            out.putAll(payload);
        }
        out.put(ownerField(), principal.userId());
        return out;
    }

    /**
     * 有效范围的<b>唯一判定源</b>（AC-RBAC-2）：角色范围不完整 → 一律按最小权限 SELF + 告警。
     *
     * <p><b>门禁4 审计 H2</b>：禁止业务直读 {@code principal.scope()}——任何「按范围做安全判定」的地方
     * 都必须调用本方法，否则会绕过 SELF 降级（脱敏/大屏 scope 展示曾因此失效）。</p>
     */
    public ScopeLevel effectiveScope() {
        if (!principal.scopeComplete()) {
            log.warn("[AC-RBAC-2] userId={} 数据范围不完整，按 SELF 兜底（resource={}）",
                    principal.userId(), opts.resource());
        }
        return principal.scopeComplete() ? principal.scope() : ScopeLevel.SELF;
    }

    /** 供 Interceptor 判定：角色有效范围是否满足接口声明的最低要求（TS satisfies）。 */
    public boolean satisfies() {
        if (opts.ownership() == OwnershipType.PUBLIC) {
            return true;
        }
        if (opts.requiredScope() == null) {
            return true;
        }
        return ScopeLevel.covers(effectiveScope(), opts.requiredScope());
    }

    // ---- 私有辅助 ----

    private String ownerField() {
        return opts.ownerField();
    }

    private String deptField() {
        return opts.deptField();
    }

    private String classField() {
        return opts.classField();
    }

    private String termField() {
        return opts.termField();
    }

    private boolean hasClassField() {
        return opts.classField() != null && !opts.classField().isEmpty();
    }

    /**
     * 班主任所带班级（按学期取值，E3 换届快照）：
     * 记录无 termId 时退化为「当前学期」的班级集合，仍做收窄（TS classIdsFor）。
     */
    private List<String> classIdsFor(Object termIdObj) {
        String t = (termIdObj instanceof String s && !s.isEmpty()) ? s : principal.currentTermId();
        List<String> out = new ArrayList<>();
        for (HeadTeacherTerm h : principal.headTeacherTerms()) {
            if ((t == null || t.isEmpty()) || h.termId() == null || h.termId().equals(t)) {
                out.add(h.classId());
            }
        }
        return out;
    }

    private Map<String, Object> applyOwnership() {
        Map<String, Object> cond = new LinkedHashMap<>();
        // 接口恒本人：不随角色数据范围放大，也不因表无 deptId 而生成非法条件（AC-MS7-1）
        if (opts.forceSelf()) {
            cond.put(ownerField(), principal.userId());
            return cond;
        }
        if (opts.ownership() == OwnershipType.PUBLIC) {
            return cond;
        }
        if (opts.ownership() == OwnershipType.RELATION) {
            List<String> ids = principal.childStudentIds();
            List<String> in = ids.isEmpty() ? List.of(NO_CHILD_SENTINEL) : ids;
            cond.put("studentId", new InValues(in));
            return cond;
        }
        if (opts.ownership() == OwnershipType.ROLE_GRANT) {
            cond.put("visibleRoles", new HasSome(principal.roleCodes()));
            return cond;
        }

        ScopeLevel scope = effectiveScope();
        if (scope == ScopeLevel.ALL) {
            return cond;
        }
        if (scope == ScopeLevel.SELF) {
            cond.put(ownerField(), principal.userId());
            return cond;
        }
        // GRADE
        List<Map<String, Object>> or = new ArrayList<>();
        Map<String, Object> deptBranch = new LinkedHashMap<>();
        deptBranch.put(deptField(), new InValues(principal.visibleDeptIds()));
        or.add(deptBranch);
        Map<String, Object> ownerBranch = new LinkedHashMap<>();
        ownerBranch.put(ownerField(), principal.userId());
        or.add(ownerBranch);
        cond.put(OR_KEY, or);

        // 本班载体收窄（班主任）：列表场景按当前学期取所带班级（E3）
        List<String> classIds = classIdsFor(null);
        if (!classIds.isEmpty() && hasClassField()) {
            cond.put(classField(), new InValues(classIds));
        }
        return cond;
    }

    /** 学期口径：聚合与鉴权一律先按学期+周次（ADR §4.5），自然月仅展示层重排（TS applyTerm）。 */
    private void applyTerm(Map<String, Object> where) {
        if (hasTermField() && principal.currentTermId() != null && !principal.currentTermId().isEmpty()) {
            where.put(termField(), principal.currentTermId());
        }
    }

    private boolean hasTermField() {
        return opts.termField() != null && !opts.termField().isEmpty();
    }

    private static boolean eq(Object a, Object b) {
        return a != null && a.equals(b);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) {
                    out.add(e.toString());
                }
            }
            return out;
        }
        if (o instanceof String[] arr) {
            return List.of(arr);
        }
        if (o instanceof String s) {
            return List.of(s);
        }
        return List.of();
    }

    /** SQL IN 条件标记值 */
    public record InValues(List<String> values) {
        public InValues {
            values = List.copyOf(values);
        }
    }

    /** PostgreSQL 数组重叠标记值（visibleRoles && ARRAY[...]） */
    public record HasSome(List<String> values) {
        public HasSome {
            values = List.copyOf(values);
        }
    }
}
