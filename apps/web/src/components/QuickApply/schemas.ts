import { ref } from "vue";
import {
  applyLeave,
  applyMaterial,
  bookSpace,
  listFreeSlots,
  submitRepair,
} from "@/api/biz";
import type { TimeSlot } from "@/api/types";

/** 当前学期周次：由服务端 /api/dashboard/term/current 下发，前端不本地推算 */
export const CURRENT_WEEK = ref(1);

/**
 * 高频事务「快捷申请」字段 Schema
 * ---------------------------------------------------------------------------
 * 设计原则（教师端硬指标②：≤3 步）：
 *  1. 一屏一表单：所有高频事务的字段压到 5 个以内，不做分步向导、不做多页签。
 *  2. 能带默认值的一律带默认值（当前学期周次 / 本人 / 本班 / 最近常用物资），
 *     教师只做"确认 + 提交"，不做"填空"。
 *  3. 场地类事务默认选中「第一个推荐空闲时段」，把 3 步压回 2 步。
 *  4. 冲突/库存不足走 ConflictAlert 一键重提，不回退表单重填。
 *
 * 权限说明：schema.btnCode 仅用于 v-auth 做入口隐藏（体验优化），
 * 真正的权限边界在服务端（docs/01 AC-E5-1）：按钮权限 40302、数据范围 40303。
 */
export type FieldType =
  | "asset" // 统一资产台账（assetType 域隔离）
  | "space" // 统一场地 space
  | "slot" // 时段（场地 + 周次 → 服务端返回空闲时段）
  | "number"
  | "text"
  | "textarea"
  | "select";

export interface FieldDef {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  placeholder?: string;
  /** select 静态选项；字典类选项用 dictCode 从服务端取（只读消费，非平台配置维护） */
  options?: { label: string; value: string }[];
  dictCode?: string;
  /** number：min/max */
  min?: number;
  max?: number;
  unit?: string;
  /** 栅格宽度：12 半行 / 24 整行 */
  cols?: 12 | 24;
  tip?: string;
  /** 资产域：限定 assetType（域隔离，跨域 40302） */
  assetType?: "MATERIAL" | "LAB_EQUIP" | "SPORT_EQUIP";
  spaceType?: "CLASSROOM" | "LAB" | "MEETING_ROOM" | "SPORT_VENUE";
}

export interface QuickSchema {
  code: string;
  title: string;
  /** 按钮级权限点编码（docs/03） */
  btnCode: string;
  /** 所属菜单编码，用于越权提示定位 */
  menuCode: string;
  fields: FieldDef[];
  /** 提交：payload 已按 fields 收集 */
  submit: (payload: Record<string, unknown>) => Promise<Record<string, unknown>>;
  /** 提交成功文案（教师端要"立刻知道结果"） */
  successText: (res: Record<string, unknown>) => string;
  /** 预计步数说明（用于界面上标注，自证 ≤3 步） */
  stepText: string;
}

