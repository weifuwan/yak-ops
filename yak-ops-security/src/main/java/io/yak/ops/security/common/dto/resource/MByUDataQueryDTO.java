package io.yak.ops.security.common.dto.resource;

import lombok.Data;
/**
 * 按用户查询授权数据的数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class MByUDataQueryDTO {
  /** 用户标识。 */
  private Long userId;
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 展示级别。 */
  private Integer showLevel;
  /** 管控级别。 */
  private Integer controlLevel;
  /** 是否批量查询。 */
  private Boolean batch;

}
