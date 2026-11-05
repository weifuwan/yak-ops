package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_relation_datasource_user")
public class DatasourceUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private int userId;

    private int datasourceId;

    /**
     * permission
     */
    private int perm;
    private Date createTime;

    private Date updateTime;
}
