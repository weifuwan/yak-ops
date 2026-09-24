package io.yak.ops.security.common.entity.project;

import lombok.Data;

/**
 * 项目简要信息实体。
 *
 * @author weifuwan
 */
@Data
public class ProjectBrief {
  /** 实体标识。 */
  private Long id;
  /** 项目名称。 */
  private String projectName;
  /** 项目编码。 */
  private String projectCode;

}
