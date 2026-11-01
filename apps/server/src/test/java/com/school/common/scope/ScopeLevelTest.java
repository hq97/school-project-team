package com.school.common.scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScopeLevel covers/max 边界（TS 语义来源：data-scope.options.ts 的 SCOPE_RANK）。
 */
class ScopeLevelTest {

    @Test
    void rankOrderIsStrictlyIncreasing() {
        assertTrue(ScopeLevel.SELF.rank() < ScopeLevel.GRADE.rank());
        assertTrue(ScopeLevel.GRADE.rank() < ScopeLevel.ALL.rank());
    }

    @Test
    void coversSelf() {
        assertTrue(ScopeLevel.covers(ScopeLevel.SELF, ScopeLevel.SELF));
        assertFalse(ScopeLevel.covers(ScopeLevel.SELF, ScopeLevel.GRADE));
        assertFalse(ScopeLevel.covers(ScopeLevel.SELF, ScopeLevel.ALL));
    }

    @Test
    void coversGrade() {
        assertTrue(ScopeLevel.covers(ScopeLevel.GRADE, ScopeLevel.SELF));
        assertTrue(ScopeLevel.covers(ScopeLevel.GRADE, ScopeLevel.GRADE));
        assertFalse(ScopeLevel.covers(ScopeLevel.GRADE, ScopeLevel.ALL));
    }

    @Test
    void coversAll() {
        assertTrue(ScopeLevel.covers(ScopeLevel.ALL, ScopeLevel.SELF));
        assertTrue(ScopeLevel.covers(ScopeLevel.ALL, ScopeLevel.GRADE));
        assertTrue(ScopeLevel.covers(ScopeLevel.ALL, ScopeLevel.ALL));
    }

    @Test
    void maxPicksHigherRank() {
        assertSame(ScopeLevel.GRADE, ScopeLevel.max(ScopeLevel.SELF, ScopeLevel.GRADE));
        assertSame(ScopeLevel.ALL, ScopeLevel.max(ScopeLevel.ALL, ScopeLevel.SELF));
        assertSame(ScopeLevel.ALL, ScopeLevel.max(ScopeLevel.GRADE, ScopeLevel.ALL));
        assertSame(ScopeLevel.SELF, ScopeLevel.max(ScopeLevel.SELF, ScopeLevel.SELF));
    }
}
