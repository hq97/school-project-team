package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.dto.MenuCreateRequest;
import com.school.system.dto.MenuUpdateRequest;
import com.school.system.entity.RoleMenu;
import com.school.system.entity.SysMenu;
import com.school.system.mapper.RoleMenuMapper;
import com.school.system.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统菜单管理（M0503）。
 */
@Service
public class MenuService {

    private final SysMenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    public MenuService(SysMenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    public List<Map<String, Object>> tree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (SysMenu m : all) {
            byId.put(m.getId(), toMap(m));
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysMenu m : all) {
            Map<String, Object> node = byId.get(m.getId());
            if (m.getParentId() == null || !byId.containsKey(m.getParentId())) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) byId.get(m.getParentId()).get("children");
                children.add(node);
            }
        }
        roots.sort(Comparator.comparing(r -> (Integer) r.get("sort")));
        return roots;
    }

    public SysMenu create(MenuCreateRequest req) {
        Long exists = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getCode, req.code()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("菜单编码已存在");
        }
        SysMenu menu = new SysMenu();
        menu.setId(Ids.next("menu"));
        menu.setCode(req.code());
        menu.setName(req.name());
        menu.setPath(req.path());
        menu.setParentId(req.parentId());
        menu.setSort(req.sort() == null ? 0 : req.sort());
        menu.setListLike(Boolean.TRUE.equals(req.listLike()));
        menu.setComponent(req.component());
        menu.setIcon(req.icon());
        menuMapper.insert(menu);
        return menuMapper.selectById(menu.getId());
    }

    public SysMenu update(String id, MenuUpdateRequest req) {
        SysMenu menu = require(id);
        if (req.name() != null) {
            menu.setName(req.name());
        }
        if (req.path() != null) {
            menu.setPath(req.path());
        }
        if (req.parentId() != null) {
            menu.setParentId(req.parentId());
        }
        if (req.sort() != null) {
            menu.setSort(req.sort());
        }
        if (req.component() != null) {
            menu.setComponent(req.component());
        }
        if (req.icon() != null) {
            menu.setIcon(req.icon());
        }
        if (req.listLike() != null) {
            menu.setListLike(req.listLike());
        }
        menuMapper.updateById(menu);
        return menuMapper.selectById(id);
    }

    public void delete(String id) {
        require(id);
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw BizException.conflict("该菜单下存在子菜单，无法删除");
        }
        Long roleRef = roleMenuMapper.selectCount(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, id));
        if (roleRef != null && roleRef > 0) {
            throw BizException.conflict("该菜单已授权给角色，无法删除");
        }
        menuMapper.deleteById(id);
    }

    private Map<String, Object> toMap(SysMenu m) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", m.getId());
        node.put("code", m.getCode());
        node.put("name", m.getName());
        node.put("path", m.getPath());
        node.put("parentId", m.getParentId());
        node.put("sort", m.getSort());
        node.put("component", m.getComponent());
        node.put("icon", m.getIcon());
        node.put("listLike", m.getListLike());
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    private SysMenu require(String id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw BizException.notFound();
        }
        return menu;
    }
}
