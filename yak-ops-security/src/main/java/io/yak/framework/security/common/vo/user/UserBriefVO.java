package io.yak.framework.security.common.vo.user;

import lombok.Data;

import java.util.List;
/**
 * 用户简要信息视图对象。
 *
 * @author weifuwan
 */
@Data
public class UserBriefVO {
  /** 主键标识。 */
  private Long id;
  /** 用户名。 */
  private String userName;
  /** 用户真实姓名。 */
  private String realName;
  /** 部门标识。 */
  private Long deptId;
  /** 手机号码。 */
  private String phone;
  /** 电子邮箱。 */
  private String email;
  /** 角色列表。 */
  private List<String> roleList;

}
