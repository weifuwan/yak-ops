package io.yak.ops.common.bean.vo.security.user;

import lombok.Data;

/** 用户简要信息视图对象。 */
@Data
public class UserBriefVO {
    private Long id;
    private String userName;
    private String realName;
    private Long deptId;
    private String phone;
    private String email;
}
