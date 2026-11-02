package com.school.repair.controller;

import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireButton;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.repair.dto.FaultTypeCreateRequest;
import com.school.repair.dto.FaultTypeUpdateRequest;
import com.school.repair.dto.SpaceCreateRequest;
import com.school.repair.dto.SpaceUpdateRequest;
import com.school.repair.service.RepairConfigService;
import jakarta.servlet.http.HttpServletRequest;
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
 * 报修配置（M0405，仅后勤角色 R-LOGISTICS 可维护）。独立于 R-ADMIN 专属的
 * 字典管理(M0505)，只操作报修相关数据（故障类型 / 场地）。审批流程配置入口回归 M0507
 * 流程模板管理，本控制器不再提供流程写入端点。
 *
 * <p>配置均为平台级（PUBLIC）数据，仅菜单/按钮权限控制，不做记录级过滤；列表读取
 * 仍经 ScopeQueryBuilder 唯一出口注入（PUBLIC 下为空范围），写操作挂 BTN-REP-CONFIG。</p>
 */
@RestController
@RequestMapping("/api/repair/config")
@RequireMenu("M0405")
public class RepairConfigController {

    private final RepairConfigService service;

    public RepairConfigController(RepairConfigService service) {
        this.service = service;
    }

    // ---- 故障类型 ----

    @DataScope(resource = "sys_dict", ownership = OwnershipType.PUBLIC)
    @GetMapping("/fault-types")
    public Map<String, Object> faultTypes(HttpServletRequest request) {
        return ApiResponse.ok(service.faultTypes(request));
    }

    @DataScope(resource = "sys_dict", ownership = OwnershipType.PUBLIC)
    @RequireButton("BTN-REP-CONFIG")
    @PostMapping("/fault-types")
    public Map<String, Object> createFaultType(@Valid @RequestBody FaultTypeCreateRequest req) {
        return ApiResponse.ok(service.createFaultType(req));
    }

    @DataScope(resource = "sys_dict", ownership = OwnershipType.PUBLIC)
    @RequireButton("BTN-REP-CONFIG")
    @PutMapping("/fault-types/{id}")
    public Map<String, Object> updateFaultType(@PathVariable String id, @RequestBody FaultTypeUpdateRequest req) {
        return ApiResponse.ok(service.updateFaultType(id, req));
    }

    @DataScope(resource = "sys_dict", ownership = OwnershipType.PUBLIC)
    @RequireButton("BTN-REP-CONFIG")
    @DeleteMapping("/fault-types/{id}")
    public Map<String, Object> deleteFaultType(@PathVariable String id) {
        service.deleteFaultType(id);
        return ApiResponse.ok(null);
    }

    // ---- 报修场地 ----

    @DataScope(resource = "space", ownership = OwnershipType.PUBLIC)
    @GetMapping("/spaces")
    public Map<String, Object> spaces(HttpServletRequest request) {
        return ApiResponse.ok(service.spaces(request));
    }

    @DataScope(resource = "space", ownership = OwnershipType.PUBLIC)
    @RequireButton("BTN-REP-CONFIG")
    @PostMapping("/spaces")
    public Map<String, Object> createSpace(@Valid @RequestBody SpaceCreateRequest req) {
        return ApiResponse.ok(service.createSpace(req));
    }

    @DataScope(resource = "space", ownership = OwnershipType.PUBLIC)
    @RequireButton("BTN-REP-CONFIG")
    @PutMapping("/spaces/{id}")
    public Map<String, Object> updateSpace(@PathVariable String id, @RequestBody SpaceUpdateRequest req) {
        return ApiResponse.ok(service.updateSpace(id, req));
    }

    @DataScope(resource = "space", ownership = OwnershipType.PUBLIC)
    @RequireButton("BTN-REP-CONFIG")
    @DeleteMapping("/spaces/{id}")
    public Map<String, Object> deleteSpace(@PathVariable String id) {
        service.deleteSpace(id);
        return ApiResponse.ok(null);
    }
}
