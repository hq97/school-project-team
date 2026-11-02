package com.school.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.asset.entity.Asset;
import com.school.asset.mapper.AssetMapper;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产台账查询（统一台账，PUBLIC，仅菜单/按钮权限控制，不做记录级过滤）。
 *
 * <p>列表仍统一走 {@link ScopeQueryBuilder} + {@link ScopeWrapper} 唯一出口——
 * PUBLIC 归属下 buildWhere 返回空范围（仅携带 assetType 过滤），语义上无记录级过滤，
 * 但保持数据访问口径一致（门禁4 规则③）。</p>
 */
@Service
public class AssetService {

    private final AssetMapper assetMapper;

    public AssetService(AssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public List<Map<String, Object>> list(HttpServletRequest request, String assetType) {
        Map<String, Object> extra = new HashMap<>();
        if (assetType != null && !assetType.isEmpty()) {
            extra.put("assetType", assetType);
        }
        QueryWrapper<Asset> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
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
