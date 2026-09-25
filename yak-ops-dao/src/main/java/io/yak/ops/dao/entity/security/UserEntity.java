package io.yak.ops.dao.entity.security;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_security_user 表，承载登录和用户管理所需的持久化状态。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
@ToString
@TableName("yak_security_user")
public class UserEntity {

    /** 用户主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用级数据隔离键，由 Security MyBatis 运行时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private String appName;

    /** 用户账号，在同一应用的未删除用户中唯一。 */
    private String userName;

    /** 单向哈希后的登录密码。 */
    @ToString.Exclude
    private String pw;

    /** 密码盐兼容字段，当前密码编码流程不依赖该字段。 */
    @ToString.Exclude
    private String salt;

    /** 用户真实姓名。 */
    private String realName;

    /** 用户手机号码。 */
    private String phone;

    /** 用户电子邮箱。 */
    private String email;

    /** 部门 ID，仅作为用户资料字段保留，不创建数据库外键。 */
    private Long deptId;

    /** 用户状态：1 正常，2 禁用。 */
    private Integer status = 1;

    /** 逻辑删除标记：0 未删除，1 已删除。 */
    @TableLogic(value = "0", delval = "1")
    private int isDelete = 0;

    /** 用户记录创建时间。 */
    private LocalDateTime createTime;

    /** 用户记录最后更新时间。 */
    private LocalDateTime updateTime;
}
