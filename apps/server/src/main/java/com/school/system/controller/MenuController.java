package com.school.system.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.system.dto.MenuCreateRequest;
import com.school.system.dto.MenuUpdateRequest;
import com.school.system.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统菜单管理（M0503）。
 */
@RestController
@RequestMapping("/api/system/menus")
@RequireMenu("M0503")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @GetMapping
    public Map<String, Object> tree() {
        return ApiResponse.ok(menuService.tree());
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-MENU-ADD")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody MenuCreateRequest req) {
        return ApiResponse.ok(menuService.create(req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-MENU-EDIT")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody MenuUpdateRequest req) {
        return ApiResponse.ok(menuService.update(id, req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-MENU-DEL")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        menuService.delete(id);
        return ApiResponse.ok(null);
    }
}
