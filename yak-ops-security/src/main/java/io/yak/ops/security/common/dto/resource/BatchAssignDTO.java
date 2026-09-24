package io.yak.ops.security.common.dto.resource;

import lombok.Data;

import java.util.List;
/**
 * 资源批量分配数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class BatchAssignDTO {
  /** 用户标识列表。 */
  private List<Long> userIdList;
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 资源标识列表。 */
  private List<Long> idList;
  /** 管控级别。 */
  private Integer controlLevel;
  /** 分配标记。 */
  private Boolean assignFlag;

}
