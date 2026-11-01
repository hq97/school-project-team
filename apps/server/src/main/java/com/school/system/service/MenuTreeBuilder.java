package com.school.system.service;

import com.school.system.dto.MenuNode;
import com.school.system.entity.SysMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树组装（纯逻辑，无 DB 依赖，可单测）。
 *
 * <p>规则（team-lead 门禁4 第三步契约）：</p>
 * <ul>
 *   <li>{@code path}：叶子取库内 path；顶层目录若 path 为空，按第一个子菜单路径的首段推导（如 /system/users → /system）；</li>
 *   <li>{@code name}：取菜单 {@code code}（唯一稳定，作为 Vue Router route name；显示名在 meta.title）；</li>
 *   <li>{@code component}：有子节点（目录）填字面量 {@code "Layout"}，叶子填库内 component；</li>
 *   <li>{@code meta.title}=库内 name，{@code meta.icon}=库内 icon，{@code meta.backstage}=true；</li>
 *   <li>{@code meta.rank}：仅顶层（parentId 为空）填充 = sort（菜单排序），叶子省略；</li>
 *   <li>整体按 sort 升序。</li>
 * </ul>
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    public static List<MenuNode> build(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }

        Map<String, SysMenu> byId = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            if (m.getId() != null) {
                byId.put(m.getId(), m);
            }
        }

        // 记录有子节点的菜单（目录）
        Map<String, Boolean> hasChildren = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            if (m.getParentId() != null && byId.containsKey(m.getParentId())) {
                hasChildren.put(m.getParentId(), true);
            }
        }

        // 根 = parentId 为 null，或 parentId 不在本次授权菜单集合内（防悬挂）
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu m : menus) {
            if (m.getParentId() == null || !byId.containsKey(m.getParentId())) {
                roots.add(m);
            }
        }
        roots.sort(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Comparator.naturalOrder())));

        List<MenuNode> result = new ArrayList<>();
        for (SysMenu root : roots) {
            MenuNode node = toNode(root, hasChildren, byId);
            result.add(node);
        }
        return result;
    }

    private static MenuNode toNode(SysMenu menu, Map<String, Boolean> hasChildren, Map<String, SysMenu> byId) {
        boolean isDirectory = Boolean.TRUE.equals(hasChildren.get(menu.getId()));

        List<SysMenu> childMenus = new ArrayList<>();
        for (SysMenu m : byId.values()) {
            if (menu.getId() != null && menu.getId().equals(m.getParentId())) {
                childMenus.add(m);
            }
        }
        childMenus.sort(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Comparator.naturalOrder())));

        List<MenuNode> children = new ArrayList<>();
        for (SysMenu c : childMenus) {
            children.add(toNode(c, hasChildren, byId));
        }

        String path = menu.getPath();
        if (isDirectory && (path == null || path.isEmpty()) && !children.isEmpty()) {
            path = deriveDirectoryPath(children.get(0).path());
        }

        String component = isDirectory ? "Layout" : menu.getComponent();

        Integer rank = (menu.getParentId() == null) ? menu.getSort() : null;
        MenuNode.Meta meta = new MenuNode.Meta(menu.getName(), menu.getIcon(), rank, true);

        return new MenuNode(path, menu.getCode(), component, meta, children);
    }

    /** 目录路径推导：取子路径首段，如 /system/users → /system；/dashboard/bigscreen → /dashboard。 */
    private static String deriveDirectoryPath(String childPath) {
        if (childPath == null || childPath.isEmpty()) {
            return "/";
        }
        String p = childPath.startsWith("/") ? childPath : "/" + childPath;
        int second = p.indexOf('/', 1);
        return second > 0 ? p.substring(0, second) : p;
    }
}
