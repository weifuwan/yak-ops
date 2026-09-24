package io.yak.framework.security.common.vo.dept;

import lombok.Data;
/**
 * 部门简要信息视图对象。
 *
 * @author weifuwan
 */
@Data
public class DeptBriefVO {
  /** 主键标识。 */
  private Long id;
  /** 部门名称。 */
  private String deptName;
  /** 父节点标识。 */
  private Long parentId;

}
