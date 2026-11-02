import { http } from "@/utils/http";

/**
 * 系统管理（M05）接口层。
 * ---------------------------------------------------------------------------
 * 契约：后端统一响应体 { success: true, data: <payload> }（ApiResponse.ok）。
 * 本层函数统一解包，直接返回 data，页面无需关心外层 success。
 * 错误：后端 4xx/5xx 由 axios 拦截器 reject，页面 catch 后统一 toast。
 */

/** 后端统一响应体 */
type ApiResp<T> = { success: boolean; data: T };

/** 分页结果 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

// ---- 枚举（与后端枚举 name 对齐） ----
export type AccountStatus = "ACTIVE" | "DISABLED";
export type StudentStatus =
  | "ENROLLED"
  | "GRADUATED"
  | "TRANSFERRED"
  | "LEFT"
  | "SUSPENDED";
export type DeptType = "SCHOOL" | "GRADE" | "CLASS" | "SUBJECT" | "OFFICE";
export type ScopeLevel = "SELF" | "GRADE" | "ALL";

// ---- 实体 ----
export interface SysUser {
  id: string;
  username: string;
  realName: string;
  accountStatus: AccountStatus;
  studentStatus?: StudentStatus;
  studentId?: string;
  createdAt?: string;
}

export interface SysRole {
  id: string;
  code: string;
  name: string;
  dataScope: ScopeLevel;
  scopeComplete: boolean;
  builtin: boolean;
  createdAt?: string;
}

export interface SysMenuNode {
  id: string;
  code: string;
  name: string;
  path?: string;
  parentId?: string;
  sort?: number;
  component?: string;
  icon?: string;
  listLike?: boolean;
  children?: SysMenuNode[];
}

export interface SysDeptNode {
  id: string;
  name: string;
  deptType: DeptType;
  parentId?: string;
  path?: string;
  sort?: number;
  children?: SysDeptNode[];
}

export interface SysDict {
  id: string;
  dictCode: string;
  itemCode: string;
  itemName: string;
  sort?: number;
  enabled: boolean;
  isCurrent: boolean;
}

/** 数据范围授权项（组织型归属） */
export interface ScopeItem {
  objectKey: string;
  ownership: string;
  dataScope: ScopeLevel;
}

const unwrap = <T>(p: Promise<ApiResp<T>>): Promise<T> => p.then(r => r.data);

// ===================== 用户（M0501） =====================
export const pageUsers = (params: {
  keyword?: string;
  page?: number;
  pageSize?: number;
}) => unwrap(http.request<ApiResp<PageResult<SysUser>>>("get", "/api/system/users", { params }));

export const createUser = (data: {
  username: string;
  password: string;
  realName: string;
  accountStatus?: AccountStatus;
  studentStatus?: StudentStatus;
  studentId?: string;
  roleIds?: string[];
}) => unwrap(http.request<ApiResp<unknown>>("post", "/api/system/users", { data }));

export const updateUser = (
  id: string,
  data: {
    realName?: string;
    accountStatus?: AccountStatus;
    studentStatus?: StudentStatus;
    studentId?: string;
  }
) => unwrap(http.request<ApiResp<unknown>>("put", `/api/system/users/${id}`, { data }));

export const deleteUser = (id: string) =>
  unwrap(http.request<ApiResp<unknown>>("delete", `/api/system/users/${id}`));

export const assignUserRoles = (id: string, roleIds: string[]) =>
  unwrap(
    http.request<ApiResp<unknown>>("put", `/api/system/users/${id}/roles`, {
      data: { roleIds }
    })
  );

export const resetUserPassword = (id: string, newPassword: string) =>
  unwrap(
    http.request<ApiResp<unknown>>("put", `/api/system/users/${id}/reset-password`, {
      data: { newPassword }
    })
  );

// ===================== 角色（M0502） =====================
export const listRoles = () =>
  unwrap(http.request<ApiResp<SysRole[]>>("get", "/api/system/roles"));

