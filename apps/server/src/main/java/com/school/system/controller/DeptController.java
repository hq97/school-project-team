package com.school.system.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.system.dto.DeptCreateRequest;
import com.school.system.dto.DeptUpdateRequest;
import com.school.system.service.DeptService;
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
 * 系统部门管理（M0504）。
 */
@RestController
@RequestMapping("/api/system/depts")
@RequireMenu("M0504")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @GetMapping
    public Map<String, Object> tree() {
        return ApiResponse.ok(deptService.tree());
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-DEPT-ADD")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody DeptCreateRequest req) {
        return ApiResponse.ok(deptService.create(req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-DEPT-EDIT")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody DeptUpdateRequest req) {
        return ApiResponse.ok(deptService.update(id, req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-SYS-DEPT-DEL")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        deptService.delete(id);
        return ApiResponse.ok(null);
    }
}
