package com.school.system.service;

import java.util.List;
import java.util.Objects;

/**
 * RBAC 编码去重 + 排序（纯逻辑，可单测）。登录档案的 roles / buttons 编码需「去重 + 稳定顺序」。
 */
public final class RbacDedup {

    private RbacDedup() {
    }

    public static List<String> distinctSorted(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
