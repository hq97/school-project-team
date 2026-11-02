package com.school.material.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.asset.entity.Asset;
import com.school.asset.mapper.AssetMapper;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.common.util.Ids;
import com.school.material.dto.MaterialRequisitionRequest;
import com.school.material.entity.MaterialRequisition;
import com.school.material.mapper.MaterialRequisitionMapper;
import com.school.workflow.service.FlowEngineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物资领用（走审批流，businessType=material）。含 C9 库存 CAS。
 *
 * <p>数据范围：ORG（ownerField=applicantId / deptField=applicantDeptId / termField=termId）。</p>
 */
@Service
public class MaterialService {

    private final MaterialRequisitionMapper mapper;
    private final AssetMapper assetMapper;
    private final FlowEngineService flowEngine;

    public MaterialService(MaterialRequisitionMapper mapper, AssetMapper assetMapper, FlowEngineService flowEngine) {
        this.mapper = mapper;
        this.assetMapper = assetMapper;
        this.flowEngine = flowEngine;
    }

    /** 可领用资产列表（PUBLIC，仅菜单控制）。 */
    public List<Map<String, Object>> assets() {
        QueryWrapper<Asset> qw = new QueryWrapper<>();
        qw.eq("\"status\"", "IN_STOCK");
        qw.gt("\"availableQty\"", 0);
        qw.orderByAsc("\"name\"");
        return assetMapper.selectList(qw).stream().map(this::assetView).toList();
    }

    /**
     * 提交领用申请（≤3 步，同一事务）：
     * ① 原子扣减库存（CAS，0 行 → 40903）② 写入申请（PENDING）③ 启动审批流。
     */
    @Transactional
    public Map<String, Object> submit(MaterialRequisitionRequest req) {
        Principal p = CurrentPrincipal.require();
        Asset asset = assetMapper.selectById(req.assetId());
        if (asset == null) {
            throw BizException.notFound();
        }
        int rows = assetMapper.deductStock(req.assetId(), req.quantity());
        if (rows == 0) {
            Asset fresh = assetMapper.selectById(req.assetId());
            throw BizException.stockShort(Map.of(
                    "name", fresh.getName(),
                    "availableQty", fresh.getAvailableQty(),
                    "requestedQty", req.quantity()));
        }
        MaterialRequisition e = new MaterialRequisition();
        e.setId(Ids.next("mat"));
        e.setApplyNo(Ids.next("MAT"));
        e.setApplicantId(p.userId());
        e.setApplicantDeptId(primaryDept(p));
        e.setAssetId(req.assetId());
        e.setQuantity(req.quantity());
        e.setTermId(p.currentTermId());
        e.setStatus("PENDING");
        e.setCreatedAt(OffsetDateTime.now());
        mapper.insert(e);
        flowEngine.start("material", e.getId(), p.userId());
        return view(e);
    }

    /** 我的申请（forceSelf），带审批进度。 */
    public List<Map<String, Object>> my(HttpServletRequest request) {
        QueryWrapper<MaterialRequisition> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        List<MaterialRequisition> list = mapper.selectList(qw);
        Map<String, Map<String, Object>> flows = flowEngine.instancesByBusiness(
                "material", list.stream().map(MaterialRequisition::getId).toList());
        return list.stream().map(e -> {
            Map<String, Object> v = view(e);
            v.put("flow", flows.get(e.getId()));
            return v;
        }).toList();
    }

    /** 领用详情：matchRecord 判归属，不通过抛 40304 防枚举。 */
    public Map<String, Object> detail(HttpServletRequest request, String id) {
        MaterialRequisition e = mapper.selectById(id);
        if (e == null) {
            throw BizException.notFound();
        }
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);
        if (!scope.matchRecord(record(e))) {
            throw BizException.notFound();
        }
        Map<String, Object> v = view(e);
        v.put("flow", flowEngine.instanceByBusiness("material", id));
        return v;
    }

    private Map<String, Object> view(MaterialRequisition e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("applyNo", e.getApplyNo());
        m.put("applicantId", e.getApplicantId());
        m.put("applicantDeptId", e.getApplicantDeptId());
        m.put("assetId", e.getAssetId());
        m.put("quantity", e.getQuantity());
        m.put("termId", e.getTermId());
        m.put("status", e.getStatus());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private Map<String, Object> assetView(Asset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("assetType", a.getAssetType());
        m.put("availableQty", a.getAvailableQty());
        m.put("quantity", a.getQuantity());
        m.put("location", a.getLocation());
        m.put("borrowDomain", a.getBorrowDomain());
        return m;
    }

    private Map<String, Object> record(MaterialRequisition e) {
        Map<String, Object> r = new HashMap<>();
        r.put("applicantId", e.getApplicantId());
        r.put("applicantDeptId", e.getApplicantDeptId());
        r.put("termId", e.getTermId());
        return r;
    }

    static String primaryDept(Principal p) {
        return p.deptIds().isEmpty() ? null : p.deptIds().get(0);
    }
}
