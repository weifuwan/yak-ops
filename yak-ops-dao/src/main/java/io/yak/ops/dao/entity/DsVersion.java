package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_version")
public class DsVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String version;

}
