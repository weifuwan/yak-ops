package io.yak.framework.security.common.vo.permission;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限树节点视图对象。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PermissionTreeVO {

  /** 主键标识。 */
  private Long id;

  /** 是否已分配。 */
  private Boolean has;

  /** 权限编码。 */
  private String permissionCode;

  /** 权限名称。 */
  private String permissionName;

  /** 父节点标识。 */
  private Long parentId;

  /** 是否为叶子节点。 */
  private Boolean leaf;

  /** 权限描述。 */
  private String description;

  /** 是否启用。 */
  private Boolean active;

  /** 是否为声明式权限。 */
  private Boolean declared;

  /** 所属稳定菜单编码。 */
  private String menuCode;

  /** ROOT、MENU_GROUP、MENU、PERMISSION_GROUP 或 ACTION。 */
  private String nodeType;

  /** 子节点列表。 */
  private List<PermissionTreeVO> childList;
}
