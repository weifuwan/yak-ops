package io.yak.ops.common.bean.dto.security.user;

import lombok.Data;

/** 用户数据传输对象。 */
@Data
public class UserDTO {
    private String userName;
    private String pw;
    private String realName;
    private String phone;
    private String email;
}
