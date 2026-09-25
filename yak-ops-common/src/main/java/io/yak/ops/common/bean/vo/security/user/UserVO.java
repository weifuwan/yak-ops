package io.yak.ops.common.bean.vo.security.user;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户详情视图对象。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
public class UserVO {

    /** 用户主键 ID。 */
    private Long id;

    /** 用户账号。 */
    private String userName;

    /** 用户真实姓名。 */
    private String realName;

    /** 已按接口需要处理的手机号码。 */
    private String phone;

    /** 用户电子邮箱。 */
    private String email;

    /** 用户记录最后更新时间。 */
    private LocalDateTime updateTime;

    /** 用户记录创建时间。 */
    private LocalDateTime createTime;
}
