package io.yak.framework.security.common.dto.oplog;

import io.yak.framework.security.common.dto.PageParamDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志查询数据传输对象。
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OplogQueryDTO extends PageParamDTO {
  /** 操作类型。 */
  private String operateType;
  /** 操作页面。 */
  private String operatePage;
  /** 操作详情。 */
  private String detail;
  /** 操作人。 */
  private String operator;
  /** 操作人 IP 地址。 */
  private String operatorIp;
  /** 操作目标。 */
  private String target;
  /** 操作目标类型。 */
  private String targetType;
  /** 操作方法。 */
  private String operationMethods;
  /** 开始时间，Unix 毫秒时间戳。 */
  private Long startTime;
  /** 结束时间，Unix 毫秒时间戳。 */
  private Long endTime;
}
