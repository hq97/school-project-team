package com.school.common.scope;

/**
 * 三级数据范围（TS 语义来源：scope.types.ts + data-scope.options.ts）。
 *
 * <p><b>严禁加第四级</b>（docs/03 决策 18⑤ / AC-RBAC-4）：SELF &lt; GRADE &lt; ALL 严格递增，
 * 新增枚举值即破坏「范围递增 + 唯一判定源」语义，门禁 5/6 会拦截。</p>
 */
public enum ScopeLevel {

    SELF(1),
    GRADE(2),
    ALL(3);

    private final int rank;

    ScopeLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** 用户范围是否覆盖接口所需最低范围（TS covers） */
    public static boolean covers(ScopeLevel userScope, ScopeLevel required) {
        return userScope.rank >= required.rank;
    }

    /** 取并集（TS maxScope）：多角色取最大范围 */
    public static ScopeLevel max(ScopeLevel a, ScopeLevel b) {
        return a.rank >= b.rank ? a : b;
    }
}
