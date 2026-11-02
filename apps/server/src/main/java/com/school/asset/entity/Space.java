package com.school.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 统一场地（V1 表 space）。docs/04 §1.11。
 *
 * <p>{@code spaceType} 英文码：CLASSROOM / LAB / MEETING_ROOM / SPORT_VENUE；
 * {@code status} 英文码：AVAILABLE / DISABLED / REPAIRING；
 * {@code openScope} 开放范围：SELF / GRADE / ALL。</p>
 */
@TableName("\"space\"")
public class Space {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"spaceName\"")
    private String spaceName;

    @TableField("\"spaceType\"")
    private String spaceType;

    private Integer capacity;

    @TableField("\"deptNodeId\"")
    private String deptNodeId;

    private String status;

    @TableField("\"openScope\"")
    private String openScope;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSpaceName() { return spaceName; }
    public void setSpaceName(String spaceName) { this.spaceName = spaceName; }
    public String getSpaceType() { return spaceType; }
    public void setSpaceType(String spaceType) { this.spaceType = spaceType; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getDeptNodeId() { return deptNodeId; }
    public void setDeptNodeId(String deptNodeId) { this.deptNodeId = deptNodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOpenScope() { return openScope; }
    public void setOpenScope(String openScope) { this.openScope = openScope; }
}
