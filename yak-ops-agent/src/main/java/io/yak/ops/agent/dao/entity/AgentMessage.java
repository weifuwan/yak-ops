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
@TableName("t_yak_ops_agent_message")
public class AgentMessage implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String sessionId;

    private String turnId;

    private String role;

    private String content;

    private String contextJson;

    private Date createTime;

    private String createBy;

    private Date updateTime;

    private String updateBy;
}
