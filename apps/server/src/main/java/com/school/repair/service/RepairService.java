package com.school.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.school.asset.entity.Space;
import com.school.asset.mapper.SpaceMapper;
import com.school.common.scope.Principal;
import com.school.common.scope.ScopeQueryBuilder;
import com.school.common.scope.ScopeWrapper;
import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import com.school.common.util.Ids;
import com.school.repair.dto.RepairTicketAssignRequest;
import com.school.repair.dto.RepairTicketCreateRequest;
import com.school.repair.entity.RepairTicket;
import com.school.repair.mapper.RepairTicketMapper;
import com.school.system.entity.SysRole;
import com.school.system.entity.SysUser;
import com.school.system.entity.UserRole;
import com.school.system.mapper.SysRoleMapper;
import com.school.system.mapper.SysUserMapper;
import com.school.system.mapper.UserRoleMapper;
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
 * 报修工单（接入审批流引擎，businessType=repair）。
 *
 * <p>数据范围：ORG（ownerField=reporterId / deptField=reporterDeptId / termField=termId）。
 * 生命周期：提交即启动 flow 实例 →「分派维修人」= FT-REPAIR 节点1（R-LOGISTICS）审批
 * →「维修处理」= 节点2（R-REPAIRMAN）审批 → 终审通过由 {@link RepairApprovalHandler} 回写 COMPLETED。</p>
 */
@Service
public class RepairService {

    private static final String ROLE_REPAIRMAN = "R-REPAIRMAN";