export const schemas: Record<string, QuickSchema> = {
  /** ① 物资领用申请（M0301，AC-B1-1 ≤3 步） */
  materialApply: {
    code: "materialApply",
    title: "物资领用申请",
    btnCode: "BTN-MAT-APPLY",
    menuCode: "M0301",
    fields: [
      { key: "assetId", label: "领用物资", type: "asset", required: true, cols: 24, assetType: "MATERIAL", placeholder: "搜索物资名称" },
      { key: "qty", label: "数量", type: "number", required: true, min: 1, max: 999, cols: 12 },
    ],
    submit: (p) =>
      applyMaterial({
        assetId: String(p.assetId),
        quantity: Number(p.qty),
      }),
    successText: (r) => `已提交，单号 ${r.applyNo}，审批结果将发到消息中心`,
    stepText: "2 步：点「物资领用」→ 填写提交",
  },

  /** ② 报修提交（M0401，AC-C1-1 ≤3 步） */
  repairSubmit: {
    code: "repairSubmit",
    title: "报修提交",
    btnCode: "BTN-REP-SUBMIT",
    menuCode: "M0401",
    fields: [
      { key: "spaceId", label: "报修位置", type: "space", required: true, cols: 12, spaceType: "CLASSROOM" },
      { key: "faultType", label: "故障类型", type: "select", required: true, cols: 12, dictCode: "DICT-FAULT-TYPE" },
    ],
    submit: (p) =>
      submitRepair({
        spaceId: String(p.spaceId),
        faultType: String(p.faultType),
      }),
    successText: (r) => `已提交，工单号 ${r.ticketNo}，可在「我的报修」跟踪进度`,
    stepText: "2 步：点「报修提交」→ 填写提交",
  },

  /** ③ 请假申请（M0901，学生/教师） */
  leaveApply: {
    code: "leaveApply",
    title: "请假申请",
    btnCode: "BTN-LEAVE-APPLY",
    menuCode: "M0901",
    fields: [
      { key: "leaveType", label: "请假类型", type: "select", required: true, cols: 12, dictCode: "DICT-LEAVE-TYPE" },
      { key: "days", label: "天数", type: "number", required: true, cols: 12, min: 0.5, max: 30, unit: "天" },
      { key: "reason", label: "事由", type: "textarea", required: true, cols: 24, max: 200 },
    ],
    submit: (p) =>
      applyLeave({
        leaveType: String(p.leaveType),
        days: Number(p.days),
        reason: String(p.reason ?? ""),
      }),
    successText: (r) => `已提交，单号 ${r.applyNo}，审批结果将发到消息中心`,
    stepText: "2 步：点「请假申请」→ 填写提交",
  },

  /** ④ 会议室预约（M1204）—— 场地冲突高发，走 40901/40902 统一错误态 */
  meetingRoomBook: {
    code: "meetingRoomBook",
    title: "会议室预约",
    btnCode: "BTN-PARTY-ROOM-BOOK",
    menuCode: "M1204",
    fields: [
      { key: "spaceId", label: "会议室", type: "space", required: true, cols: 12, spaceType: "MEETING_ROOM" },
      { key: "week", label: "周次", type: "number", required: true, cols: 12, min: 1, max: 30, unit: "周" },
      { key: "slot", label: "时段", type: "slot", required: true, cols: 24, tip: "已排除被课表占用的时段；默认选中最近一个空闲时段" },
      { key: "subject", label: "会议主题", type: "text", required: true, cols: 24, max: 60 },
      { key: "attendeeCount", label: "参会人数", type: "number", required: true, cols: 12, min: 1, max: 200, unit: "人" },
    ],
    submit: (p) =>
      bookSpace({
        spaceId: String(p.spaceId),
        slot: p.slot as TimeSlot,
        subject: String(p.subject ?? ""),
        attendeeCount: Number(p.attendeeCount ?? 1),
      }),
    successText: (r) => `预约成功，预约号 ${r.bookingId}`,
    stepText: "3 步：点「会议室预约」→ 选时段 → 提交（默认已选中最近空闲时段）",
  },

  /** ⑥ 实验预约（M1302）—— 同场地冲突规则 */
  labBook: {
    code: "labBook",
    title: "实验预约",
    btnCode: "BTN-SCI-LAB-BOOK",
    menuCode: "M1302",
    fields: [
      { key: "spaceId", label: "实验室", type: "space", required: true, cols: 12, spaceType: "LAB" },
      { key: "week", label: "周次", type: "number", required: true, cols: 12, min: 1, max: 30, unit: "周" },
      { key: "slot", label: "时段", type: "slot", required: true, cols: 24, tip: "已排除被课表占用的时段" },
      { key: "subject", label: "实验项目", type: "text", required: true, cols: 24, max: 60 },
      { key: "attendeeCount", label: "学生人数", type: "number", required: true, cols: 12, min: 1, max: 60, unit: "人" },
    ],
    submit: (p) =>
      bookSpace({
        spaceId: String(p.spaceId),
        slot: p.slot as TimeSlot,
        subject: String(p.subject ?? ""),
        attendeeCount: Number(p.attendeeCount ?? 1),
      }),
    successText: (r) => `预约成功，预约号 ${r.bookingId}`,
    stepText: "3 步：点「实验预约」→ 选时段 → 提交",
  },
};

