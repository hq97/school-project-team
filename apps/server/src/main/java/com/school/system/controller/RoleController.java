package com.school.system.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.system.dto.RoleCreateRequest;
import com.school.system.dto.RoleMenuAssignRequest;
import com.school.system.dto.RoleScopeAssignRequest;
import com.school.system.dto.RoleUpdateRequest;
import com.school.system.service.RoleService;
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
 * 系统角色管理（M0502）+ 授权（菜单/按钮/数据范围）。
 */
@RestController
@RequestMapping("/api/system/roles")
@RequireMenu("M0502")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @GetMapping
    public Map<String, Object> list() {
        return ApiResponse.ok(roleService.list());
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-ROLE-ADD")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody RoleCreateRequest req) {
        return ApiResponse.ok(roleService.create(req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-ROLE-EDIT")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody RoleUpdateRequest req) {
        return ApiResponse.ok(roleService.update(id, req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-ROLE-DEL")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        roleService.delete(id);
        return ApiResponse.ok(null);
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-ROLE-PERM")
    @GetMapping("/{id}/menus")
    public Map<String, Object> assignedMenus(@PathVariable String id) {
        return ApiResponse.ok(roleService.assignedMenus(id));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-ROLE-PERM")
    @PutMapping("/{id}/menus")
    public Map<String, Object> assignMenus(@PathVariable String id, @RequestBody RoleMenuAssignRequest req) {
        roleService.assignMenus(id, req);
        return ApiResponse.ok(null);
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-ROLE-PERM")
    @PutMapping("/{id}/scopes")
    public Map<String, Object> assignScopes(@PathVariable String id, @RequestBody RoleScopeAssignRequest req) {
        roleService.assignScopes(id, req);
        return ApiResponse.ok(null);
    }
}
