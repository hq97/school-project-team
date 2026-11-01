/**
 * 前端共享类型（与后端 NestJS DTO 对齐）
 * ---------------------------------------------------------------------------
 * 约定：
 * 1) 所有「时间维度」参数只有 TERM / MONTH 两个取值，默认 TERM；
 *    前端只传维度，不做任何聚合或过滤，聚合与鉴权一律在服务端完成。
 * 2) 前端不做数据权限过滤：列表 / 聚合结果以服务端返回为准。
 * 3) 平台级配置对象（数据字典 / 流程模板 / 菜单 / 角色 / 部门）不出现在
 *    任何大屏接口与下钻结果中（AC-DD5-2）。
 */

/** 统计时间维度：学期（默认，聚合/鉴权口径） / 自然月（仅展示维度） */
export type StatDim = "TERM" | "MONTH";

/** 三级数据范围（后端下发，前端仅展示，不参与判定） */
export type DataScope = "SELF" | "GRADE" | "ALL";

/** 预置角色编码（docs/03） */
export type RoleCode =
  | "R-ADMIN"
  | "R-PRINCIPAL"
  | "R-VP"
  | "R-DEAN"
  | "R-LOGISTICS"
  | "R-MORAL"
  | "R-PARTY"
  | "R-GRADE"
  | "R-SUBJECT"
  | "R-TEACHER"
  | "R-STUDENT"
  | "R-PARENT";

/** 大屏模板 key（服务端按角色下发，前端按 key 取本地布局描述） */
export type ScreenTemplateKey =
  | "PRINCIPAL" // 校长：全校驾驶舱
  | "VP" // 校领导：分管领域
  | "DEAN" // 教务主任：教务运营大屏
  | "LOGISTICS" // 后勤
  | "MORAL" // 德育处
  | "PARTY" // 党政办
  | "GRADE" // 年级组长：本年级
  | "SUBJECT" // 教研组长：本组
  | "NONE"; // 教师 / 学生 / 家长：无大屏

/** 指标卡状态：正常 / 预警 / 超阈值 */
export type KpiStatus = "NORMAL" | "WARNING" | "DANGER";

/** 趋势方向 */
export type TrendDir = "UP" | "DOWN" | "FLAT";

/** 指标卡（服务端已聚合 + 已按数据权限过滤） */
export interface KpiItem {
  /** 指标编码，前端按 code 取本地文案与布局，不解析业务含义 */
  code: string;
  label: string;
  value: number;
  unit?: string;
  /** 环比（上一学期，docs/04 §1.8） */
  mom?: number;
  /** 同比（去年同学期，docs/04 §1.8） */
  yoy?: number;
  trend?: TrendDir;
  /** 正向/反向指标：反向指标（如排课冲突数）上升为坏 */
  polarity?: "POSITIVE" | "NEGATIVE";
  status: KpiStatus;
  /** 阈值说明，hover 展示 */
  thresholdText?: string;
  /** 下钻路由（服务端下发；越权路由服务端仍会拒绝 40301/40303） */
  drillTo?: string;
}

/** 图表（服务端返回维度已聚合好的 series，前端只负责渲染） */
export interface ChartItem {
  code: string;
  title: string;
  /** 图表类型：趋势折线 / 分布柱状 / 对比条形 / 达标雷达 */
  type: "LINE" | "BAR" | "BAR_H" | "RADAR";
  /** 类目轴（学期周次 或 自然月） */
  categories: string[];
  series: { name: string; data: number[]; colorKey?: string }[];
  unit?: string;
  /** 雷达图指标维度 */
  indicators?: { name: string; max: number }[];
  drillTo?: string;
}

/** 异常预警（C10 alert 聚合） */
export interface AlertItem {
  id: string;
  type: string;
  level: "WARNING" | "DANGER";
  title: string;
  /** 责任人，随数据范围下发（年级组长只见本年级） */
  ownerName?: string;
  /** 超期时长文案由服务端下发，前端不自算 */
  overdueText?: string;
  /** 定位到业务对象 */
  linkTo: string;
}

/** 大屏总览响应 */
export interface OverviewResp {
  roleCode: RoleCode;
  templateKey: ScreenTemplateKey;
  /** 数据截止时间（REQ-DD7） */
  dataAsOf: string;
  /** 服务端聚合结果集，已按数据权限过滤 */
  kpis: KpiItem[];
  charts: ChartItem[];
  alerts: AlertItem[];
  /** 本角色可见的数据范围说明，用于页面顶部"口径提示" */
  scope: DataScope;
  scopeText: string;
  /** 当前生效学期（默认口径） */
  currentTerm: string;
}

/**
 * 资产类型（assetType）—— **英文码**，中文显示名由字典提供
 * 后端定调：库内与 API 一律英文码，前端取到英文码后由展示层走字典翻译。
 */
export type AssetType = "MATERIAL" | "LAB_EQUIP" | "SPORT_EQUIP";

/** 资产状态枚举（ADR §1.7：英文码，中文显示名由 DICT-* 字典提供） */
export type AssetStatus = "IN_STOCK" | "LENT" | "REPAIRING" | "SCRAPPED";

/**
 * 场地类型（spaceType）—— **英文码**，与 docs/04 §1.11 一致
 */
