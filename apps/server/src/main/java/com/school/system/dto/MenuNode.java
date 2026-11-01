package com.school.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 前端动态路由菜单节点（vue-pure-admin initRouter 契约）。
 *
 * <p>字段名与 team-lead 门禁4 第三步契约完全一致，勿改：{@code path/name/component/meta{title,icon,rank,backstage}/children}。</p>
 *
 * <p>{@code meta.rank} 仅顶层目录填充（用于菜单排序），叶子为 null 并由 {@code @JsonInclude(NON_NULL)} 省略。</p>
 */
public record MenuNode(
        String path,
        String name,
        String component,
        Meta meta,
        List<MenuNode> children) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(String title, String icon, Integer rank, Boolean backstage) {
    }
}
