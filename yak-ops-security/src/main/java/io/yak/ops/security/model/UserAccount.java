package io.yak.ops.security.model;

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

    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDelete = false;
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
