package io.yak.framework.security.common.entity;

import lombok.Data;

/**
 * 操作日志附加信息实体。
 *
 * @author weifuwan
 */
@Data
public class OplogExtra {
  /** 实体标识。 */
  private Long id;
  /** 附加信息。 */
  private String info;
  /** 类型。 */
  private Integer type;

}
