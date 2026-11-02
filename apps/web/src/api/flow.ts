import { http } from "@/utils/http";

/**
 * 可配置审批流引擎（docs/07 REQ-D3）接口层。
 * ---------------------------------------------------------------------------
 * 后端统一响应体 { success: true, data: <payload> }（ApiResponse.ok），
 * 本层函数统一解包直接返回 data。错误由 axios 拦截器 reject，页面 catch 后 toast。
 *
 * 两个入口：
 *  - 流程模板管理（M0507）：/api/system/flow-templates —— 仅 R-ADMIN，按钮 BTN-FLOW-TPL-EDIT
 *  - 审批中心（M0102 待办审批）：/api/flow —— 我的待办/已办/审批/发起
 */

/** 后端统一响应体 */
type ApiResp<T> = { success: boolean; data: T };

/** 驳回策略 */
export type RejectPolicy = "RETURN" | "TERMINATE";
/** 实例状态 */
export type FlowStatus =
  | "PENDING"
  | "COMPLETED"
  | "REJECTED"
  | "TERMINATED";

// ---- 实体 ----

/** 流程节点 */
export interface FlowNode {
  id: string;
  templateId: string;
  /** 流转顺序（从 1 开始） */
  seq: number;
  name: string;
  /** 审批角色码 */
  approveRole: string;
  /** 超时小时数（可空） */
  timeoutHours?: number | null;
  nodeType: string;
}

/** 流程模板 */
export interface FlowTemplate {
  id: string;
  code: string;
  name: string;
  /** 绑定业务类型（唯一） */
  businessType: string;
  rejectPolicy: RejectPolicy;
  enabled: boolean;
  version: number;
  nodes: FlowNode[];
}

/** 待办条目 */
export interface FlowTodo {
  taskId: string;
  instanceId: string;
  businessType: string;
  businessId: string;
  templateName: string;
  seq: number;
  nodeName: string;
  role: string;
  creatorId: string;
  createdAt: string;
}

/** 已办条目 */
export interface FlowDone {
  taskId: string;
  instanceId: string;
  businessType: string;
  businessId: string;
  templateName: string;
  seq: number;
  nodeName: string;
  role: string;
  conclusion: "APPROVED" | "REJECTED";
  comment?: string;
  actedAt: string;
}

/** 实例详情（进度视图） */
export interface FlowInstance {
  id: string;
  businessType: string;
  businessId: string;
  templateId: string;
  templateName: string;
  currentSeq: number;
  status: FlowStatus;
  creatorId: string;
  createdAt: string;
  nodes: { seq: number; name: string; approveRole: string }[];
  currentNodeName?: string;
}

const unwrap = <T>(p: Promise<ApiResp<T>>): Promise<T> => p.then(r => r.data);

// ===================== 流程模板管理（M0507） =====================
export const listFlowTemplates = () =>
  unwrap(http.request<ApiResp<FlowTemplate[]>>("get", "/api/system/flow-templates"));

export const getFlowTemplate = (id: string) =>
  unwrap(http.request<ApiResp<FlowTemplate>>("get", `/api/system/flow-templates/${id}`));

export const createFlowTemplate = (data: {
  code: string;
  name: string;
  businessType: string;
  rejectPolicy?: RejectPolicy;
  nodes: { name: string; approveRole: string; timeoutHours?: number }[];
}) => unwrap(http.request<ApiResp<unknown>>("post", "/api/system/flow-templates", { data }));

export const updateFlowTemplate = (
  id: string,
  data: {
    name?: string;
    rejectPolicy?: RejectPolicy;
    enabled?: boolean;
    nodes?: { name: string; approveRole: string; timeoutHours?: number }[];
  }
) => unwrap(http.request<ApiResp<unknown>>("put", `/api/system/flow-templates/${id}`, { data }));

export const deleteFlowTemplate = (id: string) =>
  unwrap(http.request<ApiResp<unknown>>("delete", `/api/system/flow-templates/${id}`));

// ===================== 审批中心（M0102） =====================
export const listFlowTodos = () =>
  unwrap(http.request<ApiResp<FlowTodo[]>>("get", "/api/flow/todos"));

export const listFlowDone = () =>
  unwrap(http.request<ApiResp<FlowDone[]>>("get", "/api/flow/done"));

export const approveFlowTask = (taskId: string, action: "APPROVE" | "REJECT", comment?: string) =>
  unwrap(
    http.request<ApiResp<FlowInstance>>("post", `/api/flow/tasks/${taskId}/approve`, {
      data: { action, comment }
    })
  );

export const startFlowInstance = (businessType: string, businessId: string) =>
  unwrap(
    http.request<ApiResp<FlowInstance>>("post", "/api/flow/instances/start", {
      data: { businessType, businessId }
    })
  );

export const getFlowInstance = (id: string) =>
  unwrap(http.request<ApiResp<FlowInstance>>("get", `/api/flow/instances/${id}`));
