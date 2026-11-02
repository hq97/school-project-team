package com.school.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 资产借用记录（V1 表 asset_borrow）。领用审批通过时落账。
 */
@TableName("\"asset_borrow\"")
public class AssetBorrow {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"assetId\"")
    private String assetId;

    @TableField("\"borrowerId\"")
    private String borrowerId;

    private Integer quantity;

    @TableField("\"termId\"")
    private String termId;

    @TableField("\"dueAt\"")
    private OffsetDateTime dueAt;

    @TableField("\"returnedAt\"")
    private OffsetDateTime returnedAt;

    @TableField("\"applyNo\"")
    private String applyNo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getBorrowerId() { return borrowerId; }
    public void setBorrowerId(String borrowerId) { this.borrowerId = borrowerId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(OffsetDateTime returnedAt) { this.returnedAt = returnedAt; }
    public String getApplyNo() { return applyNo; }
    public void setApplyNo(String applyNo) { this.applyNo = applyNo; }
}
