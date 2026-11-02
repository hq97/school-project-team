package com.school.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 统一资产台账（V1 表 asset）。docs/04 §1.6（域隔离）。
 *
 * <p>{@code assetType} 英文码：MATERIAL / LAB_EQUIP / SPORT_EQUIP；
 * {@code status} 英文码：IN_STOCK / LENT / REPAIRING / SCRAPPED；
 * {@code borrowDomain} 归属业务域（如 LOGISTICS / SCIENCE / SPORT），域隔离用。</p>
 */
@TableName("\"asset\"")
public class Asset {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String name;

    @TableField("\"assetType\"")
    private String assetType;

    private Integer quantity;

    @TableField("\"availableQty\"")
    private Integer availableQty;

    private String status;

    private String location;

    @TableField("\"borrowDomain\"")
    private String borrowDomain;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getAvailableQty() { return availableQty; }
    public void setAvailableQty(Integer availableQty) { this.availableQty = availableQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getBorrowDomain() { return borrowDomain; }
    public void setBorrowDomain(String borrowDomain) { this.borrowDomain = borrowDomain; }
}
