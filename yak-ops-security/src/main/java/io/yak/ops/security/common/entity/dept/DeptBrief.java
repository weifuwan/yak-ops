package io.yak.ops.security.common.entity.dept;

import lombok.Data;

/**
 * 部门简要信息实体。
 *
 * @author weifuwan
 */
@Data
public class DeptBrief {
  /** 实体标识。 */
  private Long id;
  /** 部门名称。 */
  private String deptName;
  /** 叶节点标记。 */
  private Boolean leaf;
  /** 层级。 */
  private Integer level;
  /** 父级标识。 */
  private Long parentId;

}
