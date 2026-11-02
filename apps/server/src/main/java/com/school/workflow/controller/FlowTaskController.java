package com.school.workflow.controller;

import com.school.common.scope.PublicDataScope;
import com.school.common.security.CurrentPrincipal;
import com.school.common.security.RequireMenu;
import com.school.common.web.ApiResponse;
import com.school.workflow.dto.FlowInstanceStartRequest;
import com.school.workflow.dto.FlowTaskApproveRequest;
import com.school.workflow.service.FlowEngineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审批中心（M0102 待办审批）。docs/07 REQ-D3。
 *
 * <p>数据范围说明：待办/审批的可见与越权判定由引擎内部 {@code covers/assertDataScope}
 * 处理（命中当前节点审批角色 + 数据范围覆盖发起人归属），属审批引擎自有的横切数据，
 * 非标准组织型/关系型/角色授权型归属，故此处声明 {@link PublicDataScope} 交由引擎判定。</p>
 */
@RestController
@RequestMapping("/api/flow")
@RequireMenu("M0102")
public class FlowTaskController {

    private final FlowEngineService engine;

    public FlowTaskController(FlowEngineService engine) {
        this.engine = engine;
    }

    @PublicDataScope(reason = "审批待办范围由引擎 covers 判定（命中角色 + 数据范围覆盖发起人）")
    @GetMapping("/todos")
    public Map<String, Object> todos() {
        return ApiResponse.ok(engine.myTodos(CurrentPrincipal.require()));
    }

    @PublicDataScope(reason = "已办仅本人实际审批记录，天然 SELF 语义由引擎按 approverId 过滤")
    @GetMapping("/done")
    public Map<String, Object> done() {
        return ApiResponse.ok(engine.myDone(CurrentPrincipal.require()));
    }

    @PublicDataScope(reason = "审批动作越权由引擎 assertDataScope 校验（AC-D3-4/8）")
    @PostMapping("/tasks/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id, @Valid @RequestBody FlowTaskApproveRequest req) {
        return ApiResponse.ok(engine.approve(id, req.action(), req.comment(), CurrentPrincipal.require()));
    }

    /**
     * 业务发起入口。真实业务模块应在自身 Controller 通过 {@code FlowEngineService.start()} 调用
     * （在业务事务内发起）；本端点为独立接入/联调用，发起人取当前登录用户。
     */
    @PublicDataScope(reason = "发起人由登录态取，不由请求体传，引擎内部不读业务数据")
    @PostMapping("/instances/start")
    public Map<String, Object> start(@Valid @RequestBody FlowInstanceStartRequest req) {
        return ApiResponse.ok(engine.start(req.businessType(), req.businessId(), CurrentPrincipal.require().userId()));
    }

    @PublicDataScope(reason = "实例查询由业务侧按需调用，越权由引擎归属判定")
    @GetMapping("/instances/{id}")
    public Map<String, Object> instance(@PathVariable String id) {
        return ApiResponse.ok(engine.instance(id));
    }
}
