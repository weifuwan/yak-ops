package io.yak.framework.security.common.dto.config;

import lombok.Data;
/**
 * 配置数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class ConfigDTO {
  /** 标识。 */
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
  /** 操作人。 */
  private String operator;

  public ConfigDTO() {}

  public ConfigDTO(Long id, String valueGroup, String valueName,
                   String value, Integer status, String memo, String operator) {
    this.id = id;
    this.valueGroup = valueGroup;
    this.valueName = valueName;
    this.value = value;
    this.status = status;
    this.memo = memo;
    this.operator = operator;
  }
}
