package io.yak.framework.security.common.vo.resource;

import lombok.Data;
/**
 * 资源类型视图对象。
 *
 * @author weifuwan
 */
@Data
public class ResourceTypeVO {
  /** 主键标识。 */
  private Long id;
  /** 资源类型名称。 */
  private String typeName;

}
