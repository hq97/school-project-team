package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.security.BizException;
import com.school.system.dto.AuthProfile;
import com.school.system.dto.MenuNode;
import com.school.system.entity.RoleButton;
import com.school.system.entity.RoleMenu;
import com.school.system.entity.SysButton;
import com.school.system.entity.SysMenu;
import com.school.system.entity.SysRole;
import com.school.system.entity.SysUser;
import com.school.system.entity.UserRole;
import com.school.system.mapper.RoleButtonMapper;
import com.school.system.mapper.RoleMenuMapper;
import com.school.system.mapper.SysButtonMapper;
import com.school.system.mapper.SysMenuMapper;
import com.school.system.mapper.SysRoleMapper;
import com.school.system.mapper.SysUserMapper;
import com.school.system.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录/当前用户档案装配：roles / buttons（去重）/ menus（树）。
 *
 * <p>实时读库，不做授权快照缓存（ADR-05）。菜单树由 {@link MenuTreeBuilder} 纯逻辑组装。</p>
 */
@Service
public class AuthProfileService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysButtonMapper buttonMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleButtonMapper roleButtonMapper;

    public AuthProfileService(SysUserMapper userMapper, SysRoleMapper roleMapper, SysMenuMapper menuMapper,
                              SysButtonMapper buttonMapper, UserRoleMapper userRoleMapper,
                              RoleMenuMapper roleMenuMapper, RoleButtonMapper roleButtonMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.buttonMapper = buttonMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleButtonMapper = roleButtonMapper;
    }

    public AuthProfile load(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound();
        }

        // 1. 角色 ids
        List<String> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();

        // 2. 角色编码（roles，去重排序）
        List<String> roles = roleIds.isEmpty()
                ? List.of()
                : RbacDedup.distinctSorted(roleMapper.selectBatchIds(roleIds).stream()
                        .map(SysRole::getCode).toList());

        // 3. 菜单 ids + 树
        Set<String> menuIds = new LinkedHashSet<>();
        if (!roleIds.isEmpty()) {
            roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, roleIds))
                    .forEach(rm -> menuIds.add(rm.getMenuId()));
        }
        List<SysMenu> menus = menuIds.isEmpty()
                ? List.of()
                : menuMapper.selectBatchIds(menuIds);
        List<MenuNode> menuTree = MenuTreeBuilder.build(menus);

        // 4. 按钮编码（buttons，去重排序）
        Set<String> buttonIds = new LinkedHashSet<>();
        if (!roleIds.isEmpty()) {
            roleButtonMapper.selectList(new LambdaQueryWrapper<RoleButton>().in(RoleButton::getRoleId, roleIds))
                    .forEach(rb -> buttonIds.add(rb.getButtonId()));
        }
        List<String> buttons = buttonIds.isEmpty()
                ? List.of()
                : RbacDedup.distinctSorted(buttonMapper.selectBatchIds(buttonIds).stream()
                        .map(SysButton::getCode).toList());

        return new AuthProfile(user.getUsername(), user.getRealName(), roles, buttons, menuTree);
    }
}
