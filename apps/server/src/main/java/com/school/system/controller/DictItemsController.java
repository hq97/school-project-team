package com.school.system.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.web.ApiResponse;
import com.school.system.entity.SysDict;
import com.school.system.service.DictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开字典只读消费入口（{@code /api/dicts/items}）。
 *
 * <p>与 {@link DictController}（{@code /api/system/dicts}，类级 {@code @RequireMenu("M0505")} 仅管理员）分离：
 * 本入口为业务表单下拉选项（报修「故障类型」等）提供无需登录的只读字典项，对应
 * {@code SecurityConfig} 白名单 {@code /api/dicts/items/**}（ADR v3.0 白名单）。</p>
 *
 * <p>只返回 {@code enabled=true} 的项，按 {@code sort} 升序；每个元素 {@code {label: itemName, value: itemCode}}，
 * 对齐前端 {@code apps/web/src/api/biz.ts listDictItems} 的消费契约。</p>
 */
@RestController
@RequestMapping("/api/dicts/items")
public class DictItemsController {

    private final DictService dictService;

    public DictItemsController(DictService dictService) {
        this.dictService = dictService;
    }

    @PublicDataScope(reason = "字典只读消费：业务表单下拉选项，不读写业务数据")
    @GetMapping
    public Map<String, Object> items(@RequestParam String dictCode) {
        List<Map<String, Object>> items = dictService.list(dictCode).stream()
                .filter(d -> Boolean.TRUE.equals(d.getEnabled()))
                .map(this::view)
                .toList();
        return ApiResponse.ok(items);
    }

    private Map<String, Object> view(SysDict d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", d.getItemName());
        m.put("value", d.getItemCode());
        return m;
    }
}
