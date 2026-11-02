package com.school.repair.service;

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
import com.school.repair.dto.RepairTicketStatusRequest;
import com.school.repair.entity.RepairTicket;
import com.school.repair.mapper.RepairTicketMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报修工单（自有状态机，不走审批流）。
 *
 * <p>数据范围：ORG（ownerField=reporterId / deptField=reporterDeptId / termField=termId）。
 * 状态机：PENDING → ASSIGNED → PROCESSING → COMPLETED，另有 CANCELLED。</p>
 */
@Service
public class RepairService {

    private final RepairTicketMapper mapper;
    private final SpaceMapper spaceMapper;

    public RepairService(RepairTicketMapper mapper, SpaceMapper spaceMapper) {
        this.mapper = mapper;
        this.spaceMapper = spaceMapper;
    }

    /** 可选场地列表（PUBLIC，仅菜单控制）。 */
    public List<Map<String, Object>> spaces() {
        QueryWrapper<Space> qw = new QueryWrapper<>();
        qw.eq("\"status\"", "AVAILABLE");
        qw.orderByAsc("\"spaceType\"");
        return spaceMapper.selectList(qw).stream().map(this::spaceView).toList();
    }

    /** 提交报修（≤3 步）：写入 reporterId=当前用户、reporterDeptId=主部门、termId=当前学期，status=PENDING。 */
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
        e.setTermId(p.currentTermId());
        e.setStatus("PENDING");
        e.setCreatedAt(OffsetDateTime.now());
        mapper.insert(e);
        return view(e);
    }

    /** 我的报修（forceSelf）。 */
    public List<Map<String, Object>> my(HttpServletRequest request) {
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(null));
        qw.orderByDesc("\"createdAt\"");
        return mapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 工单列表（后勤处理视角，按范围过滤，可选 status 过滤）。 */
    public List<Map<String, Object>> list(HttpServletRequest request, String status) {
        Map<String, Object> extra = new HashMap<>();
        if (status != null && !status.isEmpty()) {
            extra.put("status", status);
        }
        QueryWrapper<RepairTicket> qw = new QueryWrapper<>();
        ScopeWrapper.apply(qw, ScopeQueryBuilder.fromRequest(request).buildWhere(extra));
        qw.orderByDesc("\"createdAt\"");
        return mapper.selectList(qw).stream().map(this::view).toList();
    }

    /** 分派维修人：PENDING → ASSIGNED。 */
    @Transactional
    public Map<String, Object> assign(HttpServletRequest request, String id, RepairTicketAssignRequest req) {
        RepairTicket e = requireTicket(request, id);
        if (!"PENDING".equals(e.getStatus())) {
            throw BizException.conflict("仅待分派工单可分派");
        }
        e.setAssignedTo(req.assignedTo());
        e.setStatus("ASSIGNED");
        mapper.updateById(e);
        return view(e);
    }

    /** 更新工单状态：ASSIGNED → PROCESSING → COMPLETED（或 CANCELLED）。 */
    @Transactional
    public Map<String, Object> updateStatus(HttpServletRequest request, String id, RepairTicketStatusRequest req) {
        RepairTicket e = requireTicket(request, id);
        String from = e.getStatus();
        String to = req.status();
        if (!validTransition(from, to)) {
            throw BizException.conflict("工单状态不允许从 " + from + " 流转到 " + to);
        }
        e.setStatus(to);
        mapper.updateById(e);
        return view(e);
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

    private boolean validTransition(String from, String to) {
        if ("CANCELLED".equals(to)) {
            return !"COMPLETED".equals(from) && !"CANCELLED".equals(from);
        }
        return switch (from) {
            case "ASSIGNED" -> "PROCESSING".equals(to) || "COMPLETED".equals(to);
            case "PROCESSING" -> "COMPLETED".equals(to);
            default -> false;
        };
    }

    private Map<String, Object> view(RepairTicket e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("ticketNo", e.getTicketNo());
        m.put("reporterId", e.getReporterId());
        m.put("reporterDeptId", e.getReporterDeptId());
        m.put("spaceId", e.getSpaceId());
        m.put("faultType", e.getFaultType());
        m.put("termId", e.getTermId());
        m.put("status", e.getStatus());
        m.put("assignedTo", e.getAssignedTo());
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
