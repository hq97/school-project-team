package com.school.common.scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 豁免声明：仅限「不读不写业务数据」的接口（如登录、健康检查、字典只读消费）。
 *
 * <p>必须携带 {@code reason} 说明豁免理由（TS 语义来源：data-scope.decorator.ts 的 {@code @PublicDataScope}）。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicDataScope {

    /** 豁免理由（写入代码，供门禁 5 Review 复核） */
    String reason();
}
