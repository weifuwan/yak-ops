package io.yak.ops.common.bean.vo.security.user;

import java.util.Date;
import lombok.Data;

/** 用户详情视图对象。 */
@Data
public class UserVO {
  private Long id;
  private String userName;
  private String realName;
  private String phone;
  private String email;
  private Date updateTime;
  private Date createTime;
}
