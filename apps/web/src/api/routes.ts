import { http } from "@/utils/http";
import { getMe } from "@/api/user";
import type { MenuNode } from "@/api/user";

type Result = {
  success: boolean;
  data: Array<MenuNode>;
};

/**
 * 获取当前用户动态路由（菜单树）。
 * 后端菜单树来自 `GET /api/auth/me` 返回的 `data.menus` 字段（Java 后端契约）。
 */
export const getAsyncRoutes = async (): Promise<Result> => {
  const res: any = await http.request("get", "/api/auth/me");
  return {
    success: true,
    data: res?.data?.menus ?? []
  };
};

/** 导出便于其它模块复用（避免重复 import） */
export { getMe };