export const createRole = (data: {
  code: string;
  name: string;
  dataScope?: ScopeLevel;
  scopeComplete?: boolean;
  menuIds?: string[];
  buttonIds?: string[];
  scopes?: ScopeItem[];
}) => unwrap(http.request<ApiResp<unknown>>("post", "/api/system/roles", { data }));

export const updateRole = (
  id: string,
  data: { name?: string; dataScope?: ScopeLevel; scopeComplete?: boolean }
) => unwrap(http.request<ApiResp<unknown>>("put", `/api/system/roles/${id}`, { data }));

export const deleteRole = (id: string) =>
  unwrap(http.request<ApiResp<unknown>>("delete", `/api/system/roles/${id}`));

export const assignRoleMenus = (id: string, menuIds: string[], buttonIds?: string[]) =>
  unwrap(
    http.request<ApiResp<unknown>>("put", `/api/system/roles/${id}/menus`, {
      data: { menuIds, buttonIds }
    })
  );

/** 回显某角色已分配的菜单/按钮（分配菜单对话框打开时回填勾选） */
export const getRoleMenus = (id: string) =>
  unwrap(
    http.request<ApiResp<{ menuIds: string[]; buttonIds: string[] }>>(
      "get",
      `/api/system/roles/${id}/menus`
    )
  );

export const assignRoleScopes = (id: string, scopes: ScopeItem[]) =>
  unwrap(
    http.request<ApiResp<unknown>>("put", `/api/system/roles/${id}/scopes`, {
      data: { scopes }
    })
  );

// ===================== 菜单（M0503） =====================
export const listMenus = () =>
  unwrap(http.request<ApiResp<SysMenuNode[]>>("get", "/api/system/menus"));

export const createMenu = (data: {
  code: string;
  name: string;
  path?: string;
  parentId?: string;
  sort?: number;
  component?: string;
  icon?: string;
  listLike?: boolean;
}) => unwrap(http.request<ApiResp<unknown>>("post", "/api/system/menus", { data }));

export const updateMenu = (
  id: string,
  data: {
    name?: string;
    path?: string;
    parentId?: string;
    sort?: number;
    component?: string;
    icon?: string;
    listLike?: boolean;
  }
) => unwrap(http.request<ApiResp<unknown>>("put", `/api/system/menus/${id}`, { data }));

export const deleteMenu = (id: string) =>
  unwrap(http.request<ApiResp<unknown>>("delete", `/api/system/menus/${id}`));

// ===================== 部门（M0504） =====================
export const listDepts = () =>
  unwrap(http.request<ApiResp<SysDeptNode[]>>("get", "/api/system/depts"));

export const createDept = (data: {
  name: string;
  deptType: DeptType;
  parentId?: string;
  sort?: number;
}) => unwrap(http.request<ApiResp<unknown>>("post", "/api/system/depts", { data }));

export const updateDept = (
  id: string,
  data: { name?: string; deptType?: DeptType; parentId?: string; sort?: number }
) => unwrap(http.request<ApiResp<unknown>>("put", `/api/system/depts/${id}`, { data }));

export const deleteDept = (id: string) =>
  unwrap(http.request<ApiResp<unknown>>("delete", `/api/system/depts/${id}`));

// ===================== 数据字典（M0505） =====================
export const listDicts = (dictCode?: string) =>
  unwrap(
    http.request<ApiResp<SysDict[]>>("get", "/api/system/dicts", {
      params: { dictCode }
    })
  );

export const createDict = (data: {
  dictCode: string;
  itemCode: string;
  itemName: string;
  sort?: number;
  enabled?: boolean;
  isCurrent?: boolean;
}) => unwrap(http.request<ApiResp<unknown>>("post", "/api/system/dicts", { data }));

export const updateDict = (
  id: string,
  data: { itemName?: string; sort?: number; enabled?: boolean; isCurrent?: boolean }
) => unwrap(http.request<ApiResp<unknown>>("put", `/api/system/dicts/${id}`, { data }));

export const deleteDict = (id: string) =>
  unwrap(http.request<ApiResp<unknown>>("delete", `/api/system/dicts/${id}`));
