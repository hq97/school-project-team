package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.dto.UserCreateRequest;
import com.school.system.dto.UserRoleAssignRequest;
import com.school.system.dto.UserUpdateRequest;
import com.school.system.entity.SysRole;
import com.school.system.entity.SysUser;
import com.school.system.entity.UserRole;
import com.school.system.enums.AccountStatus;
import com.school.system.mapper.SysRoleMapper;
import com.school.system.mapper.SysUserMapper;
import com.school.system.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统用户管理（M0501）。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserMapper userMapper, UserRoleMapper userRoleMapper,
                       SysRoleMapper sysRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<SysUser> page(String keyword, String status, String roleId, String studentId,
                              long page, long pageSize) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }
        if (status != null && !status.isBlank()) {
            try {
                qw.eq(SysUser::getAccountStatus, AccountStatus.valueOf(status));
            } catch (IllegalArgumentException ignored) {
                // 非法状态码：忽略该条件
            }
        }
        if (studentId != null && !studentId.isBlank()) {
            qw.like(SysUser::getStudentId, studentId);
        }
        if (roleId != null && !roleId.isBlank()) {
            List<String> userIds = userRoleMapper.selectList(
                            new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, roleId))
                    .stream().map(UserRole::getUserId).toList();
            if (userIds.isEmpty()) {
                Page<SysUser> empty = new Page<>(page, pageSize);
                empty.setRecords(List.of());
                empty.setTotal(0);
                return empty;
            }
            qw.in(SysUser::getId, userIds);
        }
        qw.orderByAsc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, pageSize), qw);
        fillRoles(result.getRecords());
        return result;
    }

    /** 批量填充用户列表的角色信息（id/code/name）。 */
    private void fillRoles(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<String> userIds = users.stream().map(SysUser::getId).toList();
        List<UserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
        if (links.isEmpty()) {
            users.forEach(u -> u.setRoles(List.of()));
            return;
        }
        List<String> roleIds = links.stream().map(UserRole::getRoleId).distinct().toList();
        Map<String, SysRole> roleById = new HashMap<>();
        for (SysRole r : sysRoleMapper.selectBatchIds(roleIds)) {
            roleById.put(r.getId(), r);
        }
        Map<String, List<Map<String, Object>>> byUser = new HashMap<>();
        for (UserRole link : links) {
            SysRole role = roleById.get(link.getRoleId());
            if (role == null) {
                continue;
            }
            Map<String, Object> brief = new LinkedHashMap<>();
            brief.put("id", role.getId());
            brief.put("code", role.getCode());
            brief.put("name", role.getName());
            byUser.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(brief);
        }
        for (SysUser u : users) {
            u.setRoles(byUser.getOrDefault(u.getId(), List.of()));
        }
    }

    @Transactional
    public SysUser create(UserCreateRequest req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setId(Ids.next("user"));
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRealName(req.realName());
        user.setAccountStatus(req.accountStatus() == null ? AccountStatus.ACTIVE : req.accountStatus());
        user.setStudentStatus(req.studentStatus());
        user.setStudentId(req.studentId());
        userMapper.insert(user);
        assignRolesInternal(user.getId(), req.roleIds());
        return userMapper.selectById(user.getId());
    }

    public SysUser update(String id, UserUpdateRequest req) {
        SysUser user = require(id);
        if (req.realName() != null) {
            user.setRealName(req.realName());
        }
        if (req.accountStatus() != null) {
            user.setAccountStatus(req.accountStatus());
        }
        if (req.studentStatus() != null) {
            user.setStudentStatus(req.studentStatus());
        }
        if (req.studentId() != null) {
            user.setStudentId(req.studentId());
        }
        userMapper.updateById(user);
        return userMapper.selectById(id);
    }

    public void delete(String id, String currentUserId) {
        require(id);
        if (id.equals(currentUserId)) {
            throw BizException.conflict("不能删除当前登录账号");
        }
        userMapper.deleteById(id);
    }

    @Transactional
    public void assignRoles(String id, UserRoleAssignRequest req) {
        require(id);
        assignRolesInternal(id, req.roleIds());
    }

    /** 回显某用户已分配的角色 id 列表（「分配角色」对话框打开时回填勾选）。 */
    public List<String> getUserRoles(String userId) {
        require(userId);
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
    }

    public void resetPassword(String id, String newPassword) {
        SysUser user = require(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private void assignRolesInternal(String userId, List<String> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds != null) {
            for (String roleId : roleIds) {
                userRoleMapper.insert(new UserRole(userId, roleId));
            }
        }
    }

    private SysUser require(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound();
        }
        return user;
    }
}
