package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 系统配置持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_config")
public class ConfigPO extends BasePO {

  /**
   * 配置主键。
   */
  @TableId(type = IdType.AUTO)
  private Long id;

  /**
   * 配置分组。
   */
  private String valueGroup;

  /**
   * 配置项名称。
   */
  private String valueName;

  /**
   * 配置项值。
   */
  private String value;

  /**
   * 配置状态。
   */
  private Integer status;

  /**
   * 配置备注。
   */
  private String memo;

  /**
   * 最后操作人。
   */
  private String operator;
}