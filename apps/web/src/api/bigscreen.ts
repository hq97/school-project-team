import { http } from "@/utils/http"; // vue-pure-admin 自带 axios 封装
import type { OverviewResp, StatDim } from "./types";

/**
 * 数据大屏接口（M15）
 * ---------------------------------------------------------------------------
 * 硬约束（AC-DD5-1 / AC-DD1-2 / AC-DD5-2）：
 *  1. 全部走服务端聚合 + 数据权限过滤，前端不拉全量、不本地过滤、不本地聚合。
 *  2. dim=MONTH 仅切换展示粒度；可见数据集合与越权判定结果必须与 TERM 一致
 *     （服务端先按学期+周次聚合与鉴权，再做月粒度重排）。
 *  3. 返回结果不得包含平台级配置对象（字典 / 流程模板 / 菜单 / 角色 / 部门）。
 */

/** 大屏总览：按角色返回对应模板数据。dim 默认 TERM */
export function getOverview(params: { dim?: StatDim; termCode?: string } = {}) {
  return http.request<OverviewResp>("get", "/api/dashboard/overview", {
    // 显式默认值：不传即学期口径，前端不允许默认 MONTH
    params: { dim: "TERM", ...params },
  });
}

/**
 * 指标下钻（REQ-DD6）
 * 前端只传指标 code + 维度，明细范围由服务端按数据权限决定；
 * 越权明细服务端返回 40303 / 40304，前端统一走全局错误处理器提示。
 */
export function drillDown(params: {
  kpiCode: string;
  dim?: StatDim;
  page?: number;
  size?: number;
}) {
  return http.request<{ list: Record<string, unknown>[]; total: number }>(
    "get",
    "/api/dashboard/drill",
    { params: { dim: "TERM", ...params } }
  );
}
