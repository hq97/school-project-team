package com.school.workflow.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.workflow.dto.FlowTemplateCreateRequest;
import com.school.workflow.dto.FlowTemplateUpdateRequest;
import com.school.workflow.service.FlowTemplateService;
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
 * 流程模板管理（M0507，仅 R-ADMIN 维护）。docs/07 第六节。
 */
@RestController
@RequestMapping("/api/system/flow-templates")
@RequireMenu("M0507")
public class FlowTemplateController {

    private final FlowTemplateService service;

    public FlowTemplateController(FlowTemplateService service) {
        this.service = service;
    }

    @PublicDataScope(reason = "流程模板为平台级配置，仅菜单/按钮权限控制，不套业务数据范围")
    @GetMapping
    public Map<String, Object> list() {
        return ApiResponse.ok(service.list());
    }

    @PublicDataScope(reason = "流程模板为平台级配置，仅菜单/按钮权限控制")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PublicDataScope(reason = "流程模板为平台级配置，仅菜单/按钮权限控制")
    @RequireButton("BTN-FLOW-TPL-EDIT")
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody FlowTemplateCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PublicDataScope(reason = "流程模板为平台级配置，仅菜单/按钮权限控制")
    @RequireButton("BTN-FLOW-TPL-EDIT")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody FlowTemplateUpdateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PublicDataScope(reason = "流程模板为平台级配置，仅菜单/按钮权限控制")
    @RequireButton("BTN-FLOW-TPL-EDIT")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
