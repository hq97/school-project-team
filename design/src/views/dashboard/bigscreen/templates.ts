import type { ScreenTemplateKey } from "@/api/types";

/**
 * 分角色大屏模板「布局描述」
 * ---------------------------------------------------------------------------
 * 关键设计决策（AC-DD2-1 / AC-DD5-1 / AC-DD5-2）：
 *  1. 本文件**只描述布局**（哪个指标占几栅格、图表多高），
 *     **不含任何指标口径、聚合逻辑、过滤条件**——数据一律由服务端按角色下发。
 *     因此"年级组长只看到本年级"是服务端实现，不是前端裁剪。
 *  2. 前端不按角色写代码分支：只按服务端返回的 templateKey 取布局，
 *     新增角色 = 服务端加一个 templateKey，前端在此表加一行即可。
 *  3. 模板中**不允许出现平台级配置类指标**（字典 / 流程模板 / 菜单 / 角色 / 部门），
 *     这些对象服务端已过滤（AC-DD5-2），前端也不要为它们预留卡片位。
 */
export interface TemplateLayout {
  /** 页面主标题 */
  title: string;
  /** 副标题（口径提示：数据范围 + 时间口径） */
  subtitle: string;
  /** KPI 区：单卡栅格宽（24 栅格），默认 4（一行 6 个） */
  kpiSpan: number;
  /** KPI 最多展示数量（超出收进「更多指标」） */
  kpiMax: number;
  /** 异常预警区：标题 + 最多展示条数 */
  alertTitle: string;
  alertMax: number;
  /** 图表布局：code → 栅格宽 + 高度 */
  charts: Record<string, { span: number; height: number }>;
  /** 图表落位顺序（服务端返回的图表按此序排列，未列出的排在最后） */
  chartOrder: string[];
}

const LINE = { span: 12, height: 288 };
const BAR = { span: 12, height: 288 };
const BAR_H = { span: 12, height: 288 };
const RADAR = { span: 8, height: 240 };

