package com.school.system.enums;

/**
 * 部门类型（V1 库内枚举 DeptType：SCHOOL/GRADE/CLASS/SUBJECT/OFFICE，英文码）。
 *
 * <p>GRADE 为「本年级」数据范围的组织载体；CLASS 为班级载体（班主任把本年级收窄到本班）。</p>
 */
public enum DeptType {
    SCHOOL,
    GRADE,
    CLASS,
    SUBJECT,
    OFFICE
}
