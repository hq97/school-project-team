package com.school.common.scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 第二层的 fail-closed 开关：启动期扫描全部路由，任一条缺 {@link DataScope} 声明
 * （且未显式 {@link PublicDataScope} 豁免）即拒绝启动（TS 语义来源：data-scope-scanner.ts）。
 *
 * <p>这是「漏配 = 裸奔」的根治手段——不是告警，是起不来（ADR §1.5 ②）。{@code ApplicationRunner#run}
 * 抛异常会中断 {@code SpringApplication.run}，服务启动失败。</p>
 *
 * <p><b>门禁4 审计 H10</b>：只认<b>方法级</b>声明。Java 侧由 {@code @DataScope @Target(METHOD)} 在编译期
 * 即禁止类级声明；本扫描器仅检查方法级，类级声明不存在（更彻底地消除了「写在类上以为全类生效」的安全错觉）。</p>
 */
@Component
public class DataScopeScanner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataScopeScanner.class);

    private final RequestMappingHandlerMapping handlerMapping;

    public DataScopeScanner(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<RequestMappingInfo, HandlerMethod> methods = handlerMapping.getHandlerMethods();
        List<String> missing = new ArrayList<>();
        int total = methods.size();
        int declared = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : methods.entrySet()) {
            HandlerMethod hm = e.getValue();
            Method m = hm.getMethod();
            boolean hasScope = m.isAnnotationPresent(DataScope.class);
            boolean isPublic = m.isAnnotationPresent(PublicDataScope.class);
            if (hasScope) {
                declared++;
            } else if (!isPublic) {
                String methodText = e.getKey().getMethodsCondition().getMethods().stream()
                        .map(Enum::name)
                        .toList()
                        .toString();
                missing.add(hm.getBeanType().getName() + "#" + m.getName() + " " + methodText);
            }
        }

        log.info("DataScope 声明扫描：路由 {} 条，已声明 {} 条，豁免 {} 条，缺失 {} 条",
                total, declared, total - declared - missing.size(), missing.size());

        if (!missing.isEmpty()) {
            for (String m : missing) {
                log.error("缺少 @DataScope() 声明：{}", m);
            }
            throw new IllegalStateException(
                    "拒绝启动：所有涉及数据读写的路由必须声明 @DataScope()，或显式标记 @PublicDataScope(reason)（ADR §1.5 ②）。缺失 " + missing.size() + " 条。");
        }
    }
}
