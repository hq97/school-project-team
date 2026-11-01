/**
 * 设计 Token（TS 侧唯一真源）
 * ---------------------------------------------------------------------------
 * 与 @/design/tokens.css 保持数值一致。ECharts option、内联样式、SVG 图表等
 * 需要 JS 取值的场景一律从这里取，禁止在业务代码里硬编码色值。
 */
export const color = {
  primary: "#2B5CE6",
  primaryHover: "#1E48C7",
  primaryBg: "#EEF3FF",
  primaryBorder: "#C7D7FF",

  success: "#1F9254",
  successBg: "#E8F6EE",
  warning: "#B26A00",
  warningBg: "#FFF5E6",
  danger: "#C0362C",
  dangerBg: "#FDECEA",
  info: "#4B5563",
  infoBg: "#F1F3F5",

  trendUp: "#1F9254",
  trendDown: "#C0362C",
  trendFlat: "#8A94A6",

  text1: "#1B1F27",
  text2: "#4E5867",
  text3: "#8A94A6",

  border1: "#E3E8EF",
  border2: "#EEF1F5",
  bgPage: "#F5F7FA",
  bgCard: "#FFFFFF",
} as const;

/** 六域固定色板：同一域在任何图表里都必须用同一个色，禁止逐图随机取色 */
export const domainColor = {
  /** 教务 */
  academic: "#2B5CE6",
  /** 德育 */
  moral: "#7A5AF8",
  /** 后勤 */
  logistics: "#00A38D",
  /** 党政办 */
  party: "#F2A33C",
  /** 科教 */
  science: "#3E8EDE",
  /** 体育 */
  sport: "#E8544B",
} as const;

export type DomainKey = keyof typeof domainColor;

/** 图表通用色（轴 / 网格 / 降级色） */
export const chart = {
  axis: "#D9DFE8",
  split: "#EEF1F5",
  text: "#8A94A6",
  /** 非六域类目（如"其它"）统一用中性色 */
  neutral: "#A9B3C4",
} as const;

/** 间距刻度（4px 基准，只允许这些值） */
export const space = [0, 4, 8, 12, 16, 20, 24, 32, 40, 48] as const;

/** 字号刻度 */
export const fontSize = {
  xs: 12,
  sm: 13,
  base: 14,
  md: 16,
  lg: 20,
  kpi: 24,
  kpiLarge: 32,
} as const;

/** 圆角 */
export const radius = { sm: 4, md: 6, lg: 8, xl: 12 } as const;

/** 大屏布局常量 */
export const screenLayout = {
  /** 栅格间距，固定 16px */
  gap: 16,
  /** KPI 卡高度 */
  kpiHeight: 96,
  /** 小图表高度 */
  chartSm: 240,
  /** 中图表高度 */
  chartMd: 288,
} as const;

/**
 * 统一图表字体配置（ECharts）
 * 理由：大屏是办公浅色场景，字号统一、不加粗，避免"挂墙大屏"观感。
 */
export const chartTextStyle = {
  fontFamily:
    '"PingFang SC","Microsoft YaHei","Helvetica Neue",Helvetica,Arial,sans-serif',
  fontSize: 12,
  color: color.text3,
} as const;
