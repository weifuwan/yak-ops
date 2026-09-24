package io.yak.ops.security.common.vo.project;

import lombok.Data;
/**
 * 项目简要信息视图对象。
 *
 * @author weifuwan
 */
@Data
public class ProjectBriefVO {
  /** 主键标识。 */
  private Long id;
  /** 项目编码。 */
  private String projectCode;
  /** 项目名称。 */
  private String projectName;

}
