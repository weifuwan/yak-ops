package io.yak.framework.security.common.entity.project;

import io.yak.framework.security.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目实体。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Project extends BaseEntity {
  /** 项目名称。 */
  private String projectName;
  /** 项目编码。 */
  private String projectCode;
  /** 描述。 */
  private String description;
  /** 运行标记。 */
  private Boolean running;
  /** 部门标识。 */
  private Long deptId;

}
