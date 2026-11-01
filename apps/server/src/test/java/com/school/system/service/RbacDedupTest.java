package com.school.system.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 编码去重 + 排序纯逻辑单测（登录档案 buttons/roles）。
 */
class RbacDedupTest {

    @Test
    void nullReturnsEmpty() {
        assertTrue(RbacDedup.distinctSorted(null).isEmpty());
    }

    @Test
    void dedupAndSort() {
        assertEquals(List.of("BTN-A", "BTN-B"), RbacDedup.distinctSorted(List.of("BTN-B", "BTN-A", "BTN-B", "BTN-A")));
    }

    @Test
    void filtersNullElements() {
        assertEquals(List.of("R-ADMIN"), RbacDedup.distinctSorted(Arrays.asList("R-ADMIN", null, "R-ADMIN")));
    }
}
