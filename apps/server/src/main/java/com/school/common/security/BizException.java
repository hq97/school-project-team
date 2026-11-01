package com.school.common.security;

import java.util.Map;

/**
 * 统一业务异常（TS 语义来源：biz-exception.ts）。
 *
 * <p>携带 {@link BizCode} + 可选 {@code data} 结构化详情，供前端三段式渲染
 * （失败原因 / 冲突详情 / 一键恢复）。</p>
 *
 * <p>{@code data} 支持的结构（docs/02 §3.2）：</p>
 * <ul>
 *   <li>{@code conflict}：{@code { spaceName, slotText, occupiedBy?, hasOccupancyDetail, suggestions[] }}；
 *       {@code hasOccupancyDetail} 为布尔标志（前端据此判断，禁止字符串比较）；
 *       {@code slotText} 由服务端渲染（前端不拼接）。</li>
 *   <li>{@code stock}：{@code { name, availableQty, requestedQty }}（40903 扣减失败当次事务内实时读值，不经缓存）。</li>
 *   <li>{@code quota}：{@code { name, quota }}（40904）。</li>
 * </ul>
 */
public class BizException extends RuntimeException {

    private final BizCode bizCode;
    private final Map<String, Object> data;

    public BizException(BizCode bizCode) {
        this(bizCode, null, bizCode.message());
    }

    public BizException(BizCode bizCode, Map<String, Object> data) {
        this(bizCode, data, bizCode.message());
    }

    public BizException(BizCode bizCode, Map<String, Object> data, String message) {
        super(message);
        this.bizCode = bizCode;
        this.data = data;
    }

    public BizCode bizCode() {
        return bizCode;
    }

    public Map<String, Object> data() {
        return data;
    }

    // ---- 静态工厂（与 TS BizException 各静态方法一一对应） ----

    public static BizException unauthorized() {
        return new BizException(BizCode.UNAUTHENTICATED);
    }

    public static BizException menuDenied() {
        return new BizException(BizCode.NO_MENU_PERM);
    }

    public static BizException actionDenied() {
        return new BizException(BizCode.NO_ACTION_PERM);
    }

    public static BizException scopeDenied() {
        return new BizException(BizCode.DATA_SCOPE_DENIED);
    }

    /** 单条越权 / 不存在：统一 40304 防枚举（docs/04 §4.3） */
    public static BizException notFound() {
        return new BizException(BizCode.RESOURCE_NOT_FOUND);
    }

    public static BizException scheduleConflict(Map<String, Object> conflict) {
        return new BizException(BizCode.SPACE_SCHEDULE_CONFLICT, Map.of("conflict", conflict));
    }

    public static BizException reservationConflict(Map<String, Object> conflict) {
        return new BizException(BizCode.SPACE_RESERVATION_CONFLICT, Map.of("conflict", conflict));
    }

    public static BizException stockShort(Map<String, Object> stock) {
        return new BizException(BizCode.STOCK_SHORT, Map.of("stock", stock));
    }

    public static BizException quotaFull(Map<String, Object> quota) {
        return new BizException(BizCode.QUOTA_FULL, Map.of("quota", quota));
    }
}
