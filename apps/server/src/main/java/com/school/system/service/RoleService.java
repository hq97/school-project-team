package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.scope.OwnershipType;
import com.school.common.scope.ScopeLevel;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.dto.RoleCreateRequest;
import com.school.system.dto.RoleMenuAssignRequest;
import com.school.system.dto.RoleScopeAssignRequest;
import com.school.system.dto.RoleUpdateRequest;
import com.school.system.dto.ScopeItem;
import com.school.system.entity.RoleButton;
import com.school.system.entity.RoleMenu;
import com.school.system.entity.RoleObjectScope;
import com.school.system.entity.SysRole;
import com.school.system.mapper.RoleButtonMapper;
import com.school.system.mapper.RoleMenuMapper;
import com.school.system.mapper.RoleObjectScopeMapper;
import com.school.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 系统角色管理（M0502）+ 角色授权（菜单/按钮/数据范围）。
 */
@Service
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleButtonMapper roleButtonMapper;
    private final RoleObjectScopeMapper roleObjectScopeMapper;

    public RoleService(SysRoleMapper roleMapper, RoleMenuMapper roleMenuMapper,
                       RoleButtonMapper roleButtonMapper, RoleObjectScopeMapper roleObjectScopeMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleButtonMapper = roleButtonMapper;
        this.roleObjectScopeMapper = roleObjectScopeMapper;
    }

    public List<SysRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getCreatedAt));
    }

    @Transactional
    public SysRole create(RoleCreateRequest req) {
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, req.code()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("角色编码已存在");
        }
        validateScopeForMenus(req.dataScope(), req.scopeComplete(), req.menuIds());

        SysRole role = new SysRole();
        role.setId(Ids.next("role"));
        role.setCode(req.code());
        role.setName(req.name());
        role.setDataScope(req.dataScope());
        role.setScopeComplete(Boolean.TRUE.equals(req.scopeComplete()));
        role.setBuiltin(false);
        roleMapper.insert(role);

        assignMenusInternal(role.getId(), req.menuIds(), req.buttonIds());
        assignScopesInternal(role.getId(), req.scopes());
        return roleMapper.selectById(role.getId());
    }

    public SysRole update(String id, RoleUpdateRequest req) {
        SysRole role = require(id);
        if (req.name() != null) {
            role.setName(req.name());
        }
        if (req.dataScope() != null) {
            role.setDataScope(req.dataScope());
        }
        if (req.scopeComplete() != null) {
            role.setScopeComplete(req.scopeComplete());
        }
        roleMapper.updateById(role);
        return roleMapper.selectById(id);
    }

    public void delete(String id) {
        SysRole role = require(id);
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw BizException.conflict("预置角色（builtin）不允许删除");
        }
        roleMapper.deleteById(id);
    }

    @Transactional
    public void assignMenus(String id, RoleMenuAssignRequest req) {
        require(id);
        assignMenusInternal(id, req.menuIds(), req.buttonIds());
    }

    @Transactional
    public void assignScopes(String id, RoleScopeAssignRequest req) {
        require(id);
        assignScopesInternal(id, req.scopes());
    }

    /** 回显某角色已分配的菜单与按钮（「分配菜单」对话框打开时勾选回显）。 */
    public Map<String, List<String>> assignedMenus(String roleId) {
        require(roleId);
        List<String> menuIds = roleMenuMapper.selectList(
                        new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId))
                .stream().map(RoleMenu::getMenuId).toList();
        List<String> buttonIds = roleButtonMapper.selectList(
                        new LambdaQueryWrapper<RoleButton>().eq(RoleButton::getRoleId, roleId))
                .stream().map(RoleButton::getButtonId).toList();
        return Map.of("menuIds", menuIds, "buttonIds", buttonIds);
    }

    private void assignMenusInternal(String roleId, List<String> menuIds, List<String> buttonIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (String menuId : menuIds) {
                roleMenuMapper.insert(new RoleMenu(roleId, menuId));
            }
        }
        // 按钮授权仅在显式传入时重建；null 表示「仅分配菜单，不改动既有按钮」。
        // 修复：前端「分配菜单」只提交 menuIds（buttonIds 缺省），旧实现误把按钮一并清空，
        // 导致被分配角色（含 R-ADMIN）按钮权限丢失、后续操作 40302。
        if (buttonIds != null) {
            roleButtonMapper.delete(new LambdaQueryWrapper<RoleButton>().eq(RoleButton::getRoleId, roleId));
            for (String buttonId : buttonIds) {
                roleButtonMapper.insert(new RoleButton(roleId, buttonId));
            }
        }
    }

    private void assignScopesInternal(String roleId, List<ScopeItem> scopes) {
        roleObjectScopeMapper.delete(new LambdaQueryWrapper<RoleObjectScope>().eq(RoleObjectScope::getRoleId, roleId));
        if (scopes != null) {
            for (ScopeItem s : scopes) {
                RoleObjectScope ros = new RoleObjectScope();
                ros.setId(Ids.next("ros"));
                ros.setRoleId(roleId);
                ros.setObjectKey(s.objectKey());
                ros.setOwnership(parseOwnership(s.ownership()));
                ros.setDataScope(s.dataScope());
                roleObjectScopeMapper.insert(ros);
            }
        }
    }

    /** AC-RBAC-1/2：授予任何菜单前必须配置完整数据范围（dataScope 非空且 scopeComplete=true）。 */
    private void validateScopeForMenus(ScopeLevel dataScope, Boolean scopeComplete, List<String> menuIds) {
        boolean hasMenus = menuIds != null && !menuIds.isEmpty();
        boolean scopeOk = dataScope != null && Boolean.TRUE.equals(scopeComplete);
        if (hasMenus && !scopeOk) {
            throw BizException.conflict("数据范围未配置完整（dataScope/scopeComplete）的角色不允许被授予菜单");
        }
    }

    private static OwnershipType parseOwnership(String ownership) {
        try {
            return OwnershipType.valueOf(ownership.toUpperCase());
        } catch (Exception e) {
            throw BizException.conflict("非法的归属类型：" + ownership);
        }
    }

    private SysRole require(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound();
        }
        return role;
    }
}
