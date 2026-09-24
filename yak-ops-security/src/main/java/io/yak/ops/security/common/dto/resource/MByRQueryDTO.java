package io.yak.ops.security.common.dto.resource;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.ops.security.common.dto.PageParamDTO;
/**
 * 按资源查询授权的数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MByRQueryDTO extends PageParamDTO {
  /** 项目标识。 */
  private Long projectId;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 展示级别。 */
  private Integer showLevel;
  /** 名称。 */
  private String name;

}
