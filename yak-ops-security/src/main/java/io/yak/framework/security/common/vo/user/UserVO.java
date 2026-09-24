package io.yak.framework.security.common.vo.user;

import lombok.Data;

import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import java.util.Date;
import java.util.List;
/**
 * 用户详情视图对象。
 *
 * @author weifuwan
 */
@Data
public class UserVO {
  /** 主键标识。 */
  private Long id;
  /** 用户名。 */
  private String userName;
  /** 用户真实姓名。 */
  private String realName;
  /** 手机号码。 */
  private String phone;
  /** 电子邮箱。 */
  private String email;
  /** 最后更新时间。 */
  private Date updateTime;
  /** 创建时间。 */
  private Date createTime;
  /** 角色列表。 */
  private List<RoleBriefVO> roleList;
  /** 权限树。 */
  private PermissionTreeVO permissionTreeVO;
  /** 项目列表。 */
  private List<ProjectBriefVO> projectList;

}
