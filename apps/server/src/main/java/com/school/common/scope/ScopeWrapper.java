package com.school.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 第三层数据层粘合：把 {@link ScopeQueryBuilder#buildWhere(Map)} 产出的<b>逻辑 where</b>
 * 翻译成 MyBatis-Plus {@link QueryWrapper} 条件。
 *
 * <p>这是「数据层唯一出口」的落地件（ADR §1.5 ③）：所有业务列表/详情/统计查询必须经它注入
 * 范围条件，禁止裸查。值语义约定见 {@link ScopeQueryBuilder} 的 Javadoc：</p>
 * <ul>
 *   <li>String 值 → {@code eq(column, value)}</li>
 *   <li>{@link ScopeQueryBuilder.InValues} → {@code in(column, values)}</li>
 *   <li>{@link ScopeQueryBuilder.HasSome} → PostgreSQL 数组重叠 {@code column && ARRAY[...]::text[]}</li>
 *   <li>{@code "$or"} 键 → 嵌套 {@code AND ( (branch0) OR (branch1) ... )}</li>
 * </ul>
 *
 * <p><b>列名加引号</b>：PostgreSQL 建表统一使用带引号的 camelCase 列名（如 {@code "creatorDeptId"}），
 * 未加引号会被折叠为小写导致「列不存在」。故本工具对列名统一 {@code "..."} 包裹。</p>
 */
public final class ScopeWrapper {

    private ScopeWrapper() {
    }

    /** 把逻辑 where 应用到 qw 并返回（链式友好）。 */
    public static <T> QueryWrapper<T> apply(QueryWrapper<T> qw, Map<String, Object> where) {
        if (where != null && !where.isEmpty()) {
            applyEntries(qw, where);
        }
        return qw;
    }

    private static <T> void applyEntries(QueryWrapper<T> qw, Map<String, Object> where) {
        for (Map.Entry<String, Object> e : where.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value == null) {
                continue;
            }
            if (ScopeQueryBuilder.OR_KEY.equals(key)) {
                applyOr(qw, value);
            } else {
                applyCond(qw, quote(key), value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyOr(QueryWrapper<T> qw, Object value) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        // 归属条件在 buildWhere 中后置覆盖，OR 分支安全；空分支直接忽略
        List<Map<String, Object>> branches = list.stream()
                .filter(Map.class::isInstance)
                .map(b -> (Map<String, Object>) b)
                .toList();
        if (branches.isEmpty()) {
            return;
        }
        qw.and(w -> {
            for (int i = 0; i < branches.size(); i++) {
                Map<String, Object> branch = branches.get(i);
                if (i == 0) {
                    w.and(sub -> applyEntries(sub, branch));
                } else {
                    w.or(sub -> applyEntries(sub, branch));
                }
            }
        });
    }

    private static <T> void applyCond(QueryWrapper<T> qw, String column, Object value) {
        if (value instanceof ScopeQueryBuilder.InValues in) {
            if (!in.values().isEmpty()) {
                qw.in(column, in.values());
            }
            return;
        }
        if (value instanceof ScopeQueryBuilder.HasSome has) {
            if (!has.values().isEmpty()) {
                String literal = has.values().stream()
                        .map(s -> "'" + s.replace("'", "''") + "'")
                        .collect(Collectors.joining(","));
                qw.apply(column + " && ARRAY[" + literal + "]::text[]");
            }
            return;
        }
        qw.eq(column, value);
    }

    private static String quote(String column) {
        return "\"" + column + "\"";
    }
}
