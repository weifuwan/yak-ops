package io.yak.ops.agent.dao.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_yak_ops_agent_tool_call")
public class AgentToolCall implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String sessionId;

    private String turnId;

    private String messageId;

    private String toolName;

    private String argumentsJson;

    private String resultSummary;

    private Integer durationMs;

    private String status;

    private Date createTime;

    private String createBy;

    private Date updateTime;

    private String updateBy;
}
