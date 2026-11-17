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
@TableName("t_yak_ops_agent_session")
public class AgentSession implements Serializable {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private Integer userId;

    private String title;

    private String status;

    private Date createTime;

    private String createBy;

    private Date updateTime;

    private String updateBy;
}
