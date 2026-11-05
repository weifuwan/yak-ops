package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_version")
public class YakOpsVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String version;

}
