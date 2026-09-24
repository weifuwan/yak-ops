package io.yak.framework.security.common.dto.resource;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.framework.security.common.dto.PageParamDTO;
/**
 * 按用户查询授权的数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MByUQueryDTO extends PageParamDTO {
  /** 所属部门标识。 */
  private Long deptId;
  /** 部门名称。 */
  private String deptName;
  /** 用户名。 */
  private String userName;
  /** 真实姓名。 */
  private String realName;

}
