package com.school.procurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 采购项目（V1 表 procurement_project）。走审批流（businessType=procurement）。
 *
 * <p>{@code status} 英文码（DICT-STATUS）：PENDING / APPROVING / APPROVED / REJECTED / COMPLETED / CANCELLED。</p>
 */
@TableName("\"procurement_project\"")
public class ProcurementProject {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"applyNo\"")
    private String applyNo;

    private String name;

    @TableField("\"creatorId\"")
    private String creatorId;

    @TableField("\"creatorDeptId\"")
    private String creatorDeptId;

    @TableField("\"termId\"")
    private String termId;

    private BigDecimal budget;

    private String status;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplyNo() { return applyNo; }
    public void setApplyNo(String applyNo) { this.applyNo = applyNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public String getCreatorDeptId() { return creatorDeptId; }
    public void setCreatorDeptId(String creatorDeptId) { this.creatorDeptId = creatorDeptId; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
