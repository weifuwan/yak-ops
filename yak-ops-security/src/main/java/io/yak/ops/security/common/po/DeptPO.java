package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 部门持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_dept")
public class DeptPO extends BasePO {

  /**
   * 部门名称。
   */
  private String deptName;

  /**
   * 部门描述。
   */
  private String description;

  /**
   * 上级部门标识。
   */
  private Long parentId;

  /**
   * 是否为叶子部门。
   */
  private Boolean leaf;

  /**
   * 部门层级。
   */
  private Integer level;
}