package io.yak.framework.security.common.dto.project;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.framework.security.common.dto.PageParamDTO;
/**
 * 项目查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectQueryDTO extends PageParamDTO {
  /** 项目名称。 */
  private String projectName;
  /** 项目编码。 */
  private String projectCode;
  /** 项目负责人用户名。 */
  private String chargeUsername;
  /** 所属部门标识。 */
  private Long deptId;
  /** 项目是否正在运行。 */
  private Boolean running;

}
