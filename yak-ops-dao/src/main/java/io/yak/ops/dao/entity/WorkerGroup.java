package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("t_yak_ops_worker_group")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerGroup {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String name;

    private String addrList;

    private Date createTime;

    private Date updateTime;

    private String description;

    @TableField(exist = false)
    private boolean systemDefault;

}
