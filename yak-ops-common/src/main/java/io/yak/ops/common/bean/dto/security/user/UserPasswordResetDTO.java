package io.yak.ops.common.bean.dto.security.user;

import lombok.Data;

/**
 * 管理员重置用户密码请求。
 *
 * @author weifuwan
 */
@Data
public class UserPasswordResetDTO {

    /**
     * 新密码。
     */
    private String password;
}
