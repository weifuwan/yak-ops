package io.yak.framework.security.common.vo.resource;

import lombok.Data;
/**
 * 按资源查询的用户授权数据视图对象。
 *
 * @author weifuwan
 */
@Data
public class MByRDataVO {
  /** 用户标识。 */
  private Long userId;
  /** 用户名。 */
  private String userName;
  /** 用户真实姓名。 */
  private String realName;
  /** 授权级别。 */
  private Integer hasLevel;

}
