package com.school.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.scope.PublicDataScope;
import com.school.common.security.CurrentPrincipal;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.system.dto.ResetPasswordRequest;
import com.school.system.dto.UserCreateRequest;
import com.school.system.dto.UserRoleAssignRequest;
import com.school.system.dto.UserUpdateRequest;
import com.school.system.entity.SysUser;
import com.school.system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统用户管理（M0501）。平台级数据，仅菜单/按钮权限控制，不套业务数据范围过滤。
 */
@RestController
@RequestMapping("/api/system/users")
@RequireMenu("M0501")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @GetMapping
    public Map<String, Object> page(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String roleId,
                                    @RequestParam(required = false) String studentId,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long pageSize) {
        Page<SysUser> p = userService.page(keyword, status, roleId, studentId, page, pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", p.getRecords());
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("pageSize", p.getSize());
        return ApiResponse.ok(data);
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-USER-ADD")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody UserCreateRequest req) {
        return ApiResponse.ok(userService.create(req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-USER-EDIT")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody UserUpdateRequest req) {
        return ApiResponse.ok(userService.update(id, req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-USER-DEL")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        userService.delete(id, CurrentPrincipal.require().userId());
        return ApiResponse.ok(null);
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-USER-EDIT")
    @GetMapping("/{id}/roles")
    public Map<String, Object> getUserRoles(@PathVariable String id) {
        return ApiResponse.ok(userService.getUserRoles(id));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-USER-EDIT")
    @PutMapping("/{id}/roles")
    public Map<String, Object> assignRoles(@PathVariable String id, @RequestBody UserRoleAssignRequest req) {
        userService.assignRoles(id, req);
        return ApiResponse.ok(null);
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-USER-EDIT")
    @PutMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable String id, @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.newPassword());
        return ApiResponse.ok(null);
    }
}
