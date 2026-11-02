package com.school.asset.controller;

import com.school.asset.service.AssetService;
import com.school.common.scope.DataScope;
import com.school.common.scope.OwnershipType;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 资产台账（M0303 库存查询）。PUBLIC：全校共享台账，仅菜单控制，无记录级归属过滤。
 */
@RestController
@RequestMapping("/api/asset")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @DataScope(resource = "asset", ownership = OwnershipType.PUBLIC)
    @RequireMenu("M0303")
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String assetType, HttpServletRequest request) {
        return ApiResponse.ok(assetService.list(request, assetType));
    }
}
