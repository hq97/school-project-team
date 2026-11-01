import { http } from "@/utils/http"; // vue-pure-admin 自带 axios 封装
import type {
  AssetItem,
  BizError,
  BizErrorCode,
  OccupancyState,
  SpaceItem,
  StatDim,
  TimeSlot,
} from "./types";

/** 统一业务接口（物资 / 报修 / 场地预约 / 请假 / 调课） */

/**
 * 字典项只读消费（DICT-FAULT-TYPE / DICT-LEAVE-TYPE 等）
 * 注意：字典（M0505）是平台级配置，维护权仅 R-ADMIN；
 * 这里只是把字典项当作下拉选项来读，符合 AC-DD5-2——字典不得作为大屏指标或下钻明细。
 */
export function listDictItems(dictCode: string) {
  return http.request<{ label: string; value: string }[]>("get", "/api/dicts/items", {
    params: { dictCode },
  });
}

/** 可领用资产列表（全校共享 asset，按 assetType 域隔离，服务端过滤） */
export function listAssets(assetType: AssetItem["assetType"], keyword = "") {
  return http.request<AssetItem[]>("get", "/api/assets", {
    params: { assetType, keyword },
  });
}

/** 可预约场地列表（space 组织型归属，服务端按开放范围过滤，越权 40303） */
export function listSpaces(spaceType: SpaceItem["spaceType"]) {
  return http.request<SpaceItem[]>("get", "/api/spaces", {
    params: { spaceType },
  });
}

/** 场地空闲时段查询：服务端已排除排课占用 + 按开放范围过滤，返回可预约时段 */
export function listFreeSlots(spaceId: string, week: number) {
  return http.request<{ slot: TimeSlot; slotText: string }[]>(
    "get",
    `/api/spaces/${spaceId}/free-slots`,
    { params: { week } }
  );
}

/**
 * 场地占用矩阵：key = `${dayOfWeek}-${period}`，value = FREE / SCHEDULE / RESERVED
 * 服务端已按请求人数据范围过滤，且**不下发占用人身份**（越权红线）。
 */
export function listOccupancies(spaceId: string, week: number) {
  return http.request<Record<string, OccupancyState>>(
    "get",
    `/api/spaces/${spaceId}/occupancies`,
    { params: { week } }
  );
}

/** 物资领用申请（BTN-MAT-APPLY） */
export function applyMaterial(payload: {
  assetId: string;
  qty: number;
  usage: string;
}) {
  return http.request<{ applyNo: string }>("post", "/api/material/apply", {
    data: payload,
  });
}

/** 报修提交（BTN-REP-SUBMIT） */
export function submitRepair(payload: {
  spaceId: string;
  faultType: string;
  description: string;
  photoIds?: string[];
}) {
  return http.request<{ ticketNo: string }>("post", "/api/repair/submit", {
    data: payload,
  });
}

/** 请假申请（BTN-LEAVE-APPLY，M0901） */
export function applyLeave(payload: {
  leaveType: string;
  days: number;
  reason: string;
}) {
  return http.request<{ applyNo: string }>("post", "/api/leave/apply", {
    data: payload,
  });
}

/** 调课 / 代课申请（BTN-SCH-CHANGE-APPLY，M0801） */
export function applyScheduleChange(payload: {
  reason: string;
  periods: number;
  remark?: string;
}) {
  return http.request<{ applyNo: string }>("post", "/api/schedule-change/apply", {
    data: payload,
  });
}

/** 场地预约（会议室 / 实验室 / 体育场地）：可能触发 40901 / 40902 */
export function bookSpace(payload: {
  spaceId: string;
  slot: TimeSlot;
  subject: string;
  attendeeCount: number;
}) {
  return http.request<{ bookingId: string }>(
    "post",
    `/api/spaces/${payload.spaceId}/occupancies`,
    { data: payload }
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
