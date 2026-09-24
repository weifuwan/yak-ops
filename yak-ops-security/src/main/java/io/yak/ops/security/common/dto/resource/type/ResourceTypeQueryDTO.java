package io.yak.ops.security.common.dto.resource.type;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.ops.security.common.dto.PageParamDTO;
import io.yak.ops.security.common.dto.resource.MByRQueryDTO;

/**
 * 资源类型查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceTypeQueryDTO extends PageParamDTO {
  /** 资源类型名称。 */
  private String typeName;

  public ResourceTypeQueryDTO(MByRQueryDTO queryDTO) {
    this.setPage(queryDTO.getPage());
    this.setSize(queryDTO.getSize());
    this.typeName = queryDTO.getName();
  }

}
