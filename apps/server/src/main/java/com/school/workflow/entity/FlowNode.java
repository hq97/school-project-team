package com.school.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 流程节点（V1 表 flow_node）。docs/07 REQ-D3。
 *
 * <p>节点固定线性（nodeType=LINEAR），按 {@code seq} 升序流转；
 * {@code approveRole} 为审批角色码（role_code），是审批人的最小授权单元（docs/07 第六节）。</p>
 */
@TableName("\"flow_node\"")
public class FlowNode {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"templateId\"")
    private String templateId;

    private Integer seq;

    private String name;

    @TableField("\"approveRole\"")
    private String approveRole;

    @TableField("\"timeoutHours\"")
    private Integer timeoutHours;

    @TableField("\"nodeType\"")
    private String nodeType;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApproveRole() { return approveRole; }
    public void setApproveRole(String approveRole) { this.approveRole = approveRole; }
    public Integer getTimeoutHours() { return timeoutHours; }
    public void setTimeoutHours(Integer timeoutHours) { this.timeoutHours = timeoutHours; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
}
