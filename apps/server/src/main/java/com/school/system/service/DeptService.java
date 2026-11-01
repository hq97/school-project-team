package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.dto.DeptCreateRequest;
import com.school.system.dto.DeptUpdateRequest;
import com.school.system.entity.SysDept;
import com.school.system.entity.UserDept;
import com.school.system.enums.DeptType;
import com.school.system.mapper.SysDeptMapper;
import com.school.system.mapper.UserDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统部门管理（M0504）。
 */
@Service
public class DeptService {

    private final SysDeptMapper deptMapper;
    private final UserDeptMapper userDeptMapper;

    public DeptService(SysDeptMapper deptMapper, UserDeptMapper userDeptMapper) {
        this.deptMapper = deptMapper;
        this.userDeptMapper = userDeptMapper;
    }

    public List<Map<String, Object>> tree() {
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (SysDept d : all) {
            byId.put(d.getId(), toMap(d));
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysDept d : all) {
            Map<String, Object> node = byId.get(d.getId());
            if (d.getParentId() == null || !byId.containsKey(d.getParentId())) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) byId.get(d.getParentId()).get("children");
                children.add(node);
            }
        }
        roots.sort(Comparator.comparing(r -> (Integer) r.get("sort")));
        return roots;
    }

    @Transactional
    public SysDept create(DeptCreateRequest req) {
        SysDept dept = new SysDept();
        dept.setId(Ids.next("dept"));
        dept.setName(req.name());
        dept.setDeptType(req.deptType());
        dept.setParentId(req.parentId());
        dept.setSort(req.sort() == null ? 0 : req.sort());
        dept.setPath(computePath(dept.getId(), req.parentId()));
        deptMapper.insert(dept);
        return deptMapper.selectById(dept.getId());
    }

    @Transactional
    public SysDept update(String id, DeptUpdateRequest req) {
        SysDept dept = require(id);
        if (req.name() != null) {
            dept.setName(req.name());
        }
        if (req.deptType() != null) {
            dept.setDeptType(req.deptType());
        }
        if (req.sort() != null) {
            dept.setSort(req.sort());
        }
        if (req.parentId() != null && !req.parentId().equals(dept.getParentId())) {
            dept.setParentId(req.parentId());
            dept.setPath(computePath(dept.getId(), req.parentId()));
            deptMapper.updateById(dept);
            recomputeDescendants(dept.getId());
            return deptMapper.selectById(id);
        }
        deptMapper.updateById(dept);
        return deptMapper.selectById(id);
    }

    public void delete(String id) {
        require(id);
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw BizException.conflict("该部门下存在子部门，无法删除");
        }
        Long userCount = userDeptMapper.selectCount(new LambdaQueryWrapper<UserDept>().eq(UserDept::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw BizException.conflict("该部门下存在用户，无法删除");
        }
        deptMapper.deleteById(id);
    }

    private String computePath(String id, String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            return "/" + id;
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            return "/" + id;
        }
        return parent.getPath() + "/" + id;
    }

    /** 移动节点后递归重算子孙 path。 */
    private void recomputeDescendants(String parentId) {
        List<SysDept> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, parentId));
        for (SysDept child : children) {
            child.setPath(computePath(child.getId(), parentId));
            deptMapper.updateById(child);
            recomputeDescendants(child.getId());
        }
    }

    private Map<String, Object> toMap(SysDept d) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", d.getId());
        node.put("name", d.getName());
        node.put("deptType", d.getDeptType() == null ? null : d.getDeptType().name());
        node.put("parentId", d.getParentId());
        node.put("path", d.getPath());
        node.put("sort", d.getSort());
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    private SysDept require(String id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw BizException.notFound();
        }
        return dept;
    }
}
