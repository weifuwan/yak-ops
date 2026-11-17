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
@TableName("t_yak_ops_agent_turn")
public class AgentTurn implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String sessionId;

    private Integer userId;

    private Long projectCode;

    private String status;

    private String contextJson;

    private Integer llmLatencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private String errorCode;

    private String errorMessage;

    private Date createTime;

    private String createBy;

    private Date updateTime;

    private String updateBy;
}
