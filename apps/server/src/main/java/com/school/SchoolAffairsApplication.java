package com.school;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 中学校务数字化平台后端服务入口。
 *
 * <p>模块划分（对应 ADR v3.0 §1.6）：</p>
 * <ul>
 *   <li>{@code common}    — 权限三层 fail-closed、ScopeQueryBuilder、错误码、AI 网关等横切</li>
 *   <li>{@code platform}  — auth / rbac / dict / space / asset / message / term / alert / jobs</li>
 *   <li>{@code domains}   — logistics / academic / moral / party / science / sport（六域零依赖）</li>
 *   <li>{@code crosscut}  — workflow（审批流引擎）/ dashboard（数据大屏聚合）</li>
 * </ul>
 */
@SpringBootApplication
public class SchoolAffairsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolAffairsApplication.class, args);
    }
}
