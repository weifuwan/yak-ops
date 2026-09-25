package io.yak.ops.security.model;

import io.yak.ops.common.enums.security.user.UserStatus;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * Security 运行时使用的内部用户账号模型。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
public class UserAccount {

    /** 用户主键 ID。 */
    private String id;

    /** 用户记录创建时间。 */
    private LocalDateTime createTime;

    /** 用户记录最后更新时间。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记，false 表示当前账号有效。 */
    private Boolean isDelete = false;

    /** 用户登录账号。 */
    private String userName;

    /** 持久化后的密码摘要，不得写入日志或对外返回。 */
    @ToString.Exclude
    private String pw;

    /** 历史兼容的密码盐字段，不得写入日志或对外返回。 */
    @ToString.Exclude
    private String salt;

    /** 用户真实姓名。 */
    private String realName;

    /** 用户联系电话。 */
    private String phone;

    /** 用户电子邮箱。 */
    private String email;

    /** 历史兼容的部门 ID；当前 Security 不提供部门授权能力。 */
    private Long deptId;

    /** 用户账号状态，默认启用。 */
    private UserStatus status = UserStatus.ACTIVE;
}
