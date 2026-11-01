package com.school.system.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.system.dto.DictCreateRequest;
import com.school.system.dto.DictUpdateRequest;
import com.school.system.service.DictService;
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

import java.util.Map;

/**
 * 数据字典管理（M0505）。维护入口仅 R-ADMIN（BTN-DICT-EDIT），读接口不挂菜单（docs/03 决策 19）。
 */
@RestController
@RequestMapping("/api/system/dicts")
@RequireMenu("M0505")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String dictCode) {
        return ApiResponse.ok(dictService.list(dictCode));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-DICT-EDIT")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody DictCreateRequest req) {
        return ApiResponse.ok(dictService.create(req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-DICT-EDIT")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody DictUpdateRequest req) {
        return ApiResponse.ok(dictService.update(id, req));
    }

    @PublicDataScope(reason = "系统管理平台级数据，仅菜单/按钮权限控制")
    @RequireButton("BTN-DICT-EDIT")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        dictService.delete(id);
        return ApiResponse.ok(null);
    }
}
