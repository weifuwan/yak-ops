package io.yak.framework.security.common.vo.role;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import java.util.Date;
import java.util.List;
/**
 * 角色详情视图对象。
 *
 * @author weifuwan
 */
@Data
public class RoleVO {
  /** 主键标识。 */
  private Long id;
  /** 角色名称。 */
  private String roleName;
  /** 角色编码。 */
  private String roleCode;
  /** 描述信息。 */
  private String description;
  /** 已授权用户数量。 */
  private Integer authedUserCnt;
  /** 已授权用户名列表。 */
  private List<String> authedUsers;
  /** 最后修改人。 */
  private String lastReviser;
  /** 创建时间。 */
  private Date createTime;
  /** 最后更新时间。 */
  private Date updateTime;
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  /** 权限树。 */
  private PermissionTreeVO permissionTreeVO;

}
