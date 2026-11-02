package com.school.material.service;

import com.school.asset.entity.AssetBorrow;
import com.school.asset.mapper.AssetBorrowMapper;
import com.school.asset.mapper.AssetMapper;
import com.school.common.util.Ids;
import com.school.material.entity.MaterialRequisition;
import com.school.material.mapper.MaterialRequisitionMapper;
import com.school.workflow.service.ApprovalResultHandler;
import org.springframework.stereotype.Component;

/**
 * 领用审批结果回写：终审通过 → COMPLETED（落 asset_borrow）；驳回 → REJECTED + 回滚库存。
 *
 * <p>库存扣减在提交时已完成，通过即状态收口；驳回必须回滚库存（幂等防重复回滚）。</p>
 */
@Component
public class MaterialApprovalHandler implements ApprovalResultHandler {

    private final MaterialRequisitionMapper mapper;
    private final AssetMapper assetMapper;
    private final AssetBorrowMapper borrowMapper;

    public MaterialApprovalHandler(MaterialRequisitionMapper mapper, AssetMapper assetMapper,
                                   AssetBorrowMapper borrowMapper) {
        this.mapper = mapper;
        this.assetMapper = assetMapper;
        this.borrowMapper = borrowMapper;
    }

    @Override
    public String businessType() {
        return "material";
    }

    @Override
    public void onApproved(String businessId) {
        MaterialRequisition e = mapper.selectById(businessId);
        if (e == null || "COMPLETED".equals(e.getStatus())) {
            return;
        }
        e.setStatus("COMPLETED");
        mapper.updateById(e);

        AssetBorrow b = new AssetBorrow();
        b.setId(Ids.next("borrow"));
        b.setAssetId(e.getAssetId());
        b.setBorrowerId(e.getApplicantId());
        b.setQuantity(e.getQuantity());
        b.setTermId(e.getTermId());
        b.setApplyNo(e.getApplyNo());
        borrowMapper.insert(b);
    }

    @Override
    public void onRejected(String businessId) {
        MaterialRequisition e = mapper.selectById(businessId);
        if (e == null || "REJECTED".equals(e.getStatus())) {
            return;
        }
        e.setStatus("REJECTED");
        mapper.updateById(e);
        assetMapper.restoreStock(e.getAssetId(), e.getQuantity());
    }
}
