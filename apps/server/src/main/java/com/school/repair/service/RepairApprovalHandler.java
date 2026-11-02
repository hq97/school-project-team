package com.school.repair.service;

import com.school.repair.entity.RepairTicket;
import com.school.repair.mapper.RepairTicketMapper;
import com.school.workflow.service.ApprovalResultHandler;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 报修审批结果回写：终审通过 → COMPLETED + completedAt；驳回/终止 → REJECTED。
 *
 * <p>回调在 {@code FlowEngineService#approve} 的同一事务内执行（REQUIRED 传播），
 * 业务状态与流程状态原子提交/回滚。只做按 businessId 的定点写回（selectById/updateById），
 * 满足门禁4 规则③「单点写回免走 ScopeQueryBuilder」的豁免口径。</p>
 */
@Component
public class RepairApprovalHandler implements ApprovalResultHandler {

    private final RepairTicketMapper mapper;

    public RepairApprovalHandler(RepairTicketMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String businessType() {
        return "repair";
    }

    @Override
    public void onApproved(String businessId) {
        RepairTicket e = mapper.selectById(businessId);
        if (e == null || "COMPLETED".equals(e.getStatus())) {
            return;
        }
        e.setStatus("COMPLETED");
        e.setCompletedAt(OffsetDateTime.now());
        mapper.updateById(e);
    }

    @Override
    public void onRejected(String businessId) {
        RepairTicket e = mapper.selectById(businessId);
        if (e == null || "REJECTED".equals(e.getStatus())) {
            return;
        }
        e.setStatus("REJECTED");
        mapper.updateById(e);
    }
}
