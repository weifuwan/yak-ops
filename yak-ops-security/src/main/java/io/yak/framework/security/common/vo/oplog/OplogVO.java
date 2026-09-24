package io.yak.framework.security.common.vo.oplog;

import lombok.Data;

import java.util.Date;

/**
 * 操作日志视图对象。
 *
 * @author weifuwan
 */
@Data
public class OplogVO {
  /** 主键标识。 */
  private Long id;
  /** 操作人 IP 地址。 */
  private String operatorIp;
  /** 操作人。 */
  private String operator;
  /** 操作页面。 */
  private String operatePage;
  /** 操作类型。 */
  private String operateType;
  /** 操作方法。 */
  private String operationMethods;
  /** 操作目标。 */
  private String target;
  /** 操作目标类型。 */
  private String targetType;
  /** 操作详情。 */
  private String detail;
  /** 创建时间。 */
  private Date createTime;
  /** 最后更新时间。 */
  private Date updateTime;
}
