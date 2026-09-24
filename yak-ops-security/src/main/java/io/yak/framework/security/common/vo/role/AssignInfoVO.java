package io.yak.framework.security.common.vo.role;

import lombok.Data;
/**
 * 分配信息视图对象。
 *
 * @author weifuwan
 */
@Data
public class AssignInfoVO {
  /** 主键标识。 */
  private Long id;
  /** 名称。 */
  private String name;
  /** 是否已分配。 */
  private Boolean has;

}
