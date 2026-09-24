package io.yak.ops.security.common.entity.dept;

import lombok.Data;

/**
 * 部门实体。
 *
 * @author weifuwan
 */
@Data
public class Dept {
  /** 实体标识。 */
  private Long id;
  /** 部门名称。 */
  private String deptName;
  /** 父级标识。 */
  private Long parentId;
  /** 叶节点标记。 */
  private Boolean leaf;
  /** 层级。 */
  private Integer level;
  /** 描述。 */
  private String description;

}
