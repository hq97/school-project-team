package com.school.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.asset.entity.Space;
import com.school.asset.entity.SpaceOccupancy;
import com.school.asset.mapper.SpaceMapper;
import com.school.asset.mapper.SpaceOccupancyMapper;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.repair.dto.FaultTypeCreateRequest;
import com.school.repair.dto.FaultTypeUpdateRequest;
import com.school.repair.dto.RepairFlowNodeRequest;
import com.school.repair.dto.RepairFlowUpdateRequest;
import com.school.repair.dto.SpaceCreateRequest;
import com.school.repair.dto.SpaceUpdateRequest;
import com.school.repair.entity.RepairTicket;
import com.school.repair.mapper.RepairTicketMapper;
import com.school.system.entity.SysDict;
import com.school.system.entity.SysRole;
import com.school.system.mapper.SysDictMapper;
import com.school.system.mapper.SysRoleMapper;
import com.school.workflow.entity.FlowNode;
import com.school.workflow.entity.FlowTemplate;
import com.school.workflow.mapper.FlowNodeMapper;
import com.school.workflow.mapper.FlowTemplateMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报修配置（故障类型 / 报修场地 / 审批流程）。独立于 R-ADMIN 专属的字典管理(M0505)与
 * 流程模板管理(M0507)，供后勤角色 R-LOGISTICS 通过独立菜单 M0405 + 按钮 BTN-REP-CONFIG 维护。
 *
 * <p>配置均为平台级数据（PUBLIC，不做记录级过滤），但列表仍统一走
 * {@link ScopeQueryBuilder} + {@link ScopeWrapper} 唯一出口——PUBLIC 归属下 buildWhere 返回空范围，
 * 保持数据访问口径一致并满足门禁4 规则③（业务包列表/聚合查询必须依赖 ScopeQueryBuilder）。</p>
 */
@Service
public class RepairConfigService {

    private static final String DICT_FAULT_TYPE = "DICT-FAULT-TYPE";
    private static final String FLOW_CODE = "FT-REPAIR";
    private static final String FLOW_BIZ_TYPE = "repair";

    private static final List<String> SPACE_TYPES = List.of("CLASSROOM", "LAB", "MEETING_ROOM", "SPORT_VENUE");
    private static final List<String> SPACE_STATUSES = List.of("AVAILABLE", "DISABLED", "REPAIRING");
    private static final List<String> OPEN_SCOPES = List.of("SELF", "GRADE", "ALL");

    private final SysDictMapper dictMapper;
    private final SpaceMapper spaceMapper;
    private final SpaceOccupancyMapper spaceOccupancyMapper;
    private final RepairTicketMapper repairTicketMapper;
    private final FlowTemplateMapper templateMapper;
    private final FlowNodeMapper nodeMapper;
    private final SysRoleMapper roleMapper;

    public RepairConfigService(SysDictMapper dictMapper, SpaceMapper spaceMapper,
                               SpaceOccupancyMapper spaceOccupancyMapper, RepairTicketMapper repairTicketMapper,
                               FlowTemplateMapper templateMapper, FlowNodeMapper nodeMapper,
                               SysRoleMapper roleMapper) {
        this.dictMapper = dictMapper;
        this.spaceMapper = spaceMapper;
        this.spaceOccupancyMapper = spaceOccupancyMapper;
        this.repairTicketMapper = repairTicketMapper;
        this.templateMapper = templateMapper;
        this.nodeMapper = nodeMapper;
        this.roleMapper = roleMapper;
    }

    // ==================== 故障类型（DICT-FAULT-TYPE） ====================

