package io.yak.ops.security.common.entity;

import lombok.Data;

/**
 * 资源类型实体。
 *
 * @author weifuwan
 */
@Data
public class ResourceType {
  /** 实体标识。 */
  private Long id;
  /** 资源类型名称。 */
  private String typeName;

}
