package io.yak.framework.security.common.dto.user;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.framework.security.common.dto.PageParamDTO;
/**
 * 用户查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageParamDTO {
  /** 标识。 */
  private Long id;
  /** 角色标识。 */
  private Long roleId;
  /** 用户名。 */
  private String userName;
  /** 真实姓名。 */
  private String realName;

}
