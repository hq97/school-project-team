package com.school.system.service;

import com.school.common.security.BizCode;
import com.school.common.security.BizException;
import com.school.system.entity.SysMenu;
import com.school.system.mapper.RoleMenuMapper;
import com.school.system.mapper.SysMenuMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单删除保护 409 单测（有子菜单 / 已挂角色禁止删除）。
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private RoleMenuMapper roleMenuMapper;

    @InjectMocks
    private MenuService menuService;

    private static SysMenu menu() {
        SysMenu m = new SysMenu();
        m.setId("m0501");
        m.setCode("M0501");
        m.setName("用户管理");
        return m;
    }

    @Test
    void delete_hasChildren_throwsConflict409() {
        when(menuMapper.selectById("m0501")).thenReturn(menu());
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> menuService.delete("m0501"));
        assertEquals(BizCode.RESOURCE_CONFLICT, ex.bizCode());
        verify(menuMapper, never()).deleteById("m0501");
    }

    @Test
    void delete_attachedToRole_throwsConflict409() {
        when(menuMapper.selectById("m0501")).thenReturn(menu());
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(roleMenuMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> menuService.delete("m0501"));
        assertEquals(BizCode.RESOURCE_CONFLICT, ex.bizCode());
        verify(menuMapper, never()).deleteById("m0501");
    }

    @Test
    void delete_noRefs_succeeds() {
        when(menuMapper.selectById("m0501")).thenReturn(menu());
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(roleMenuMapper.selectCount(any())).thenReturn(0L);

        menuService.delete("m0501");
        verify(menuMapper).deleteById("m0501");
    }
}
