package com.school.common.security;

/**
 * 统一业务错误码（事实源：docs/04 §4.3 + docs/02 §3.2；TS 语义来源：biz-codes.ts）。
 *
 * <p>编号规律：401 未认证 / 40301–40304 越权 / 40901–40904 资源冲突（C9）。</p>
 *
 * <p><b>两类性质不混（门禁红线）</b>：</p>
 * <ul>
 *   <li><b>403xx 权限类</b>：客户端「重试无效」——权限不足/越权/防枚举，重试不会改变结果；</li>
 *   <li><b>409xx 冲突类</b>：客户端「可重试」——库存不足/名额已满/时段冲突，携带实时值供前端回填后重试。</li>
 * </ul>
 */
public enum BizCode {

    PARAM_INVALID(40000, "请求参数不合法", 400),

    UNAUTHENTICATED(401, "请重新登录", 401),

    NO_MENU_PERM(40301, "您没有权限访问该功能", 403),
    NO_ACTION_PERM(40302, "您没有权限执行此操作", 403),
    DATA_SCOPE_DENIED(40303, "数据不存在或您无权访问", 403),
    RESOURCE_NOT_FOUND(40304, "数据不存在或您无权访问", 403),

    SPACE_SCHEDULE_CONFLICT(40901, "该时段已被课表占用，无法预约", 409),
    SPACE_RESERVATION_CONFLICT(40902, "该时段刚被他人预约，请重新选择时段", 409),
    STOCK_SHORT(40903, "库存不足，本次申请未提交", 409),
    QUOTA_FULL(40904, "名额已满，本次报名未成功", 409);

    private final int code;
    private final String message;
    private final int httpStatus;

    BizCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    /** 业务码 → HTTP 状态码（TS httpStatusOf 等价） */
    public int httpStatus() {
        return httpStatus;
    }
}
