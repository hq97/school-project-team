package com.school.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.scope.OwnershipResolver;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeLevel;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.entity.SysRole;
import com.school.system.entity.UserRole;
import com.school.system.mapper.SysRoleMapper;
import com.school.system.mapper.UserRoleMapper;
import com.school.workflow.entity.FlowInstance;
import com.school.workflow.entity.FlowNode;
import com.school.workflow.entity.FlowTask;
import com.school.workflow.entity.FlowTemplate;
import com.school.workflow.mapper.FlowInstanceMapper;
import com.school.workflow.mapper.FlowNodeMapper;
import com.school.workflow.mapper.FlowTaskMapper;
import com.school.workflow.mapper.FlowTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批流引擎核心（docs/07 REQ-D3）。
 *
 * <p>固定线性流转：提交 → 审批[可配置多级] → 完成/驳回。关键不变量：</p>
 * <ul>
 *   <li><b>快照确定性（AC-D3-3）</b>：实例创建时把模板+节点完整快照进 templateSnap，
 *       后续流转只读快照，模板修改不影响进行中实例。</li>
 *   <li><b>审批双校验（AC-D3-4/8）</b>：每次审批动作前①命中当前节点审批角色（40302）②数据范围覆盖发起人归属（40303）。</li>
 *   <li><b>留痕（AC-D3-5）</b>：每级审批回填 approverId + conclusion + comment + actedAt。</li>
 * </ul>
 */
@Service
public class FlowEngineService {

    private final FlowTemplateMapper templateMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskMapper taskMapper;
    private final SysRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OwnershipResolver ownershipResolver;
    private final ObjectMapper objectMapper;

    public FlowEngineService(FlowTemplateMapper templateMapper, FlowNodeMapper nodeMapper,
                             FlowInstanceMapper instanceMapper, FlowTaskMapper taskMapper,
                             SysRoleMapper roleMapper, UserRoleMapper userRoleMapper,
                             OwnershipResolver ownershipResolver, ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.nodeMapper = nodeMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.ownershipResolver = ownershipResolver;
        this.objectMapper = objectMapper;
    }

