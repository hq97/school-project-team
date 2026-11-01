import { http } from "@/utils/http";

/** 后端登录/用户信息返回的菜单树节点（对齐 vue-pure-admin 动态路由 + Java 后端契约） */
export type MenuNode = {
  path: string;
  name?: string;
  /** 组件路径：目录为字面量 "Layout"，叶子为相对 src/views/ 的路径（如 system/users/index） */
  component?: string;
  meta?: {
    title?: string;
    icon?: string;
    rank?: number;
    backstage?: boolean;
    roles?: Array<string>;
    auths?: Array<string>;
    keepAlive?: boolean;
    frameSrc?: string;
    [key: string]: any;
  };
  children?: Array<MenuNode>;
};

/** 后端登录返回（Java 后端契约：token/username/realName/roles/buttons/menus） */
export type LoginResult = {
  success: boolean;
  data: {
    token: string;
    username: string;
    realName: string;
    roles: Array<string>;
    /** 按钮权限码（去重） */
    buttons: Array<string>;
    /** 当前用户的菜单树 */
    menus: Array<MenuNode>;
  };
};

export type RefreshTokenResult = {
  success: boolean;
  data: {
    /** `token` */
    accessToken: string;
    /** 用于调用刷新`accessToken`的接口时所需的`token` */
    refreshToken: string;
    /** `accessToken`的过期时间（格式'xxxx/xx/xx xx:xx:xx'） */
    expires: Date;
  };
};

/** 登录 */
export const getLogin = (data?: object) => {
  return http.request<LoginResult>("post", "/api/auth/login", { data });
};

/** 当前登录用户信息（含菜单树，供动态路由刷新复用） */
export const getMe = () => {
  return http.request<LoginResult>("get", "/api/auth/me");
};

/** 刷新`token`（后端未实现 refresh，占位） */
export const refreshTokenApi = (data?: object) => {
  return http.request<RefreshTokenResult>("post", "/refresh-token", { data });
};