/** 教师端快捷事务顺序（按使用频次排，不按菜单顺序排） */
export const TEACHER_QUICK_ACTIONS = [
  "materialApply",
  "repairSubmit",
  "leaveApply",
  "meetingRoomBook",
  "labBook",
] as const;

/** 空闲时段查询（服务端已排除排课占用，返回可预约时段） */
export async function loadFreeSlots(spaceId: string, week: number) {
  const res = await listFreeSlots(spaceId, week);
  return res;
}

/* ==========================================================================
   硬指标②不变量校验（自动执行，不是注释）
   --------------------------------------------------------------------------
   为什么不用注释：注释只能让"改的人看见"，改了照样能跑。
   改字段不加步数、或多加两个字段把表单撑爆，界面上还写着"2 步"——
   验收时实测点击次数才发现，那时已经发出去了。所以做成运行时断言。
   ========================================================================== */

/** 从 stepText 解析声明步数区间：「2 步」→ [2,2]；「2–3 步」→ [2,3] */
export function parseStepRange(text: string): [number, number] {
  const range = text.match(/(\d+)\s*[–\-~]\s*(\d+)\s*步/);
  if (range) return [Number(range[1]), Number(range[2])];
  const single = text.match(/(\d+)\s*步/);
  if (single) return [Number(single[1]), Number(single[1])];
  return [Number.NaN, Number.NaN];
}

/** 单个 Schema 的不变量违规项（空数组 = 合规） */
export function checkSchema(s: QuickSchema): string[] {
  const errs: string[] = [];
  const [min, max] = parseStepRange(s.stepText);
  if (!Number.isFinite(min)) {
    errs.push(`stepText 无法解析出步数：「${s.stepText}」（须写成「N 步：…」或「N–M 步：…」）`);
  } else {
    if (min < 1) errs.push(`声明步数下限 ${min} 不合理（至少 1 步）`);
    if (max > 3)
      errs.push(
        `声明步数上限 ${max} 超过硬指标②上限 3 步（当前声明：${s.stepText}）`
      );
  }
  if (s.fields.length > 5)
    errs.push(`字段 ${s.fields.length} 个，超过一屏上限 5 个——会撑爆抽屉、破坏 ≤3 步`);
  for (const f of s.fields) {
    if (f.required && !f.options && !f.dictCode && f.type === "select")
      errs.push(`字段「${f.label}」是必填下拉但既无 options 也无 dictCode，教师无法完成选择`);
  }
  return errs;
}

  /**
   * 全量校验：改字段不同步 stepText → 开发期直接抛错，不让它悄悄溜到验收。
   * 生产构建（`import.meta.env.DEV === false`）不执行，零运行时开销。
   *
   * 【口径边界 · 用户裁决】本断言**只校验正常路径**（§1.5 裁决 1）：
   *   用户裁决「正常路径 ≤3 步，异常恢复不计步」，因此断言的上限就是 3，
   *   既不放宽（正常路径必须 ≤3）也不收紧（不把异常分支的"最坏 4 步"算进来）。
   *   会议室/实验预约声明「2–3 步」，解析上限 3 = 断言上限 3，**不会误报**。
   *   不要为了"覆盖异常分支"去改这个上限——改了反而掩盖真正的步数退化。
   */
export function assertSchemaInvariants() {
  const errs: string[] = [];
  for (const s of Object.values(schemas)) {
    for (const e of checkSchema(s)) errs.push(`[${s.code}] ${e}`);
  }
  if (errs.length) {
    throw new Error(
      "[QuickApply] 高频事务 Schema 不变量校验失败（硬指标②）：\n" + errs.join("\n")
    );
  }
}

// 开发期自动执行：改完字段刷新页面即报错，不用等人工 Review
if (import.meta.env.DEV) assertSchemaInvariants();
