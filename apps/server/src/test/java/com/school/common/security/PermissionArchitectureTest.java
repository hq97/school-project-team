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

/**
 * 权限三层 fail-closed 第三层：ArchUnit 架构测试（封堵裸 SQL，测试期强制）。
 *
 * <p>比原 NestJS 的 ESLint {@code no-raw-sql.js} 启发式更强：ArchUnit 在编译/测试期做字节码级依赖判定，
 * 不依赖文本匹配。业务包 = domains / platform / crosscut；{@code common/scope} 是数据层唯一出口（豁免）。</p>
 *
 * <p><b>规则 ③ 的类级近似说明</b>：TS 的 ESLint 规则是「selectList 调用所在方法体内必须有 scopeQuery 痕迹」
 * （方法级共现）。ArchUnit 无法直接表达「同一方法内两个调用共现」，此处用<b>类级依赖</b>近似：
 * 「依赖 BaseMapper 的业务类必须同时依赖 ScopeQueryBuilder（走唯一出口）」。
 * 方法级精确校验由门禁 5 Code Review 补位。</p>
 *
 * <p><b>allowEmptyShould(true)</b>：骨架阶段六域业务包（domains/platform/crosscut）尚未落类，匹配集为空；
 * 规则声明「空集不判失败」（vacuous truth），待六域实现后自动生效。</p>
 */
@AnalyzeClasses(packages = "com.school")
public class PermissionArchitectureTest {

    private static final String[] BUSINESS_PACKAGES = {
            "com.school.domains..",
            "com.school.platform..",
            "com.school.crosscut.."
    };

    private static boolean residesInBusinessPackage(JavaClass input) {
        String pkg = input.getPackageName();
        for (String pattern : BUSINESS_PACKAGES) {
            // "com.school.domains.." -> "com.school.domains"
            if (pkg.startsWith(pattern.substring(0, pattern.length() - 2))) {
                return true;
            }
        }
        return false;
    }

    /** ① 业务包禁止裸 SQL：JdbcTemplate / NamedParameterJdbcTemplate / SqlSession / EntityManager */
    @ArchTest
    static final ArchRule business_must_not_access_raw_sql = noClasses()
            .that().resideInAnyPackage(BUSINESS_PACKAGES)
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.apache.ibatis.session.SqlSession")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
            .allowEmptyShould(true);

    /** ② 业务 Mapper 接口禁止声明 MyBatis 原生注解（@Select/@Update/@Insert/@Delete） */
    @ArchTest
    static final ArchRule business_mapper_must_not_use_raw_mybatis_annotations = noClasses()
            .that().resideInAnyPackage(BUSINESS_PACKAGES)
            .and().areAnnotatedWith(Mapper.class)
            .should().beAnnotatedWith(Select.class)
            .orShould().beAnnotatedWith(Update.class)
            .orShould().beAnnotatedWith(Insert.class)
            .orShould().beAnnotatedWith(Delete.class)
            .allowEmptyShould(true);

    /**
     * ③ 业务类依赖 BaseMapper（用 MyBatis-Plus 查询）则必须同时依赖 ScopeQueryBuilder（唯一出口）。
     *
     * <p>用「if-then」类级依赖表达：先把测试对象限定为「业务包内且依赖 BaseMapper」的类，
     * 再断言它们「依赖 ScopeQueryBuilder」。违反当且仅当「依赖 BaseMapper 且不依赖 ScopeQueryBuilder」。</p>
     */
    @ArchTest
    static final ArchRule business_using_mapper_must_also_use_scope_query = classes()
            .that(new DescribedPredicate<JavaClass>("reside in a business package and depend on BaseMapper") {
                @Override
                public boolean test(JavaClass input) {
                    if (!residesInBusinessPackage(input)) {
                        return false;
                    }
                    return input.getDirectDependenciesFromSelf().stream()
                            .anyMatch(dep -> dep.getTargetClass().isAssignableTo(BaseMapper.class));
                }
            })
            .should().dependOnClassesThat().areAssignableTo(ScopeQueryBuilder.class)
            .allowEmptyShould(true);
}
