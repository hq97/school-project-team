package com.school.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮/操作权限点（docs/03 BTN-*）：不满足 → 40302。
 *
 * <p>可标注在方法或类上（方法级优先于类级）。多个 code 之间为「全部满足」（AND）。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireButton {

    /** 按钮权限点编码，如 "BTN-MAT-APPLY" */
    String[] value();
}
