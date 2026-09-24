package io.yak.ops.security.common.dto.user;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.yak.ops.security.common.dto.PageParamDTO;
import io.yak.ops.security.common.dto.resource.MByUQueryDTO;

/**
 * 用户简要查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserBriefQueryDTO extends PageParamDTO {
  /** 用户名。 */
  private String userName;
  /** 真实姓名。 */
  private String realName;
  /** 所属部门标识。 */
  private Long deptId;
  /** 部门名称。 */
  private String deptName;

  public UserBriefQueryDTO(MByUQueryDTO queryDTO) {
    this.setPage(queryDTO.getPage());
    this.setSize(queryDTO.getSize());
    this.userName = queryDTO.getUserName();
    this.realName = queryDTO.getRealName();
    this.deptId = queryDTO.getDeptId();
    this.deptName = queryDTO.getDeptName();
  }

}