    private final RepairTicketMapper mapper;
    private final SpaceMapper spaceMapper;
    private final FlowEngineService flowEngine;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public RepairService(RepairTicketMapper mapper, SpaceMapper spaceMapper, FlowEngineService flowEngine,
                         SysRoleMapper roleMapper, SysUserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.mapper = mapper;
        this.spaceMapper = spaceMapper;
        this.flowEngine = flowEngine;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /** 可选场地列表（PUBLIC，仅菜单控制）。 */
    public List<Map<String, Object>> spaces() {
        QueryWrapper<Space> qw = new QueryWrapper<>();
        qw.eq("\"status\"", "AVAILABLE");
        qw.orderByAsc("\"spaceType\"");
        return spaceMapper.selectList(qw).stream().map(this::spaceView).toList();
    }

    /** 维修人员下拉（R-REPAIRMAN 角色下的用户，PUBLIC，供 M0403 分派使用）。 */
    public List<Map<String, Object>> repairmen() {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, ROLE_REPAIRMAN));
        if (role == null) {
            return List.of();
        }
        List<UserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, role.getId()));
        if (links.isEmpty()) {
            return List.of();
        }
        List<String> userIds = links.stream().map(UserRole::getUserId).distinct().toList();
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds));
        return users.stream().map(this::repairmanView).toList();
    }

    /**
     * 提交报修（≤3 步）：写入 reporterId=当前用户、reporterDeptId=主部门、termId=当前学期，
     * status=PENDING，随后启动审批流实例。
     */
    @Transactional
    public Map<String, Object> submit(RepairTicketCreateRequest req) {
        Principal p = CurrentPrincipal.require();
        RepairTicket e = new RepairTicket();
        e.setId(Ids.next("repair"));
        e.setTicketNo(Ids.next("REP"));
        e.setReporterId(p.userId());
        e.setReporterDeptId(primaryDept(p));
        e.setSpaceId(req.spaceId());
        e.setFaultType(req.faultType());
        e.setDescription(req.description());
        e.setTermId(p.currentTermId());
        e.setStatus("PENDING");
        e.setCreatedAt(OffsetDateTime.now());
        mapper.insert(e);
        flowEngine.start("repair", e.getId(), p.userId());
        return view(e);
    }

    /** 我的报修（forceSelf），带审批进度。 */
    public List<Map<String, Object>> my(HttpServletRequest request) {
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        List<RepairTicket> list = mapper.selectList(qw);
        Map<String, Map<String, Object>> flows = flowEngine.instancesByBusiness(
                "repair", list.stream().map(RepairTicket::getId).toList());
        return list.stream().map(e -> {
            Map<String, Object> v = view(e);
            v.put("flow", flows.get(e.getId()));
            return v;
        }).toList();
    }

    /** 工单列表（后勤处理视角，按范围过滤，可选 status 过滤），带审批进度。 */
    public List<Map<String, Object>> list(HttpServletRequest request, String status) {
        Map<String, Object> extra = new HashMap<>();
        if (status != null && !status.isEmpty()) {
            extra.put("status", status);
        }
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByDesc("\"createdAt\"");
        List<RepairTicket> list = mapper.selectList(qw);
        Map<String, Map<String, Object>> flows = flowEngine.instancesByBusiness(
                "repair", list.stream().map(RepairTicket::getId).toList());
        return list.stream().map(e -> {
            Map<String, Object> v = view(e);
            v.put("flow", flows.get(e.getId()));
            return v;
        }).toList();
    }

    /**
     * 分派维修人（= 审批 FT-REPAIR 节点1）：
     * 校验工单 PENDING + 维修人确为 R-REPAIRMAN → 落 assignedTo/assignedAt/status=ASSIGNED
     * → 以当前登录人（R-LOGISTICS）通过节点1，流转至节点2（维修处理）。
     */
    @Transactional
    public Map<String, Object> assign(HttpServletRequest request, String id, RepairTicketAssignRequest req) {
        Principal p = CurrentPrincipal.require();
        RepairTicket e = requireTicket(request, id);
        if (!"PENDING".equals(e.getStatus())) {
            throw BizException.conflict("仅待分派工单可分派");
        }
        assertRepairman(req.repairmanId());
        e.setAssignedTo(req.repairmanId());
        e.setAssignedAt(OffsetDateTime.now());
        e.setStatus("ASSIGNED");
        mapper.updateById(e);
        flowEngine.approveByBusiness("repair", id, "APPROVE", null, p);
        return view(e);
    }

    /** 工单详情：matchRecord 判归属，不通过抛 40304 防枚举；带审批进度。 */
    public Map<String, Object> detail(HttpServletRequest request, String id) {
        RepairTicket e = requireTicket(request, id);
        Map<String, Object> v = view(e);
        v.put("flow", flowEngine.instanceByBusiness("repair", id));
        return v;
    }

    /** 报修响应统计（M0404）：平均响应时长（秒）+ 超时未处理清单，按范围过滤。 */
    public Map<String, Object> stats(HttpServletRequest request) {
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);

        // 平均响应时长：assignedAt - createdAt（秒）
        QueryWrapper<RepairTicket> avgQw = new QueryWrapper<>();
        ScopeWrapper.apply(avgQw, scope.buildWhere(null));
        avgQw.isNotNull("\"assignedAt\"");
        avgQw.select("COALESCE(AVG(EXTRACT(EPOCH FROM (\"assignedAt\" - \"createdAt\"))), 0) AS \"avgSeconds\"");
        List<Map<String, Object>> avgRows = mapper.selectMaps(avgQw);
        double avgSeconds = avgRows.isEmpty() ? 0 : ((Number) avgRows.get(0).get("avgSeconds")).doubleValue();

        // 超时未处理：PENDING/ASSIGNED/PROCESSING 且创建超 24h（P0 默认阈值，dict 缺数值列，暂硬编码）
        QueryWrapper<RepairTicket> overdueQw = new QueryWrapper<>();
        ScopeWrapper.apply(overdueQw, scope.buildWhere(null));
        overdueQw.in("\"status\"", "PENDING", "ASSIGNED", "PROCESSING");
        overdueQw.lt("\"createdAt\"", OffsetDateTime.now().minusHours(24));
        overdueQw.orderByAsc("\"createdAt\"");
        List<Map<String, Object>> overdue = mapper.selectList(overdueQw).stream().map(this::view).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("avgResponseSeconds", Math.round(avgSeconds));
        out.put("overdueCount", overdue.size());
        out.put("overdueTickets", overdue);
        return out;
    }

    private void assertRepairman(String userId) {
        if (userId == null || userId.isBlank()) {
            throw BizException.conflict("维修人不能为空");
        }
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, ROLE_REPAIRMAN));
        if (role == null) {
            throw BizException.conflict("维修人员角色不存在：" + ROLE_REPAIRMAN);
        }
        Long cnt = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, role.getId()));
        if (cnt == null || cnt == 0) {
            throw BizException.conflict("指定用户不是维修人员（" + ROLE_REPAIRMAN + "）");
        }
    }

    private RepairTicket requireTicket(HttpServletRequest request, String id) {
        RepairTicket e = mapper.selectById(id);
        if (e == null) {
            throw BizException.notFound();
        }
        ScopeQueryBuilder scope = ScopeQueryBuilder.fromRequest(request);
        if (!scope.matchRecord(record(e))) {
            throw BizException.notFound();
        }
        return e;
    }

    private Map<String, Object> view(RepairTicket e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("ticketNo", e.getTicketNo());
        m.put("reporterId", e.getReporterId());
        m.put("reporterDeptId", e.getReporterDeptId());
        m.put("spaceId", e.getSpaceId());
        m.put("faultType", e.getFaultType());
        m.put("description", e.getDescription());
        m.put("termId", e.getTermId());
        m.put("status", e.getStatus());
        m.put("assignedTo", e.getAssignedTo());
        m.put("assignedAt", e.getAssignedAt());
        m.put("completedAt", e.getCompletedAt());
        m.put("createdAt", e.getCreatedAt());
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

    private Map<String, Object> repairmanView(SysUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        return m;
    }

    private Map<String, Object> record(RepairTicket e) {
        Map<String, Object> r = new HashMap<>();
        r.put("reporterId", e.getReporterId());
        r.put("reporterDeptId", e.getReporterDeptId());
        r.put("termId", e.getTermId());
        return r;
    }

    static String primaryDept(Principal p) {
        return p.deptIds().isEmpty() ? null : p.deptIds().get(0);
    }
}
