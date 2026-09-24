package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户资源权限持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_user_resource")
public class UserResourcePO extends BasePO {

  /**
   * 用户标识。
   */
  private Long userId;

  /**
   * 项目标识。
   */
  private Long projectId;

  /**
   * 资源类型标识。
   */
  private Long resourceTypeId;

  /**
   * 资源标识。
   */
  private Long resourceId;

  /**
   * 资源控制级别。
   */
  private Integer controlLevel;
}