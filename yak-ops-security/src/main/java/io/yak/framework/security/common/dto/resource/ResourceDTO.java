package io.yak.framework.security.common.dto.resource;

import lombok.Data;

/**
 * 资源数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class ResourceDTO {
  /** 资源标识。 */
  private Long resourceId;
  /** 资源名称。 */
  private String resourceName;
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;

  public ResourceDTO() {}

  public ResourceDTO(Long projectId, Long resourceTypeId,
                     Long resourceId) {
    this.projectId = projectId;
    this.resourceTypeId = resourceTypeId;
    this.resourceId = resourceId;
  }

}
