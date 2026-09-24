package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 菜单持久化对象。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_menu")
public class MenuPO extends BasePO {

  /** 与前端路由元数据 id 对应的稳定编码。 */
  private String menuCode;

  /** 菜单显示名称。 */
  private String menuName;

  /** 父菜单编码，根菜单为空。 */
  private String parentCode;

  /** 前端路由路径。 */
  private String routePath;

  /** 前端图标键。 */
  private String iconKey;

  /** 菜单类型：1 目录，2 页面。 */
  private Integer menuType;

  /** 排序号。 */
  private Integer sortOrder;

  /** 是否显示。 */
  private Boolean visible;

  /** 是否启用。 */
  private Boolean active;

  /** 菜单访问隐含授予的读取权限编码。 */
  private String requiredPermissionCode;

  /** 菜单说明。 */
  private String description;
}
