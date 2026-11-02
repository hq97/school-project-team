package com.school.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 流程实例（V1 表 flow_instance）。docs/07 REQ-D3。
 *
 * <p>{@code templateSnap} 为创建时的模板+节点完整快照（JSON 文本，V7 由 JSONB 改 TEXT），
 * 保证「进行中实例沿用创建时模板」的确定性（AC-D3-3），模板后续修改不影响历史实例。</p>
 *
 * <p>{@code status} 文本码：PENDING 进行中 / COMPLETED 完成 / REJECTED 已驳回（可重提）/ TERMINATED 已终止。</p>
 */
@TableName("\"flow_instance\"")
public class FlowInstance {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"businessType\"")
    private String businessType;

    @TableField("\"businessId\"")
    private String businessId;

    @TableField("\"templateId\"")
    private String templateId;

    /** 模板+节点快照（JSON 文本） */
    @TableField("\"templateSnap\"")
    private String templateSnap;

    @TableField("\"currentSeq\"")
    private Integer currentSeq;

    private String status;

    @TableField("\"createdAt\"")
    private OffsetDateTime createdAt;

    /** 发起人（V7 新增，AC-D3-4 归属判定） */
    @TableField("\"creatorId\"")
    private String creatorId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTemplateSnap() { return templateSnap; }
    public void setTemplateSnap(String templateSnap) { this.templateSnap = templateSnap; }
    public Integer getCurrentSeq() { return currentSeq; }
    public void setCurrentSeq(Integer currentSeq) { this.currentSeq = currentSeq; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
}
