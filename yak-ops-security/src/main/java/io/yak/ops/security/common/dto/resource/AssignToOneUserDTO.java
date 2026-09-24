package io.yak.ops.security.common.dto.resource;

import lombok.Data;

import java.util.List;
/**
 * 用户资源分配数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class AssignToOneUserDTO {
  /** 用户标识。 */
  private Long userId;
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 资源标识列表。 */
  private List<Long> idList;
  /** 排除的资源标识列表。 */
  private List<Long> excludeIdList;
  /** 管控级别。 */
  private Integer controlLevel;

}
