package io.yak.ops.security.common.dto.resource;

import lombok.Data;

/**
 * 资源查看权限控制状态数据传输对象。
 */
@Data
public class ResourceViewControlDTO {

  /** 是否开启资源查看权限控制。 */
  private Boolean enabled;
}
