package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 项目持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_project")
public class ProjectPO extends BasePO {

  /**
   * 项目名称。
   */
  private String projectName;

  /**
   * 项目编码。
   */
  private String projectCode;

  /**
   * 项目描述。
   */
  @ToString.Exclude
  private String description;

  /**
   * 项目是否正在运行。
   */
  private Boolean running;

  /**
   * 所属部门标识。
   */
  private Long deptId;
}