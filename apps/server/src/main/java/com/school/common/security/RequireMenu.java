package com.school.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 菜单权限点（docs/03 M01–M15）：不满足 → 40301。
 *
 * <p>可标注在方法或类上（方法级优先于类级，TS 语义来源：global-permission.guard.ts 的
 * {@code getAllAndOverride([handler, class])}）。多个 code 之间为「全部满足」（AND），
 * 是更严格的 fail-closed 语义。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireMenu {

    /** 菜单权限点编码，如 "M01" / "M0505" */
    String[] value();
}
