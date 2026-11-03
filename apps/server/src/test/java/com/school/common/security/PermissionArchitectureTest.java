package com.school.common.security;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.common.scope.ScopeQueryBuilder;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 权限三层 fail-closed 第三层：ArchUnit 架构测试（封堵裸 SQL，测试期强制）。
 *
 * <p>比原 NestJS 的 ESLint {@code no-raw-sql.js} 启发式更强：ArchUnit 在编译/测试期做字节码级依赖判定，
 * 不依赖文本匹配。业务包 = 六域业务模块 + 平台/横切包；{@code common/scope} 是数据层唯一出口（豁免）。</p>
 *
 * <p><b>包匹配统一走 {@link #BUSINESS_CLASS}</b>（显式根包前缀匹配），不依赖 {@code resideInAnyPackage}
 * 的子包语义——此前 BUSINESS_PACKAGES 只有不存在的 domains/platform/crosscut 三个占位包，导致
 * {@code allowEmptyShould(true)} 把「空集」误判为通过（空集放行）；现改为按「根包 + 子包前缀」显式判定，
 * 六域业务模块（procurement/material/repair/asset/dashboard）一律纳入封堵范围。</p>
 *
 * <p><b>规则 ③ 的类级近似说明</b>：TS 的 ESLint 规则是「selectList 调用所在方法体内必须有 scopeQuery 痕迹」
 * （方法级共现）。ArchUnit 无法直接表达「同一方法内两个调用共现」，此处用<b>方法调用近似</b>：
 * 「业务类若调用 BaseMapper 的列表/聚合查询（selectList/selectPage/selectMaps/selectObjs/selectCount），
 * 则必须同时依赖 ScopeQueryBuilder（走唯一出口）」。单点写回（selectById/updateById/insert）不受此约束
 * （审批回调 Handler 在 FlowEngine 事务内经 assertDataScope 校验后按 businessId 定点写回）。
 * 方法级精确校验由门禁 5 Code Review 补位。</p>
 */
@AnalyzeClasses(packages = "com.school")
public class PermissionArchitectureTest {

    /** 业务包根标识：domains/platform/crosscut（规划）+ 已落地的业务模块。 */
    private static final String[] BUSINESS_PACKAGES = {
            "com.school.domains",
            "com.school.platform",
            "com.school.crosscut",
            "com.school.procurement",
            "com.school.material",
            "com.school.repair",
            "com.school.asset",
            "com.school.dashboard",
            "com.school.schedule"
    };

    private static boolean residesInBusinessPackage(JavaClass input) {
        String pkg = input.getPackageName();
        for (String p : BUSINESS_PACKAGES) {
            if (pkg.equals(p) || pkg.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    /** 统一业务类谓词：三个规则共用同一套子包前缀匹配，避免 resideInAnyPackage 子包语义差异。 */
    private static final DescribedPredicate<JavaClass> BUSINESS_CLASS =
            new DescribedPredicate<JavaClass>("reside in a business package (incl. subpackages)") {
                @Override
                public boolean test(JavaClass input) {
                    return residesInBusinessPackage(input);
                }
            };

    /** 统一业务 Mapper 接口谓词：业务包内 + 标注 @Mapper（用于规则②方法级原生注解封堵）。 */
    private static final DescribedPredicate<JavaClass> BUSINESS_MAPPER =
            new DescribedPredicate<JavaClass>("business Mapper interface") {
                @Override
                public boolean test(JavaClass input) {
                    return residesInBusinessPackage(input) && input.isAnnotatedWith(Mapper.class);
                }
            };

    /** ① 业务包禁止裸 SQL：JdbcTemplate / NamedParameterJdbcTemplate / SqlSession / EntityManager */
    @ArchTest
    static final ArchRule business_must_not_access_raw_sql = noClasses()
            .that(BUSINESS_CLASS)
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.apache.ibatis.session.SqlSession")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
            .allowEmptyShould(true);

    /** ② 业务 Mapper 接口禁止声明 MyBatis 原生注解（@Select/@Update/@Insert/@Delete，方法级）；CAS 原子更新走 XML。 */
    @ArchTest
    static final ArchRule business_mapper_must_not_use_raw_mybatis_annotations = noMethods()
            .that().areDeclaredInClassesThat(BUSINESS_MAPPER)
            .should().beAnnotatedWith(Select.class)
            .orShould().beAnnotatedWith(Update.class)
            .orShould().beAnnotatedWith(Insert.class)
            .orShould().beAnnotatedWith(Delete.class)
            .allowEmptyShould(true);

    /**
     * ③ 业务类若调用 BaseMapper 的列表/聚合查询（selectList/selectPage/selectMaps/selectObjs/selectCount），
     * 则必须同时依赖 ScopeQueryBuilder（走唯一出口注入范围）。Mapper 接口本身排除。
     */
    @ArchTest
    static final ArchRule business_list_query_must_use_scope_query = classes()
            .that(new DescribedPredicate<JavaClass>("reside in a business package and call a BaseMapper list/aggregation query") {
                @Override
                public boolean test(JavaClass input) {
                    if (!residesInBusinessPackage(input)) {
                        return false;
                    }
                    if (input.isAssignableTo(BaseMapper.class)) {
                        return false; // 排除 Mapper 接口本身
                    }
                    return input.getMethodCallsFromSelf().stream().anyMatch(call -> {
                        String name = call.getTarget().getName();
                        return name.startsWith("selectList")
                                || name.startsWith("selectPage")
                                || name.startsWith("selectMaps")
                                || name.startsWith("selectObjs")
                                || name.startsWith("selectCount");
                    });
                }
            })
            .should().dependOnClassesThat().areAssignableTo(ScopeQueryBuilder.class)
            .allowEmptyShould(true);
}
