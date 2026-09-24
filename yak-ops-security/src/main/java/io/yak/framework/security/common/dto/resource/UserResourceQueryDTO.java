package io.yak.framework.security.common.dto.resource;

import lombok.Data;

/**
 * 用户资源查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class UserResourceQueryDTO {
  /** 管控级别。 */
  private Integer controlLevel;
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 资源标识。 */
  private Long resourceId;

  public UserResourceQueryDTO(int controlLevel, Long projectId,
                              Long resourceTypeId, Long resourceId) {
    this.controlLevel = controlLevel;
    this.projectId = projectId;
    this.resourceTypeId = resourceTypeId;
    this.resourceId = resourceId;
  }

  public UserResourceQueryDTO(int controlLevel, Long projectId,
                              Long resourceTypeId) {
    this.controlLevel = controlLevel;
    this.projectId = projectId;
    this.resourceTypeId = resourceTypeId;
    this.resourceId = null;
  }

  public UserResourceQueryDTO(int controlLevel, Long projectId) {
    this.controlLevel = controlLevel;
    this.projectId = projectId;
    this.resourceTypeId = null;
    this.resourceId = null;
  }

  public static UserResourceQueryDTO getOpenViewPermissionControlQueryEntity() {
    return new UserResourceQueryDTO(0, 0L, 0L, 0L);
  }

}
