package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 角色持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_role")
public class RolePO extends BasePO {

  /**
   * 角色编码。
   */
  private String roleCode;

  /**
   * 角色名称。
   */
  private String roleName;

  /**
   * 角色描述。
   */
  private String description;

  /**
   * 最后修改人。
   */
  private String lastReviser;
}