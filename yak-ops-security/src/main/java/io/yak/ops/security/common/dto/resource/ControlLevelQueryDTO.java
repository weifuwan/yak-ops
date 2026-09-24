package io.yak.ops.security.common.dto.resource;

import lombok.Data;
/**
 * 管控级别查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class ControlLevelQueryDTO {
  /** 用户标识。 */
  private Long userId;
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 资源标识。 */
  private Long resourceId;

}
