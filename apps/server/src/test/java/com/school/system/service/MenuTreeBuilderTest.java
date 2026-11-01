package com.school.system.service;

import com.school.system.dto.MenuNode;
import com.school.system.entity.SysMenu;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单树组装纯逻辑单测（契约：vue-pure-admin 动态路由）。
 */
class MenuTreeBuilderTest {

    private static SysMenu menu(String id, String code, String name, String path, String parentId,
                                int sort, String component, String icon) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setCode(code);
        m.setName(name);
        m.setPath(path);
        m.setParentId(parentId);
        m.setSort(sort);
        m.setComponent(component);
        m.setIcon(icon);
        return m;
    }

    @Test
    void build_empty() {
        assertTrue(MenuTreeBuilder.build(List.of()).isEmpty());
        assertTrue(MenuTreeBuilder.build(null).isEmpty());
    }

    @Test
    void build_directoryLayoutAndLeafComponent() {
        SysMenu dir = menu("m05", "M05", "系统管理", null, null, 50, null, "ep:setting");
        SysMenu leaf = menu("m0501", "M0501", "用户管理", "/system/users", "m05", 51, "system/users/index", "ep:user");

        List<MenuNode> tree = MenuTreeBuilder.build(List.of(leaf, dir));
        assertEquals(1, tree.size());

        MenuNode root = tree.get(0);
        assertEquals("/system", root.path());          // 目录 path 由首个子菜单推导
        assertEquals("M05", root.name());              // name = code
        assertEquals("Layout", root.component());      // 目录填字面量 Layout
        assertEquals("系统管理", root.meta().title());
        assertEquals("ep:setting", root.meta().icon());
        assertEquals(50, root.meta().rank());          // 顶层目录 rank = sort
        assertTrue(root.meta().backstage());

        MenuNode child = root.children().get(0);
        assertEquals("/system/users", child.path());
        assertEquals("M0501", child.name());
        assertEquals("system/users/index", child.component());  // 叶子填 component 字段
        assertEquals("用户管理", child.meta().title());
        assertEquals("ep:user", child.meta().icon());
        assertNull(child.meta().rank());               // 叶子无 rank
        assertTrue(child.children().isEmpty());
    }

    @Test
    void build_topLevelLeaf_usesComponentField() {
        // M15 数据大屏：顶层但无子节点（是叶子页，不是目录），component 应取字段值而非 Layout
        SysMenu leaf = menu("m15", "M15", "数据大屏", "/dashboard/bigscreen", null, 150, "dashboard/bigscreen/index", "ep:monitor");
        List<MenuNode> tree = MenuTreeBuilder.build(List.of(leaf));

        assertEquals(1, tree.size());
        MenuNode node = tree.get(0);
        assertEquals("dashboard/bigscreen/index", node.component());
        assertEquals(150, node.meta().rank());
        assertTrue(node.children().isEmpty());
    }

    @Test
    void build_sortsRootsBySort() {
        SysMenu a = menu("m02", "M02", "采购看板", null, null, 20, null, "ep:trend-charts");
        SysMenu b = menu("m01", "M01", "工作台", null, null, 10, null, "ep:home-filled");
        List<MenuNode> tree = MenuTreeBuilder.build(List.of(a, b));

        assertEquals("M01", tree.get(0).name());
        assertEquals("M02", tree.get(1).name());
    }
}
