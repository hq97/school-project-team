package com.school.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.workflow.entity.FlowNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNode> {
}
