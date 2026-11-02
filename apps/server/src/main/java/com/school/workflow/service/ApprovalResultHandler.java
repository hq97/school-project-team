package com.school.workflow.service;

/**
 * 审批结果回调：审批流引擎在终审通过 / 驳回后，按 {@link #businessType()} 路由到对应业务实现，
 * 联动业务表状态（采购 APPROVED/REJECTED、领用 COMPLETED/REJECTED + 库存回滚等）。
 *
 * <p><b>依赖方向（防循环）</b>：接口定义在 workflow 包，业务模块依赖本接口（业务 → workflow）；
 * {@link FlowEngineService} 只依赖 {@code List<ApprovalResultHandler>}（接口集合，不依赖具体业务），
 * 业务实现 {@code @Component} 由 Spring 自动收集注入，无循环。</p>
 *
 * <p><b>事务语义</b>：回调在 {@link FlowEngineService#approve} 的同一事务内执行（REQUIRED 传播），
 * 业务状态更新与流程状态流转原子提交/回滚。</p>
 */
public interface ApprovalResultHandler {

    /** 绑定的业务类型（如 procurement / material），与 flow_template.businessType 一致。 */
    String businessType();

    /** 终审通过（流程实例 status=COMPLETED）。 */
    void onApproved(String businessId);

    /** 驳回/终止（流程实例 status=REJECTED / TERMINATED）。 */
    void onRejected(String businessId);
}
