package io.yak.ops.security.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志实体。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Oplog extends BaseEntity {
  /** 操作人 IP 地址。 */
  private String operatorIp;
  /** 操作人。 */
  private String operator;
  /** 操作页面。 */
  private String operatePage;
  /** 操作类型。 */
  private String operateType;
  /** 操作目标类型。 */
  private String targetType;
  /** 操作目标。 */
  private String target;
  /** 操作详情。 */
  private String detail;
  /** 操作方法。 */
  private String operationMethods;

}
