package com.school.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户-部门关联（V1 表 user_dept，复合主键 userId+deptId）。
 */
@TableName("\"user_dept\"")
public class UserDept {

    @TableField("\"userId\"")
    private String userId;

    @TableField("\"deptId\"")
    private String deptId;

    public UserDept() {
    }

    public UserDept(String userId, String deptId) {
        this.userId = userId;
        this.deptId = deptId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }
}
