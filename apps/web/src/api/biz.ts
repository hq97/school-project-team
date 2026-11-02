import { http } from "@/utils/http"; // vue-pure-admin 自带 axios 封装
import type {
  AssetItem,
  BizError,
  BizErrorCode,
  FaultTypeItem,
  MaterialRequisition,
  OccupancyState,
  RepairFlow,
  RepairStats,
  RepairTicket,
  Repairman,
  SpaceConfig,
  SpaceItem,
  StatDim,
  TimeSlot,
} from "./types";

/** 统一业务接口（物资 / 报修 / 场地预约 / 请假 / 调课） */

/** 后端统一响应体 */
type ApiResp<T> = { success: boolean; data: T };

/** 解包：http.request 返回 response.data（envelope），本层统一取 .data 返回 payload */
const unwrap = <T>(p: Promise<ApiResp<T>>): Promise<T> => p.then(r => r.data);

/**
 * 字典项只读消费（DICT-FAULT-TYPE / DICT-LEAVE-TYPE 等）
 * 注意：字典（M0505）是平台级配置，维护权仅 R-ADMIN；
 * 这里只是把字典项当作下拉选项来读，符合 AC-DD5-2——字典不得作为大屏指标或下钻明细。
 */
export function listDictItems(dictCode: string) {
  return unwrap(
    http.request<ApiResp<{ label: string; value: string }[]>>("get", "/api/dicts/items", {
      params: { dictCode },
    })
  );
}

/** 可领用资产列表（M0301，全校共享 asset，服务端过滤 IN_STOCK + availableQty>0） */
export function listAssets(_assetType?: AssetItem["assetType"]) {
  return unwrap(http.request<ApiResp<AssetItem[]>>("get", "/api/material/assets"));
}

/** 可报修/预约场地列表（M0401，space 组织型归属，服务端按开放范围过滤） */
export function listSpaces(_spaceType?: SpaceItem["spaceType"]) {
  return unwrap(http.request<ApiResp<SpaceItem[]>>("get", "/api/repair/spaces"));
}

/** 场地空闲时段查询：服务端已排除排课占用 + 按开放范围过滤，返回可预约时段 */
export function listFreeSlots(spaceId: string, week: number) {
  return unwrap(
    http.request<ApiResp<{ slot: TimeSlot; slotText: string }[]>>(
      "get",
      `/api/spaces/${spaceId}/free-slots`,
      { params: { week } }
    )
  );
}

/**
 * 场地占用矩阵：key = `${dayOfWeek}-${period}`，value = FREE / SCHEDULE / RESERVED
 * 服务端已按请求人数据范围过滤，且**不下发占用人身份**（越权红线）。
 */
export function listOccupancies(spaceId: string, week: number) {
  return unwrap(
    http.request<ApiResp<Record<string, OccupancyState>>>(
      "get",
      `/api/spaces/${spaceId}/occupancies`,
      { params: { week } }
    )
  );
}

/** 物资领用申请（BTN-MAT-APPLY，M0301）：POST /api/material/requisitions */
export function applyMaterial(payload: { assetId: string; quantity: number }) {
  return unwrap(
    http.request<ApiResp<MaterialRequisition>>("post", "/api/material/requisitions", {
      data: payload,
    })
  );
}

/** 报修提交（BTN-REP-SUBMIT，M0401）：POST /api/repair/tickets */
export function submitRepair(payload: {
  spaceId: string;
  faultType: string;
  /** 故障描述（可选） */
  description?: string;
}) {
  return unwrap(
    http.request<ApiResp<RepairTicket>>("post", "/api/repair/tickets", {
      data: payload,
    })
  );
}

/** 我的报修（M0402，forceSelf） */
export function listMyRepairTickets() {
  return unwrap(http.request<ApiResp<RepairTicket[]>>("get", "/api/repair/tickets/my"));
}

/** 报修工单列表（M0403，后勤处理视角，可 status 过滤） */
export function listRepairTickets(status?: string) {
  return unwrap(
    http.request<ApiResp<RepairTicket[]>>("get", "/api/repair/tickets", {
      params: { status },
    })
  );
}

/** 报修工单详情（M0402/M0403，含 flow 审批进度） */
export function repairDetail(id: string) {
  return unwrap(http.request<ApiResp<RepairTicket>>("get", `/api/repair/tickets/${id}`));
}

/** 维修人员下拉（R-REPAIRMAN 角色，分派维修人用） */
export function listRepairmen() {
  return unwrap(http.request<ApiResp<Repairman[]>>("get", "/api/repair/repairmen"));
}

/** 分派维修人（BTN-REP-ASSIGN，M0403）：PENDING → ASSIGNED，body { repairmanId } */
export function assignRepair(id: string, repairmanId: string) {
  return unwrap(
    http.request<ApiResp<RepairTicket>>("put", `/api/repair/tickets/${id}/assign`, {
      data: { repairmanId },
    })
  );
}

/** 代完成工单（BTN-REP-COMPLETE，M0403）：跳过审批直接完成，仅 R-ADMIN/R-LOGISTICS */
export function completeRepair(id: string) {
  return unwrap(
    http.request<ApiResp<RepairTicket>>("post", `/api/repair/tickets/${id}/complete`)
  );
}

/** 报修响应统计（M0404） */
export function repairStats() {
  return unwrap(http.request<ApiResp<RepairStats>>("get", "/api/repair/stats"));
}

