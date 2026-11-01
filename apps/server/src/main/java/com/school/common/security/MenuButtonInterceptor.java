package com.school.common.security;

import com.school.common.scope.Principal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 第一层（续）：菜单/按钮权限拦截器（TS 语义来源：global-permission.guard.ts）。
 *
 * <p>读 handler 的 {@link RequireMenu} / {@link RequireButton}（方法级优先于类级），
 * 比对 {@code principal.menuCodes / buttonCodes}，缺 → 40301 / 40302。</p>
 *
 * <p>前端 v-auth 隐藏按钮只是体验优化，这里的校验才是安全边界（docs/01 AC-E5-1、docs/05 关键边界 2）。</p>
 */
@Component
public class MenuButtonInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        RequireMenu menu = hm.getMethodAnnotation(RequireMenu.class);
        if (menu == null) {
            menu = hm.getBeanType().getAnnotation(RequireMenu.class);
        }
        RequireButton button = hm.getMethodAnnotation(RequireButton.class);
        if (button == null) {
            button = hm.getBeanType().getAnnotation(RequireButton.class);
        }

        if (menu == null && button == null) {
            return true;
        }

        Principal principal = CurrentPrincipal.require();
        if (menu != null) {
            for (String code : menu.value()) {
                if (!principal.menuCodes().contains(code)) {
                    throw BizException.menuDenied();
                }
            }
        }
        if (button != null) {
            for (String code : button.value()) {
                if (!principal.buttonCodes().contains(code)) {
                    throw BizException.actionDenied();
                }
            }
        }
        return true;
    }
}
