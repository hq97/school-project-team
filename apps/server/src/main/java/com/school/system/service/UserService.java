package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.dto.UserCreateRequest;
import com.school.system.dto.UserRoleAssignRequest;
import com.school.system.dto.UserUpdateRequest;
import com.school.system.entity.SysUser;
import com.school.system.entity.UserRole;
import com.school.system.enums.AccountStatus;
import com.school.system.mapper.SysUserMapper;
import com.school.system.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户管理（M0501）。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserMapper userMapper, UserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<SysUser> page(String keyword, long page, long pageSize) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }
        qw.orderByAsc(SysUser::getCreatedAt);
        return userMapper.selectPage(new Page<>(page, pageSize), qw);
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
