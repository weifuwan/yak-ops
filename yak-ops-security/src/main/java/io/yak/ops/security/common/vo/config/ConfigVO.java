package io.yak.ops.security.common.vo.config;

import lombok.Data;

import java.util.Date;
/**
 * 系统配置视图对象。
 *
 * @author weifuwan
 */
@Data
public class ConfigVO {
  /** 主键标识。 */
  private Long id;
  /** 配置分组。 */
  private String valueGroup;
  /** 配置名称。 */
  private String valueName;
  /** 配置值。 */
  private String value;
  /** 状态。 */
  private Integer status;
  /** 备注。 */
  private String memo;
  /** 创建时间。 */
  private Date createTime;
  /** 最后更新时间。 */
  private Date updateTime;
  /** 操作人。 */
  private String operator;

  public ConfigVO() {}

  public ConfigVO(Long id, String valueGroup, String valueName, String value,
                  Integer status, String memo, Date createTime, Date updateTime,
                  String operator) {
    this.id = id;
    this.valueGroup = valueGroup;
    this.valueName = valueName;
    this.value = value;
    this.status = status;
    this.memo = memo;
    this.createTime = createTime;
    this.updateTime = updateTime;
    this.operator = operator;
  }
}