export const TEMPLATES: Record<ScreenTemplateKey, TemplateLayout> = {
  /** 校长：全校驾驶舱 —— 六域核心指标 + 异常预警（硬指标①落点） */
  PRINCIPAL: {
    title: "全校驾驶舱",
    subtitle: "数据范围：全校 · 口径：当前学期",
    kpiSpan: 4,
    kpiMax: 6,
    alertTitle: "异常预警（需处理）",
    alertMax: 8,
    charts: {
      TREND_PURCHASE: LINE, // 趋势折线：采购金额
      TREND_REPAIR: LINE, // 趋势折线：报修量 / 响应时长
      DIST_LEAVE: BAR, // 分布柱状：请假人次
      CMP_GRADE_FITNESS: BAR_H, // 对比条形：各年级体测达标率
      RADAR_DOMAIN_HEALTH: RADAR, // 达标雷达：六域健康度
    },
    chartOrder: [
      "TREND_PURCHASE",
      "TREND_REPAIR",
      "DIST_LEAVE",
      "CMP_GRADE_FITNESS",
      "RADAR_DOMAIN_HEALTH",
    ],
  },

  /** 校领导（分管领域，领域可配） */
  VP: {
    title: "分管领域运营大屏",
    subtitle: "数据范围：全校（按分管领域） · 口径：当前学期",
    kpiSpan: 6,
    kpiMax: 4,
    alertTitle: "分管领域异常",
    alertMax: 6,
    charts: { TREND_PURCHASE: LINE, DIST_LEAVE: BAR, CMP_GRADE_FITNESS: BAR_H },
    chartOrder: ["TREND_PURCHASE", "DIST_LEAVE", "CMP_GRADE_FITNESS"],
  },

  /** 教务主任：教务运营大屏（docs/14 第一节 L20） */
  DEAN: {
    title: "教务运营大屏",
    subtitle: "数据范围：全校 · 口径：当前学期（周次）",
    kpiSpan: 4,
    kpiMax: 5,
    alertTitle: "教学运行异常（排课冲突 / 软约束违规 / 调课异常）",
    alertMax: 8,
    charts: {
      TREND_CHANGE: LINE, // 调课·代课次数趋势
      DIST_CONFLICT: BAR, // 排课冲突分布（按班级/学科）
      CMP_TEACHER_HOURS: BAR_H, // 教师周课时分布（对比条形）
      RADAR_SCHEDULE_QUALITY: RADAR, // 排课质量雷达（完整率/冲突/软约束）
    },
    chartOrder: ["TREND_CHANGE", "DIST_CONFLICT", "CMP_TEACHER_HOURS", "RADAR_SCHEDULE_QUALITY"],
  },

  /** 后勤：采购 / 库存 / 报修 */
  LOGISTICS: {
    title: "后勤运营大屏",
    subtitle: "数据范围：全校 · 口径：当前学期",
    kpiSpan: 6,
    kpiMax: 4,
    alertTitle: "库存不足 / 报修超时 / 审批滞留",
    alertMax: 8,
    charts: {
      TREND_PURCHASE: LINE,
      TREND_REPAIR: LINE,
      CMP_MATERIAL_TOP: BAR_H, // 高频领用物资 TOP
    },
    chartOrder: ["TREND_PURCHASE", "TREND_REPAIR", "CMP_MATERIAL_TOP"],
  },

  /** 德育处：请假 / 违纪 / 评优 */
  MORAL: {
    title: "德育运营大屏",
    subtitle: "数据范围：全校 · 口径：当前学期",
    kpiSpan: 6,
    kpiMax: 4,
    alertTitle: "违纪高频 / 请假未销假 / 处分待家长确认",
    alertMax: 8,
    charts: {
      DIST_LEAVE: BAR,
      DIST_DISCIPLINE: BAR,
      CMP_CLASS_DISCIPLINE: BAR_H,
    },
    chartOrder: ["DIST_LEAVE", "DIST_DISCIPLINE", "CMP_CLASS_DISCIPLINE"],
  },

  /** 党政办：公告已读率 / 公文流转 / 会议值班 */
  PARTY: {
    title: "党政运营大屏",
    subtitle: "数据范围：全校 · 口径：当前学期",
    kpiSpan: 6,
    kpiMax: 4,
    alertTitle: "滞留公文 / 未读公告 / 值班异常",
    alertMax: 8,
    charts: {
      TREND_NOTICE_READ: LINE,
      CMP_DEPT_DOC: BAR_H,
      RADAR_DOC_TIMELINESS: RADAR,
    },
    chartOrder: ["TREND_NOTICE_READ", "CMP_DEPT_DOC", "RADAR_DOC_TIMELINESS"],
  },

  /** 年级组长：本年级视图（数据范围由服务端收窄） */
  GRADE: {
    title: "本年级运营大屏",
    subtitle: "数据范围：本年级 · 口径：当前学期",
    kpiSpan: 6,
    kpiMax: 4,
    alertTitle: "本年级异常",
    alertMax: 6,
    charts: { DIST_LEAVE: BAR, CMP_CLASS_DISCIPLINE: BAR_H },
    chartOrder: ["DIST_LEAVE", "CMP_CLASS_DISCIPLINE"],
  },

  /** 教研组长：本组视图 */
  SUBJECT: {
    title: "本教研组大屏",
    subtitle: "数据范围：本教研组 · 口径：当前学期",
    kpiSpan: 6,
    kpiMax: 4,
    alertTitle: "本组待办",
    alertMax: 6,
    charts: { CMP_TEACHER_HOURS: BAR_H },
    chartOrder: ["CMP_TEACHER_HOURS"],
  },

  /**
   * 优雅降级模板（**不是"无大屏角色"的放行依据**）
   * -------------------------------------------------------------------------
   * 架构裁决：教师 / 学生 / 家长在 docs/03 的 M15 矩阵是「—」，即**无 M15 菜单**，
   * 服务端对他们的 overview 请求应直接返回 **40301**，前端也不会注册该路由
   * ——"无菜单就不渲染入口"是主路径。
   * NONE 只服务于「有 M15 菜单但没有预置模板」的自建角色（R-CUSTOM-*），
   * 让他们看到空态而不是报错。
   */
  NONE: {
    title: "",
    subtitle: "",
    kpiSpan: 6,
    kpiMax: 0,
    alertTitle: "",
    alertMax: 0,
    charts: {},
    chartOrder: [],
  },
};
