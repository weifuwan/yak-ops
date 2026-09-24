package io.yak.framework.security.common.dto.project;

import lombok.Data;

/**
 * 项目状态数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class ProjectStatusDTO {

  /**
   * 项目是否运行中。
   */
  private Boolean running;
}
