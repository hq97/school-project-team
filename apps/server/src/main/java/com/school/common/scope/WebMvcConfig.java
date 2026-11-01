package com.school.common.scope;

import com.school.common.security.MenuButtonInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：注册权限三层中的拦截器（顺序 = ADR §1.5 第一、二层续 + 第二层）。
 *
 * <p>顺序：</p>
 * <ol>
 *   <li>{@link MenuButtonInterceptor}（菜单 40301 / 按钮 40302）</li>
 *   <li>{@link DataScopeInterceptor}（数据范围 40303 + 挂载 DataScopeOptions）</li>
 * </ol>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MenuButtonInterceptor menuButtonInterceptor;
    private final DataScopeInterceptor dataScopeInterceptor;

    public WebMvcConfig(MenuButtonInterceptor menuButtonInterceptor, DataScopeInterceptor dataScopeInterceptor) {
        this.menuButtonInterceptor = menuButtonInterceptor;
        this.dataScopeInterceptor = dataScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(menuButtonInterceptor).order(1).addPathPatterns("/api/**");
        registry.addInterceptor(dataScopeInterceptor).order(2).addPathPatterns("/api/**");
    }
}
