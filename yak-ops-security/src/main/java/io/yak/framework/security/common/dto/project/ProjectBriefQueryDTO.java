package io.yak.framework.security.common.dto.project;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.framework.security.common.dto.PageParamDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
/**
 * 项目简要查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectBriefQueryDTO extends PageParamDTO {
  /** 项目名称。 */
  private String projectName;

  public ProjectBriefQueryDTO(MByRQueryDTO queryDTO) {
    this.setPage(queryDTO.getPage());
    this.setSize(queryDTO.getSize());
    this.projectName = queryDTO.getName();
  }

}
