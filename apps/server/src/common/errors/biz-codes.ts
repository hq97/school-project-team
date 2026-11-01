/**
 * 统一业务错误码（事实源：docs/04 §4.3 + docs/02 §3.2）
 * 编号规律：401 未认证 / 40301–40304 越权 / 40901–40904 资源冲突（C9）
 */
export const BizCode = {
  PARAM_INVALID: 40000, // 参数不合法（含角色保存校验缺失项，AC-RBAC-1）
  UNAUTHENTICATED: 401,

  NO_MENU_PERM: 40301, // 无菜单权限
  NO_ACTION_PERM: 40302, // 无按钮/操作权限（含 asset 跨域借用）
  DATA_SCOPE_DENIED: 40303, // 数据范围越权
  RESOURCE_NOT_FOUND: 40304, // 记录不存在（防枚举，单条越权统一返此码）

  SPACE_SCHEDULE_CONFLICT: 40901, // 场地已被课表占用（排课权威源）
  SPACE_RESERVATION_CONFLICT: 40902, // 并发预约竞争失败
  STOCK_SHORT: 40903, // 库存不足（C9 原子扣减失败）
  QUOTA_FULL: 40904, // 名额已满（C9 名额扣减失败）
} as const;

export type BizCodeValue = (typeof BizCode)[keyof typeof BizCode];

export const BIZ_CODE_MESSAGE: Record<BizCodeValue, string> = {
  401: '请重新登录',
  40000: '请求参数不合法',
  40301: '您没有权限访问该功能',
  40302: '您没有权限执行此操作',
  40303: '数据不存在或您无权访问',
  40304: '数据不存在或您无权访问',
  40901: '该时段已被课表占用，无法预约',
  40902: '该时段刚被他人预约，请重新选择时段',
  40903: '库存不足，本次申请未提交',
  40904: '名额已满，本次报名未成功',
};

/** 业务码 → HTTP 状态码 */
export function httpStatusOf(code: BizCodeValue): number {
  if (code === 401) return 401;
  if (code >= 40000 && code < 40100) return 400;
  if (code >= 40301 && code <= 40304) return 403;
  if (code >= 40901 && code <= 40904) return 409;
  return 400;
}