    public List<Map<String, Object>> faultTypes(HttpServletRequest request) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("dictCode", DICT_FAULT_TYPE);
        QueryWrapper<SysDict> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByAsc("\"sort\"");
        return dictMapper.selectList(qw).stream().map(this::faultTypeView).toList();
    }

    @Transactional
    public Map<String, Object> createFaultType(FaultTypeCreateRequest req) {
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictCode, DICT_FAULT_TYPE)
                .eq(SysDict::getItemCode, req.itemCode()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("故障类型编码已存在");
        }
        SysDict d = new SysDict();
        d.setId(Ids.next("dict"));
        d.setDictCode(DICT_FAULT_TYPE);
        d.setItemCode(req.itemCode());
        d.setItemName(req.itemName());
        d.setSort(req.sort() == null ? 0 : req.sort());
        d.setEnabled(true);
        d.setIsCurrent(null);
        dictMapper.insert(d);
        return faultTypeView(dictMapper.selectById(d.getId()));
    }

    @Transactional
    public Map<String, Object> updateFaultType(String id, FaultTypeUpdateRequest req) {
        SysDict d = requireFaultType(id);
        if (req.itemName() != null) {
            d.setItemName(req.itemName());
        }
        if (req.sort() != null) {
            d.setSort(req.sort());
        }
        if (req.enabled() != null) {
            d.setEnabled(req.enabled());
        }
        dictMapper.updateById(d);
        return faultTypeView(dictMapper.selectById(id));
    }

    @Transactional
    public void deleteFaultType(String id) {
        SysDict d = requireFaultType(id);
        Long ref = repairTicketMapper.selectCount(
                new QueryWrapper<RepairTicket>().eq("\"faultType\"", d.getItemCode()));
        if (ref != null && ref > 0) {
            throw BizException.conflict("该故障类型已被报修工单引用，无法删除");
        }
        dictMapper.deleteById(id);
    }

    // ==================== 报修场地（space） ====================

    public List<Map<String, Object>> spaces(HttpServletRequest request) {
        QueryWrapper<Space> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByAsc("\"spaceType\"").orderByAsc("\"spaceName\"");
        return spaceMapper.selectList(qw).stream().map(this::spaceView).toList();
    }

    @Transactional
    public Map<String, Object> createSpace(SpaceCreateRequest req) {
        Space s = new Space();
        s.setId(Ids.next("space"));
        s.setSpaceName(req.spaceName());
        s.setSpaceType(validateSpaceType(req.spaceType()));
        s.setCapacity(req.capacity());
        s.setDeptNodeId(null);
        s.setStatus("AVAILABLE");
        s.setOpenScope(normalizeOpenScope(req.openScope()));
        spaceMapper.insert(s);
        return spaceView(spaceMapper.selectById(s.getId()));
    }

    @Transactional
    public Map<String, Object> updateSpace(String id, SpaceUpdateRequest req) {
        Space s = requireSpace(id);
        if (req.spaceName() != null) {
            s.setSpaceName(req.spaceName());
        }
        if (req.spaceType() != null) {
            s.setSpaceType(validateSpaceType(req.spaceType()));
        }
        if (req.capacity() != null) {
            s.setCapacity(req.capacity());
        }
        if (req.status() != null) {
            s.setStatus(validateSpaceStatus(req.status()));
        }
        if (req.openScope() != null) {
            s.setOpenScope(normalizeOpenScope(req.openScope()));
        }
        spaceMapper.updateById(s);
        return spaceView(spaceMapper.selectById(id));
    }

    @Transactional
    public void deleteSpace(String id) {
        requireSpace(id);
        Long ticketRef = repairTicketMapper.selectCount(
                new QueryWrapper<RepairTicket>().eq("\"spaceId\"", id));
        if (ticketRef != null && ticketRef > 0) {
            throw BizException.conflict("该场地已被报修工单引用，无法删除");
        }
        Long occRef = spaceOccupancyMapper.selectCount(
                new QueryWrapper<SpaceOccupancy>().eq("\"spaceId\"", id));
        if (occRef != null && occRef > 0) {
            throw BizException.conflict("该场地已被占用记录引用，无法删除");
        }
        spaceMapper.deleteById(id);
    }

    // ==================== 报修审批流程（FT-REPAIR） ====================

    /** 读取 FT-REPAIR 模板节点链（报修提交页 M0401 使用；无模板时返回空 nodes）。 */
    public Map<String, Object> flow() {
        FlowTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<FlowTemplate>().eq(FlowTemplate::getCode, FLOW_CODE));
        return flowView(tpl);
    }

    /** 按数组顺序重建 FT-REPAIR 模板节点（seq 从 1 递增）。 */
    @Transactional
    public Map<String, Object> updateFlow(RepairFlowUpdateRequest req) {
        FlowTemplate tpl = templateMapper.selectOne(
                new LambdaQueryWrapper<FlowTemplate>().eq(FlowTemplate::getCode, FLOW_CODE));
        if (tpl == null) {
            tpl = new FlowTemplate();
            tpl.setId(Ids.next("flowtpl"));
            tpl.setCode(FLOW_CODE);
            tpl.setName("报修处理");
            tpl.setBusinessType(FLOW_BIZ_TYPE);
            tpl.setEnabled(true);
            tpl.setVersion(1);
            tpl.setRejectPolicy("RETURN");
            templateMapper.insert(tpl);
        }

        List<RepairFlowNodeRequest> nodes = validateNodes(req.nodes());
        nodeMapper.delete(new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getTemplateId, tpl.getId()));
        for (int i = 0; i < nodes.size(); i++) {
            RepairFlowNodeRequest d = nodes.get(i);
            FlowNode node = new FlowNode();
            node.setId(Ids.next("fnode"));
            node.setTemplateId(tpl.getId());
            node.setSeq(i + 1);
            node.setName(d.name());
            node.setApproveRole(d.approveRole());
            node.setTimeoutHours(d.timeoutHours());
            node.setNodeType("LINEAR");
            nodeMapper.insert(node);
        }
        tpl.setVersion(tpl.getVersion() + 1);
        templateMapper.updateById(tpl);
        return flowView(templateMapper.selectById(tpl.getId()));
    }

    // ==================== 内部 ====================

    private List<RepairFlowNodeRequest> validateNodes(List<RepairFlowNodeRequest> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw BizException.conflict("报修审批流程至少需要一个节点");
        }
        for (RepairFlowNodeRequest n : nodes) {
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

    private String validateSpaceType(String t) {
        if (t == null || t.isBlank()) {
            throw BizException.conflict("场地类型不能为空");
        }
        if (!SPACE_TYPES.contains(t)) {
            throw BizException.conflict("场地类型仅支持 CLASSROOM / LAB / MEETING_ROOM / SPORT_VENUE");
        }
        return t;
    }

    private String validateSpaceStatus(String s) {
        if (s == null || s.isBlank()) {
            throw BizException.conflict("场地状态不能为空");
        }
        if (!SPACE_STATUSES.contains(s)) {
            throw BizException.conflict("场地状态仅支持 AVAILABLE / DISABLED / REPAIRING");
        }
        return s;
    }

    private String normalizeOpenScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "ALL";
        }
        if (!OPEN_SCOPES.contains(scope)) {
            throw BizException.conflict("开放范围仅支持 SELF / GRADE / ALL");
        }
        return scope;
    }

    private SysDict requireFaultType(String id) {
        SysDict d = dictMapper.selectById(id);
        if (d == null || !DICT_FAULT_TYPE.equals(d.getDictCode())) {
            throw BizException.notFound();
        }
        return d;
    }

    private Space requireSpace(String id) {
        Space s = spaceMapper.selectById(id);
        if (s == null) {
            throw BizException.notFound();
        }
        return s;
    }

    private Map<String, Object> faultTypeView(SysDict d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("itemCode", d.getItemCode());
        m.put("itemName", d.getItemName());
        m.put("sort", d.getSort());
        m.put("enabled", d.getEnabled());
        return m;
    }

    private Map<String, Object> spaceView(Space s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("spaceName", s.getSpaceName());
        m.put("spaceType", s.getSpaceType());
        m.put("capacity", s.getCapacity());
        m.put("status", s.getStatus());
        m.put("openScope", s.getOpenScope());
        return m;
    }

    private Map<String, Object> flowView(FlowTemplate tpl) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (tpl == null) {
            out.put("id", null);
            out.put("name", null);
            out.put("nodes", List.of());
            return out;
        }
        out.put("id", tpl.getId());
        out.put("name", tpl.getName());
        List<Map<String, Object>> nodes = nodeMapper.selectList(
                        new LambdaQueryWrapper<FlowNode>()
                                .eq(FlowNode::getTemplateId, tpl.getId())
                                .orderByAsc(FlowNode::getSeq))
                .stream().map(this::nodeView).toList();
        out.put("nodes", nodes);
        return out;
    }

    private Map<String, Object> nodeView(FlowNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", n.getSeq());
        m.put("name", n.getName());
        m.put("approveRole", n.getApproveRole());
        m.put("timeoutHours", n.getTimeoutHours());
        return m;
    }
}
