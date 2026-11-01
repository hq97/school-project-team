package com.school.common.scope;

import com.school.common.security.BizException;
import com.school.common.security.CurrentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 第二层：数据范围拦截器（TS 语义来源：data-scope.guard.ts）。
 *
 * <p>启动期 {@link DataScopeScanner} 已保证「缺 {@code @DataScope} 声明 → 拒绝启动」，因此运行到这里：
 * 要么有 {@code @DataScope}（执行范围校验），要么有 {@code @PublicDataScope}（豁免，跳过）。
 * 本拦截器负责：</p>
 * <ul>
 *   <li>校验角色有效范围是否覆盖接口声明的最低要求 → 40303（{@code satisfies()}）；</li>
 *   <li>角色范围不完整 → 按最小权限 SELF 兜底并打告警日志（AC-RBAC-2，见 {@link ScopeQueryBuilder#effectiveScope()}）；</li>
 *   <li>把 {@link DataScopeOptions} 放入 request attribute，供 Controller 通过
 *       {@link ScopeQueryBuilder#fromRequest(HttpServletRequest)} 重建查询构建器（数据层唯一出口）。</li>
 * </ul>
 */
@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    /** request attribute key：DataScopeOptions（供 Controller 重建 ScopeQueryBuilder） */
    public static final String REQUEST_ATTR_OPTIONS = "school:data-scope-options";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        DataScope ann = hm.getMethodAnnotation(DataScope.class);
        // 无 @DataScope：要么是 @PublicDataScope 豁免（启动扫描已保证），要么非数据接口；跳过。
        if (ann == null) {
            return true;
        }

        Principal principal = CurrentPrincipal.require();
        DataScopeOptions opts = DataScopeOptions.from(ann);
        ScopeQueryBuilder builder = ScopeQueryBuilder.of(principal, opts);
        if (!builder.satisfies()) {
            throw BizException.scopeDenied();
        }

        request.setAttribute(REQUEST_ATTR_OPTIONS, opts);
        return true;
    }
}