/* ===================== 报修配置（M0405，按钮 BTN-REP-CONFIG） ===================== */

/** 报修审批流（M0401 左半流程展示用；流程配置收归流程模板管理 M0507） */
export function listRepairFlow() {
  return unwrap(http.request<ApiResp<RepairFlow>>("get", "/api/repair/flow"));
}

/** 故障类型列表（DICT-FAULT-TYPE 统一管理） */
export function listFaultTypes() {
  return unwrap(
    http.request<ApiResp<FaultTypeItem[]>>("get", "/api/repair/config/fault-types")
  );
}

/** 新增故障类型 */
export function createFaultType(payload: { itemCode: string; itemName: string; sort: number }) {
  return unwrap(
    http.request<ApiResp<FaultTypeItem>>("post", "/api/repair/config/fault-types", {
      data: payload,
    })
  );
}

/** 编辑故障类型（itemName/sort/enabled） */
export function updateFaultType(
  id: string,
  payload: { itemName: string; sort: number; enabled: boolean }
) {
  return unwrap(
    http.request<ApiResp<FaultTypeItem>>("put", `/api/repair/config/fault-types/${id}`, {
      data: payload,
    })
  );
}

/** 删除故障类型 */
export function deleteFaultType(id: string) {
  return unwrap(
    http.request<ApiResp<unknown>>("delete", `/api/repair/config/fault-types/${id}`)
  );
}

/** 报修位置配置列表 */
export function listConfigSpaces() {
  return unwrap(http.request<ApiResp<SpaceConfig[]>>("get", "/api/repair/config/spaces"));
}

/** 新增报修位置 */
export function createConfigSpace(payload: {
  spaceName: string;
  spaceType: SpaceConfig["spaceType"];
  capacity: number;
  openScope?: string;
}) {
  return unwrap(
    http.request<ApiResp<SpaceConfig>>("post", "/api/repair/config/spaces", {
      data: payload,
    })
  );
}

/** 编辑报修位置（spaceName/spaceType/capacity/status/openScope） */
export function updateConfigSpace(
  id: string,
  payload: {
    spaceName: string;
    spaceType: SpaceConfig["spaceType"];
    capacity: number;
    status: SpaceConfig["status"];
    openScope?: string;
  }
) {
  return unwrap(
    http.request<ApiResp<SpaceConfig>>("put", `/api/repair/config/spaces/${id}`, {
      data: payload,
    })
  );
}

/** 删除报修位置 */
export function deleteConfigSpace(id: string) {
  return unwrap(
    http.request<ApiResp<unknown>>("delete", `/api/repair/config/spaces/${id}`)
  );
}

/** 我的物资申请（M0302，forceSelf） */
export function listMyRequisitions() {
  return unwrap(
    http.request<ApiResp<MaterialRequisition[]>>("get", "/api/material/requisitions/my")
  );
}

/** 请假申请（BTN-LEAVE-APPLY，M0901） */
export function applyLeave(payload: {
  leaveType: string;
  days: number;
  reason: string;
}) {
  return unwrap(
    http.request<ApiResp<{ applyNo: string }>>("post", "/api/leave/apply", {
      data: payload,
    })
  );
}

/** 调课 / 代课申请（BTN-SCH-CHANGE-APPLY，M0801） */
export function applyScheduleChange(payload: {
  reason: string;
  periods: number;
  remark?: string;
}) {
  return unwrap(
    http.request<ApiResp<{ applyNo: string }>>("post", "/api/schedule-change/apply", {
      data: payload,
    })
  );
}

/** 场地预约（会议室 / 实验室 / 体育场地）：可能触发 40901 / 40902 */
export function bookSpace(payload: {
  spaceId: string;
  slot: TimeSlot;
  subject: string;
  attendeeCount: number;
}) {
  return unwrap(
    http.request<ApiResp<{ bookingId: string }>>(
      "post",
      `/api/spaces/${payload.spaceId}/occupancies`,
      { data: payload }
    )
  );
}

/**
 * 错误码 → 用户侧文案映射
 * ---------------------------------------------------------------------------
 * 设计约束：禁止把后端原始 message 直接弹给用户。
 * 40901/40902/库存不足/名额已满 必须走 ConflictAlert 组件展示「发生了什么 +
 * 为什么 + 怎么办」，其余错误走全局 toast（简短、不甩锅用户）。
 */
export const ERROR_TEXT: Record<string, string> = {
  "40301": "你没有该功能的使用权限，请联系管理员开通。",
  "40302": "当前账号不能执行此操作。",
  "40303": "数据不存在或你无权访问。",
  "40304": "数据不存在或你无权访问。",
  "40901": "该时段已被课表占用。",
  "40902": "该时段刚被他人预约。",
  "40903": "库存不足。",
  "40904": "名额已满。",
};

/** 需要走 ConflictAlert「三段式反馈」的错误码（409 段资源冲突类） */
const CONFLICT_CODES: BizErrorCode[] = [40901, 40902, 40903, 40904];

export function isConflict(err: unknown): err is { data: BizError } {
  const code = (err as { data?: BizError })?.data?.code;
  return CONFLICT_CODES.includes(code as BizErrorCode);
}

/** 趋势/报表统一维度参数（默认学期，禁止默认自然月） */
export const DEFAULT_DIM: StatDim = "TERM";
