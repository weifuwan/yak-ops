package io.yak.ops.security.common.dto.resource;

import lombok.Data;
/**
 * 按资源查询授权数据的数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class MByRDataQueryDTO {
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 资源标识。 */
  private Long resourceId;
  /** 管控级别。 */
  private Integer controlLevel;
  /** 是否批量查询。 */
  private Boolean batch;

}
