package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 操作日志持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_oplog")
public class OplogPO extends BasePO {

  /**
   * 操作人 IP 地址。
   */
  private String operatorIp;

  /**
   * 操作人。
   */
  private String operator;

  /**
   * 操作类型。
   */
  private String operateType;

  /**
   * 操作页面。
   */
  private String operatePage;

  /**
   * 操作方法。
   *
   * <p>对应数据库字段 {@code operation_methods}。</p>
   */
  private String operationMethods;

  /**
   * 操作目标。
   */
  private String target;

  /**
   * 操作目标类型。
   */
  private String targetType;

  /**
   * 操作详情。
   *
   * <p>操作详情可能包含较长内容或敏感信息，
   * 因此不输出到 {@code toString()}。</p>
   */
  @ToString.Exclude
  private String detail;
}