package io.yak.ops.security.model;

import java.util.Date;
import lombok.Data;
import lombok.ToString;

/** Internal user account model used by the Security runtime. */
@Data
public class UserAccount {

    private Long id;
    private Date createTime;
    private Date updateTime;
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
