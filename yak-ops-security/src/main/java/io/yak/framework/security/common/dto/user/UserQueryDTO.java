package io.yak.framework.security.common.dto.user;

import io.yak.framework.security.common.dto.PageParamDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户查询数据传输对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageParamDTO {
  private Long id;
  private String userName;
  private String realName;
}
