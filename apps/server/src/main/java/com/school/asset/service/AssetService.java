package com.school.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.asset.entity.Asset;
import com.school.asset.mapper.AssetMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产台账查询（统一台账，PUBLIC，仅菜单/按钮权限控制，不做记录级过滤）。
 */
@Service
public class AssetService {

    private final AssetMapper assetMapper;

    public AssetService(AssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public List<Map<String, Object>> list(String assetType) {
        QueryWrapper<Asset> qw = new QueryWrapper<>();
        if (assetType != null && !assetType.isEmpty()) {
            qw.eq("\"assetType\"", assetType);
        }
        qw.orderByAsc("\"name\"");
        return assetMapper.selectList(qw).stream().map(this::view).toList();
    }

    Map<String, Object> view(Asset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("assetType", a.getAssetType());
        m.put("quantity", a.getQuantity());
        m.put("availableQty", a.getAvailableQty());
        m.put("status", a.getStatus());
        m.put("location", a.getLocation());
        m.put("borrowDomain", a.getBorrowDomain());
        return m;
    }
}
