package io.yak.ops.dao.entity.security;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Yak Security user persistence entity. */
@Getter
@Setter
@ToString
@TableName("yak_security_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private String appName;

    private Date createTime;
    private Date updateTime;

    @TableLogic(value = "0", delval = "1")
    private int isDelete = 0;

    private String userName;

    @ToString.Exclude
    private String pw;

    @ToString.Exclude
    private String salt;

    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Integer status = 1;
}
