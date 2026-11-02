package com.school.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 物资领用申请（V1 表 material_requisition）。走审批流（businessType=material）。
 *
 * <p>{@code status} 英文码（DICT-STATUS）：PENDING / APPROVING / APPROVED / REJECTED / COMPLETED / CANCELLED。</p>
 */
@TableName("\"material_requisition\"")
public class MaterialRequisition {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"applyNo\"")
    private String applyNo;

    @TableField("\"applicantId\"")
    private String applicantId;

    @TableField("\"applicantDeptId\"")
    private String applicantDeptId;

    @TableField("\"assetId\"")
    private String assetId;

    private Integer quantity;

    @TableField("\"termId\"")
    private String termId;

    private String status;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplyNo() { return applyNo; }
    public void setApplyNo(String applyNo) { this.applyNo = applyNo; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getApplicantDeptId() { return applicantDeptId; }
    public void setApplicantDeptId(String applicantDeptId) { this.applicantDeptId = applicantDeptId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