export type SpaceType = "CLASSROOM" | "LAB" | "MEETING_ROOM" | "SPORT_VENUE";

/** 场地状态枚举（ADR §1.7） */
export type SpaceStatus = "AVAILABLE" | "DISABLED" | "REPAIRING";

/** 请假类型 / 违纪类型等业务字典项编码，同样为英文码 */
export type DictCode =
  | "DICT-STATUS"
  | "DICT-FAULT-TYPE"
  | "DICT-DISC-LEVEL"
  | "DICT-TERM"
  | "DICT-ASSET-TYPE"
  | "DICT-FITNESS-STD"
  | "DICT-OVERDUE-DAYS"
  | "DICT-LEAVE-TYPE"
  | "DICT-DISC-TYPE"
  | "DICT-SPACE-TYPE";

/** 统一资产台账（asset，域隔离：assetType × 借用权限点） */
export interface AssetItem {
  id: string;
  name: string;
  /** 后勤物资 / 科教实验器材 / 体育器材 */
  assetType: AssetType;
  /** 可用库存（服务端实时值，前端不本地扣减；**全链路不经缓存**） */
  availableQty: number;
  unit: string;
  location?: string;
  /** 在库 / 借出 / 报修 / 报废 */
  status: AssetStatus;
}

/** 场地（space，组织型归属） */
export interface SpaceItem {
  spaceId: string;
  spaceName: string;
  spaceType: SpaceType;
  capacity: number;
  /** 可用 / 停用 / 维修中 */
  status: SpaceStatus;
}

/** 时段格（周次 + 星期 + 节次） */
export interface TimeSlot {
  week: number;
  dayOfWeek: number; // 1=周一
  period: number; // 节次
}

/**
 * 统一错误码（docs/04 §五、docs/02 §三、ADR §4.1 C9）
 * ---------------------------------------------------------------------------
 * 全部为数字编号，409 段归「资源冲突类」：
 *   40901 场地被课表占用（排课权威源）
 *   40902 并发预约竞争失败（C9 时段格唯一性）
 *   40903 库存不足（C9 availableQty 原子扣减失败）
 *   40904 名额已满（C9 名额扣减失败）
 * 业务语义不用字符串码表达，一律落在 data 的结构化字段里（stock / quota / conflict）。
 */
export type BizErrorCode =
  | 40301 // NO_MENU_PERM 无菜单权限
  | 40302 // NO_ACTION_PERM 无按钮/操作权限
  | 40303 // DATA_SCOPE_DENIED 数据范围越权
  | 40304 // RESOURCE_NOT_FOUND 记录不存在（防枚举）
  | 40901 // SPACE_SCHEDULE_CONFLICT 场地已被课表占用
  | 40902 // SPACE_RESERVATION_CONFLICT 并发预约竞争失败
  | 40903 // STOCK_SHORT 库存不足（AC-B1-3 不超卖）
  | 40904; // QUOTA_FULL 名额已满（评优/竞赛/赛事）

/** 业务错误体（后端统一返回结构） */
export interface BizError {
  code: BizErrorCode;
  message: string;
  /**
   * 冲突详情（40901 / 40902）
   * -------------------------------------------------------------------------
   * 【越权红线 · 架构裁决】`occupiedBy` 是课表数据，必须按请求人的数据范围分级脱敏：
   *   - 请求人范围覆盖该占用（ALL，或 GRADE 且命中该年级/班级）→ 下发「班级 + 学科 + 教师」；
   *   - 否则只下发占位文案「已被课表占用」，**不下发班级 / 教师 / 预约人姓名**。
   * 前端必须兼容 `occupiedBy` 缺失或降级为占位文案的情况，禁止渲染 undefined。
   */
  conflict?: {
    spaceName: string;
    slotText: string;
    /** 占用来源：如"高三(1)班 数学 张老师的课"；越权时降级为占位文案 */
    occupiedBy?: string;
    /**
     * 占用明细可见标志（服务端下发，架构裁决）
     * -------------------------------------------------------------------------
     * `true`  = occupiedBy 是真实占用明细（请求人数据范围覆盖该占用）→ 可给「查看该场地课表」入口
     * `false` = 仅占位文案 → 不给该入口
     * **前端不得用「occupiedBy 是否为空 / 是否等于某段占位文案」来判断**——
     * 占位文案会随字典与文案微调变化，字符串比较迟早失效。
     */
    hasOccupancyDetail: boolean;
    /**
     * 服务端推荐的替代时段。
     * 只会来自「请求人可预约范围内且当前 FREE」的格子并已过数据范围过滤，
     * 不会推荐无权预约的时段（前端可直接渲染为可点击按钮）。
     */
    suggestions?: { slot: TimeSlot; slotText: string }[];
  };
  /** 库存不足（40903，AC-B1-3） */
  stock?: { name: string; availableQty: number; requestedQty: number };
  /** 名额已满（40904，评优 / 竞赛 / 赛事） */
  quota?: { name: string; quota: number };
}

/**
 * 占用矩阵格子状态（GET /api/spaces/:id/occupancies）
 * ---------------------------------------------------------------------------
 * RESERVED 格子对 SELF 范围用户只下发状态、不下发预约人（防借占用反查他人行程）。
 */
export type OccupancyState = "FREE" | "SCHEDULE" | "RESERVED";
