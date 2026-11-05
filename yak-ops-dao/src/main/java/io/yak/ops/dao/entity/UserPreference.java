package io.yak.ops.dao.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_yak_ops_user_preference")
public class UserPreference {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer userId;

    private String scene;

    private String itemKey;

    private Boolean favorite;

    private Integer sortOrder;

    private Long useCount;

    private LocalDateTime lastUsedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
