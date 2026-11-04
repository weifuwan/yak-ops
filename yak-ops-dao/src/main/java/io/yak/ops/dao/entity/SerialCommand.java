package io.yak.ops.dao.entity;

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
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_yak_ops_serial_command")
public class SerialCommand {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer workflowInstanceId;

    private Long workflowDefinitionCode;

    private Integer workflowDefinitionVersion;

    private String command;

    private int state;

    private Date createTime;

    private Date updateTime;

}
