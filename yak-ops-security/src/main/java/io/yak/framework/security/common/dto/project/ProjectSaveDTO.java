package io.yak.framework.security.common.dto.project;

import lombok.Data;

import java.util.List;
/**
 * 项目保存数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class ProjectSaveDTO {
  /** 标识。 */
  private Long id;
  /** 项目名称。 */
  private String projectName;
  /** 用户标识列表。 */
  private List<Long> userIdList;
  /** 项目负责人标识列表。 */
  private List<Long> ownerIdList;
  /** 描述。 */
  private String description;
  /** 项目是否正在运行。 */
  private Boolean running;
  /** 所属部门标识。 */
  private Long deptId;

}
