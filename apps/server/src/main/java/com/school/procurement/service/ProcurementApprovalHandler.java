package com.school.procurement.service;

import com.school.procurement.entity.ProcurementProject;
import com.school.procurement.mapper.ProcurementProjectMapper;
import com.school.workflow.service.ApprovalResultHandler;
import org.springframework.stereotype.Component;

/**
 * 采购审批结果回写：终审通过 → APPROVED；驳回 → REJECTED。
 *
 * <p>独立 {@code @Component}（非 ProcurementService 本身），避免
 * ProcurementService → FlowEngineService → List&lt;ApprovalResultHandler&gt; → ProcurementService 的循环依赖。</p>
 */
@Component
public class ProcurementApprovalHandler implements ApprovalResultHandler {

    private final ProcurementProjectMapper mapper;

    public ProcurementApprovalHandler(ProcurementProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String businessType() {
        return "procurement";
    }

    @Override
    public void onApproved(String businessId) {
        updateStatus(businessId, "APPROVED");
    }

    @Override
    public void onRejected(String businessId) {
        updateStatus(businessId, "REJECTED");
    }

    private void updateStatus(String businessId, String status) {
        ProcurementProject e = mapper.selectById(businessId);
        if (e == null) {
            return;
        }
        e.setStatus(status);
        mapper.updateById(e);
    }
}
