package io.yak.framework.security.common.entity;

import lombok.Data;

/**
 * 权限实体。
 *
 * @author weifuwan
 */
@Data
public class Permission {
  /** 实体标识。 */
  private Long id;
  /** 权限编码。 */
  private String permissionCode;
  /** 权限名称。 */
  private String permissionName;
  /** 父级标识。 */
  private Long parentId;
  /** 叶节点标记。 */
  private Boolean leaf;
  /** 层级。 */
  private Integer level;
  /** 描述。 */
  private String description;
  /** 该权限所属的稳定菜单编码；为空表示不隶属于具体页面。 */
  private String menuCode;

  /** Parent permission code, used only while synchronizing declarations. */
  private transient String parentCode;
  /** Whether this permission can currently grant access. */
  private Boolean active;
  /** Whether this row is managed by declarative registration. */
  private Boolean declared;

}
