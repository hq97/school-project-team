package com.school.common.scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 每个涉及数据读写的接口必须显式声明归属类型与范围要求（ADR §1.5 第二层）。
 *
 * <p>fail-closed：启动期 {@link DataScopeScanner} 扫描全部路由，任一条缺本声明（且未显式
 * {@link PublicDataScope} 豁免）即拒绝启动。前端 v-auth 隐藏按钮不构成任何安全边界。</p>
 *
 * <p><b>只认方法级</b>：本注解 {@code @Target(METHOD)}，编译期即禁止类级声明——
 * 防止「写在类上以为全类生效、实际语义是全类免检」的安全错觉（TS 语义来源：data-scope-scanner.ts H10）。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DataScope {

    /** 数据对象键，如 procurement_project / asset / space / notice */
    String resource();

    /** 归属类型 */
    OwnershipType ownership();

    /** 最低所需范围；缺省 "" = 不校验（按角色有效范围 MAX 判定） */
    String requiredScope() default "";

    /** 组织型：归属人字段 */
    String ownerField() default "ownerId";

    /** 组织型：归属部门字段 */
    String deptField() default "deptId";

    /** 组织型：班级载体字段（班主任把「本年级」收窄到该 CLASS）；"" = 无班级载体 */
    String classField() default "";

    /** 学期口径字段（聚合与鉴权先按学期+周次）；"" = 无学期维度 */
    String termField() default "";

    /**
     * 接口恒定按「本人」过滤，不随角色数据范围放大（如站内信 AC-MS7-1）。
     * 这是<b>接口级</b>约束，不是第四级数据范围（docs/03 §2.1-5 明令禁止新增第四级）。
     */
    boolean forceSelf() default false;

    /** 写操作：强制归属人 = 当前用户，忽略前端传入（防伪造 creatorId/applicantId） */
    boolean write() default false;
}
