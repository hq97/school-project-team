package com.school.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 流程模板（V1 表 flow_template）。docs/07 REQ-D3。
 *
 * <p>{@code businessType} 与业务类型一一绑定（每个业务类型一个启用模板，AC-D3-2）；
 * {@code rejectPolicy} 取值 RETURN（退回发起人可重提）/ TERMINATE（直接终止），AC-D3-7。</p>
 */
@TableName("\"flow_template\"")
public class FlowTemplate {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    @TableField("\"businessType\"")
    private String businessType;

    private Boolean enabled;

    private Integer version;

    @TableField("\"rejectPolicy\"")
    private String rejectPolicy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRejectPolicy() { return rejectPolicy; }
    public void setRejectPolicy(String rejectPolicy) { this.rejectPolicy = rejectPolicy; }
}
