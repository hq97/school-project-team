package com.school.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.entity.SysRole;
import com.school.system.mapper.SysRoleMapper;
import com.school.workflow.dto.FlowNodeDto;
import com.school.workflow.dto.FlowTemplateCreateRequest;
import com.school.workflow.dto.FlowTemplateUpdateRequest;
import com.school.workflow.entity.FlowInstance;
import com.school.workflow.entity.FlowNode;
import com.school.workflow.entity.FlowTemplate;
import com.school.workflow.mapper.FlowInstanceMapper;
import com.school.workflow.mapper.FlowNodeMapper;
import com.school.workflow.mapper.FlowTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程模板管理（M0507）。模板 + 有序节点列表 CRUD。
 *
 * <p>节点固定线性（nodeType=LINEAR），{@code approveRole} 为审批角色码，创建/更新时校验角色存在
 * （预防 AC-D3-6「节点角色无可用人员」的配置异常）。</p>
 */
@Service
public class FlowTemplateService {

    private final FlowTemplateMapper templateMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowInstanceMapper instanceMapper;
    private final SysRoleMapper roleMapper;

    public FlowTemplateService(FlowTemplateMapper templateMapper, FlowNodeMapper nodeMapper,
                               FlowInstanceMapper instanceMapper, SysRoleMapper roleMapper) {
        this.templateMapper = templateMapper;
        this.nodeMapper = nodeMapper;
        this.instanceMapper = instanceMapper;
        this.roleMapper = roleMapper;
    }

    /** 模板列表（含节点，供前端树/列表渲染）。 */
    public List<Map<String, Object>> list() {
        List<FlowTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<FlowTemplate>().orderByAsc(FlowTemplate::getBusinessType));
        return templates.stream().map(this::toView).toList();
    }

    public Map<String, Object> get(String id) {
        return toView(require(id));
    }

    @Transactional
    public FlowTemplate create(FlowTemplateCreateRequest req) {
        Long codeExists = templateMapper.selectCount(
                new LambdaQueryWrapper<FlowTemplate>().eq(FlowTemplate::getCode, req.code()));
        if (codeExists != null && codeExists > 0) {
            throw BizException.conflict("模板编码已存在");
        }
        Long bizExists = templateMapper.selectCount(
                new LambdaQueryWrapper<FlowTemplate>().eq(FlowTemplate::getBusinessType, req.businessType()));
        if (bizExists != null && bizExists > 0) {
            throw BizException.conflict("该业务类型已绑定流程模板");
        }
        List<FlowNodeDto> nodes = validateNodes(req.nodes());

        FlowTemplate tpl = new FlowTemplate();
        tpl.setId(Ids.next("flowtpl"));
        tpl.setCode(req.code());
        tpl.setName(req.name());
        tpl.setBusinessType(req.businessType());
        tpl.setRejectPolicy(normalizeRejectPolicy(req.rejectPolicy()));
        tpl.setEnabled(true);
        tpl.setVersion(1);
        templateMapper.insert(tpl);

        saveNodes(tpl.getId(), nodes);
        return templateMapper.selectById(tpl.getId());
    }

    @Transactional
    public FlowTemplate update(String id, FlowTemplateUpdateRequest req) {
        FlowTemplate tpl = require(id);
        if (req.name() != null) {
            tpl.setName(req.name());
        }
        if (req.rejectPolicy() != null) {
            tpl.setRejectPolicy(normalizeRejectPolicy(req.rejectPolicy()));
        }
        if (req.enabled() != null) {
            tpl.setEnabled(req.enabled());
        }
        // nodes 非空时全量替换节点列表（增删节点、调顺序），version+1（AC-D3-1「改配置即生效」作用于新申请）
        if (req.nodes() != null) {
            List<FlowNodeDto> nodes = validateNodes(req.nodes());
            nodeMapper.delete(new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getTemplateId, id));
            saveNodes(id, nodes);
            tpl.setVersion(tpl.getVersion() + 1);
        }
        templateMapper.updateById(tpl);
        return templateMapper.selectById(id);
    }

    @Transactional
    public void delete(String id) {
        require(id);
        // 存在流程实例时禁止删除（flow_instance.templateId 外键无级联，且历史实例须留痕）
        Long instCnt = instanceMapper.selectCount(
                new LambdaQueryWrapper<FlowInstance>().eq(FlowInstance::getTemplateId, id));
        if (instCnt != null && instCnt > 0) {
            throw BizException.conflict("该流程模板下存在流程实例，无法删除");
        }
        nodeMapper.delete(new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    /** 模板下节点列表（按 seq 升序）。 */
    public List<FlowNode> nodesOf(String templateId) {
        return nodeMapper.selectList(
                new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getTemplateId, templateId).orderByAsc(FlowNode::getSeq));
    }

    // ---- 内部 ----

    private List<FlowNodeDto> validateNodes(List<FlowNodeDto> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw BizException.conflict("流程至少需要一个审批节点");
        }
        for (FlowNodeDto n : nodes) {
            if (n.name() == null || n.name().isBlank()) {
                throw BizException.conflict("节点名称不能为空");
            }
            if (n.approveRole() == null || n.approveRole().isBlank()) {
                throw BizException.conflict("节点审批角色不能为空");
            }
            Long roleExists = roleMapper.selectCount(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, n.approveRole()));
            if (roleExists == null || roleExists == 0) {
                throw BizException.conflict("审批角色不存在：" + n.approveRole());
            }
        }
        return nodes;
    }

    private void saveNodes(String templateId, List<FlowNodeDto> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeDto d = nodes.get(i);
            FlowNode node = new FlowNode();
            node.setId(Ids.next("fnode"));
            node.setTemplateId(templateId);
            node.setSeq(i + 1);
            node.setName(d.name());
            node.setApproveRole(d.approveRole());
            node.setTimeoutHours(d.timeoutHours());
            node.setNodeType("LINEAR");
            nodeMapper.insert(node);
        }
    }

    private String normalizeRejectPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return "RETURN";
        }
        if (!"RETURN".equals(policy) && !"TERMINATE".equals(policy)) {
            throw BizException.conflict("驳回策略仅支持 RETURN / TERMINATE");
        }
        return policy;
    }

    private Map<String, Object> toView(FlowTemplate tpl) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", tpl.getId());
        view.put("code", tpl.getCode());
        view.put("name", tpl.getName());
        view.put("businessType", tpl.getBusinessType());
        view.put("rejectPolicy", tpl.getRejectPolicy());
        view.put("enabled", tpl.getEnabled());
        view.put("version", tpl.getVersion());
        view.put("nodes", nodesOf(tpl.getId()));
        return view;
    }

    private FlowTemplate require(String id) {
        FlowTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            throw BizException.notFound();
        }
        return tpl;
    }
}
