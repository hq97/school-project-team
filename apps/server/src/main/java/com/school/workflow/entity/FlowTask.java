package com.school.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 流程审批任务（V1 表 flow_task）。docs/07 REQ-D3。
 *
 * <p>每级审批节点一个 task，未处理时 {@code approverId} 为空（该节点审批角色下任意用户可审）；
 * 处理后回填 {@code approverId} + {@code conclusion}（APPROVED/REJECTED）+ {@code actedAt}（AC-D3-5 留痕）。</p>
 */
@TableName("\"flow_task\"")
public class FlowTask {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"instanceId\"")
    private String instanceId;

    private Integer seq;

    @TableField("\"approverId\"")
    private String approverId;

    /** 审批角色码（role_code） */
    @TableField("\"role\"")
    private String role;

    /** APPROVED / REJECTED，null 表示待办 */
    private String conclusion;

    @TableField("\"comment\"")
    private String comment;

    @TableField("\"actedAt\"")
    private OffsetDateTime actedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public OffsetDateTime getActedAt() { return actedAt; }
    public void setActedAt(OffsetDateTime actedAt) { this.actedAt = actedAt; }
}
