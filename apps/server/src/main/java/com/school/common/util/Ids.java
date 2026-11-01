package com.school.common.util;

import java.util.UUID;

/**
 * 主键生成：{@code prefix}_{12位随机}（可读 + 无碰撞）。TEXT 主键实体统一用此工具，不依赖数据库序列。
 */
public final class Ids {

    private Ids() {
    }

    public static String next(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
