package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户项目关联持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_user_project")
public class UserProjectPO extends BasePO {

  /**
   * 用户标识。
   */
  private Long userId;

  /**
   * 项目中的用户类型。
   */
  private Integer userType;

  /**
   * 项目标识。
   */
  private Long projectId;
}