    /** 业务发起审批：按 businessType 绑定的启用模板启动实例，创建首节点待办。 */
    @Transactional
    public Map<String, Object> start(String businessType, String businessId, String creatorId) {
        FlowTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<FlowTemplate>()
                        .eq(FlowTemplate::getBusinessType, businessType)
                        .eq(FlowTemplate::getEnabled, true));
        if (tpl == null) {
            throw BizException.conflict("业务类型「" + businessType + "」未配置启用的流程模板");
        }
        List<FlowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getTemplateId, tpl.getId()).orderByAsc(FlowNode::getSeq));
        if (nodes.isEmpty()) {
            throw BizException.conflict("流程模板「" + tpl.getName() + "」未配置审批节点");
        }
        // AC-D3-6：首节点审批角色下须有可用人员，否则不允许提交（配置异常）
        assertRoleHasUser(nodes.get(0).getApproveRole());

        TemplateSnap snap = buildSnap(tpl, nodes);
        FlowInstance inst = new FlowInstance();
        inst.setId(Ids.next("flowinst"));
        inst.setBusinessType(businessType);
        inst.setBusinessId(businessId);
        inst.setTemplateId(tpl.getId());
        inst.setTemplateSnap(writeJson(snap));
        inst.setCurrentSeq(1);
        inst.setStatus("PENDING");
        inst.setCreatorId(creatorId);
        instanceMapper.insert(inst);

        createTask(inst.getId(), 1, nodes.get(0).getApproveRole());
        return instanceView(instanceMapper.selectById(inst.getId()));
    }

    /** 我的待办（命中当前节点审批角色 + 数据范围覆盖发起人）。 */
    public List<Map<String, Object>> myTodos(Principal approver) {
        if (approver.roleCodes().isEmpty()) {
            return List.of();
        }
        List<FlowTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<FlowTask>()
                        .isNull(FlowTask::getConclusion)
                        .in(FlowTask::getRole, approver.roleCodes())
                        .orderByAsc(FlowTask::getSeq));
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowTask task : tasks) {
            FlowInstance inst = instanceMapper.selectById(task.getInstanceId());
            if (inst == null || !"PENDING".equals(inst.getStatus())) {
                continue;
            }
            if (task.getSeq() != inst.getCurrentSeq()) {
                continue;
            }
            if (!covers(approver, inst.getCreatorId())) {
                continue;
            }
            result.add(todoView(task, inst));
        }
        return result;
    }

    /** 我的已办（本人实际审批过的记录）。 */
    public List<Map<String, Object>> myDone(Principal approver) {
        List<FlowTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<FlowTask>()
                        .eq(FlowTask::getApproverId, approver.userId())
                        .isNotNull(FlowTask::getConclusion)
                        .orderByDesc(FlowTask::getActedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowTask task : tasks) {
            FlowInstance inst = instanceMapper.selectById(task.getInstanceId());
            if (inst == null) {
                continue;
            }
            result.add(doneView(task, inst));
        }
        return result;
    }

    /** 审批动作：通过流转下一节点/完成，驳回按模板策略退回发起人或终止。 */
    @Transactional
    public Map<String, Object> approve(String taskId, String action, String comment, Principal approver) {
        FlowTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound();
        }
        if (task.getConclusion() != null) {
            throw BizException.conflict("该审批任务已处理");
        }
        FlowInstance inst = instanceMapper.selectById(task.getInstanceId());
        if (inst == null) {
            throw BizException.notFound();
        }
        if (!"PENDING".equals(inst.getStatus())) {
            throw BizException.conflict("流程已结束，无法审批");
        }
        if (task.getSeq() != inst.getCurrentSeq()) {
            throw BizException.conflict("该节点尚未轮到审批");
        }
        // AC-D3-4/8 审批双校验
        if (!approver.roleCodes().contains(task.getRole())) {
            throw BizException.actionDenied(); // 40302：未命中当前节点审批角色
        }
        assertDataScope(approver, inst.getCreatorId()); // 40303：数据范围不覆盖发起人归属

        String conclusion = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        task.setApproverId(approver.userId());
        task.setConclusion(conclusion);
        task.setComment(comment);
        task.setActedAt(OffsetDateTime.now());
        taskMapper.updateById(task);

        TemplateSnap snap = parseSnap(inst.getTemplateSnap());
        if ("APPROVED".equals(conclusion)) {
            if (inst.getCurrentSeq() < snap.nodes().size()) {
                int nextSeq = inst.getCurrentSeq() + 1;
                TemplateSnap.NodeSnap next = snap.nodes().get(nextSeq - 1);
                assertRoleHasUser(next.approveRole());
                createTask(inst.getId(), nextSeq, next.approveRole());
                inst.setCurrentSeq(nextSeq);
            } else {
                inst.setStatus("COMPLETED");
            }
        } else {
            inst.setStatus("TERMINATE".equals(snap.rejectPolicy()) ? "TERMINATED" : "REJECTED");
        }
        instanceMapper.updateById(inst);
        return instanceView(instanceMapper.selectById(inst.getId()));
    }

    /** 查询实例（供业务侧查询审批进度）。 */
    public Map<String, Object> instance(String instanceId) {
        FlowInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw BizException.notFound();
        }
        return instanceView(inst);
    }

    // ---- 数据范围判定 ----

    /** 审批人数据范围是否覆盖发起人归属（docs/04 §4.2 三级范围语义）。 */
    private boolean covers(Principal approver, String creatorId) {
        if (approver.scope() == ScopeLevel.ALL) {
            return true;
        }
        if (approver.scope() == ScopeLevel.SELF) {
            return approver.userId().equals(creatorId);
        }
        Principal creator = ownershipResolver.resolve(creatorId);
        return !Collections.disjoint(approver.gradeNodeIds(), creator.gradeNodeIds());
    }

    private void assertDataScope(Principal approver, String creatorId) {
        if (!covers(approver, creatorId)) {
            throw BizException.scopeDenied();
        }
    }

    /** AC-D3-6：审批角色下须有可用人员。 */
    private void assertRoleHasUser(String roleCode) {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, roleCode));
        if (role == null) {
            throw BizException.conflict("审批角色不存在：" + roleCode);
        }
        Long cnt = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, role.getId()));
        if (cnt == null || cnt == 0) {
            throw BizException.conflict("审批角色「" + roleCode + "」下无可用人员，请先分配该角色");
        }
    }

    // ---- 快照 ----

    private TemplateSnap buildSnap(FlowTemplate tpl, List<FlowNode> nodes) {
        List<TemplateSnap.NodeSnap> nodeSnaps = nodes.stream()
                .map(n -> new TemplateSnap.NodeSnap(n.getSeq(), n.getName(), n.getApproveRole()))
                .toList();
        return new TemplateSnap(tpl.getName(), tpl.getBusinessType(), tpl.getRejectPolicy(), tpl.getVersion(), nodeSnaps);
    }

    private TemplateSnap parseSnap(String json) {
        try {
            return objectMapper.readValue(json, TemplateSnap.class);
        } catch (Exception e) {
            throw new IllegalStateException("流程快照解析失败", e);
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("流程快照序列化失败", e);
        }
    }

    private void createTask(String instanceId, int seq, String role) {
        FlowTask task = new FlowTask();
        task.setId(Ids.next("flowtask"));
        task.setInstanceId(instanceId);
        task.setSeq(seq);
        task.setRole(role);
        taskMapper.insert(task);
    }

    // ---- 视图 ----

    private Map<String, Object> instanceView(FlowInstance inst) {
        TemplateSnap snap = parseSnap(inst.getTemplateSnap());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", inst.getId());
        view.put("businessType", inst.getBusinessType());
        view.put("businessId", inst.getBusinessId());
        view.put("templateId", inst.getTemplateId());
        view.put("templateName", snap.templateName());
        view.put("currentSeq", inst.getCurrentSeq());
        view.put("status", inst.getStatus());
        view.put("creatorId", inst.getCreatorId());
        view.put("createdAt", inst.getCreatedAt());
        List<Map<String, Object>> nodeViews = snap.nodes().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", n.seq());
            m.put("name", n.name());
            m.put("approveRole", n.approveRole());
            return m;
        }).toList();
        view.put("nodes", nodeViews);
        if (inst.getCurrentSeq() >= 1 && inst.getCurrentSeq() <= snap.nodes().size()) {
            view.put("currentNodeName", snap.nodes().get(inst.getCurrentSeq() - 1).name());
        }
        return view;
    }

    private Map<String, Object> todoView(FlowTask task, FlowInstance inst) {
        TemplateSnap snap = parseSnap(inst.getTemplateSnap());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("taskId", task.getId());
        view.put("instanceId", inst.getId());
        view.put("businessType", inst.getBusinessType());
        view.put("businessId", inst.getBusinessId());
        view.put("templateName", snap.templateName());
        view.put("seq", task.getSeq());
        view.put("nodeName", nodeName(snap, task.getSeq()));
        view.put("role", task.getRole());
        view.put("creatorId", inst.getCreatorId());
        view.put("createdAt", inst.getCreatedAt());
        return view;
    }

    private Map<String, Object> doneView(FlowTask task, FlowInstance inst) {
        TemplateSnap snap = parseSnap(inst.getTemplateSnap());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("taskId", task.getId());
        view.put("instanceId", inst.getId());
        view.put("businessType", inst.getBusinessType());
        view.put("businessId", inst.getBusinessId());
        view.put("templateName", snap.templateName());
        view.put("seq", task.getSeq());
        view.put("nodeName", nodeName(snap, task.getSeq()));
        view.put("role", task.getRole());
        view.put("conclusion", task.getConclusion());
        view.put("comment", task.getComment());
        view.put("actedAt", task.getActedAt());
        return view;
    }

    private String nodeName(TemplateSnap snap, int seq) {
        for (TemplateSnap.NodeSnap n : snap.nodes()) {
            if (n.seq() == seq) {
                return n.name();
            }
        }
        return "";
    }

    /** 模板快照（JSON 序列化到 templateSnap）。 */
    public record TemplateSnap(String templateName, String businessType, String rejectPolicy,
                               Integer version, List<NodeSnap> nodes) {
        public record NodeSnap(Integer seq, String name, String approveRole) {
        }
    }
}
