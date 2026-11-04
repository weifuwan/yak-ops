package io.yak.ops.dao.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ds_session")
public class Session {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private int userId;

    private Date lastLoginTime;

    // We will not bind session with ip
    @Deprecated
    private String ip;

}
