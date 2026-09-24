package io.yak.framework.security.extend.impl;

import io.yak.framework.security.extend.OperationLogExtend;

/**
 * 空操作日志实现。
 *
 * <p>宿主应用未提供操作日志实现时，不执行任何日志持久化操作。</p>
 *
 * @author weifuwan
 */
public class NoOpOperationLogExtend
        implements OperationLogExtend {

  /**
   * 不执行任何操作。
   *
   * @param operator 操作人
   * @param operation 操作类型
   * @param target 操作目标
   * @param detail 操作详情
   */
  @Override
  public void record(
          String operator,
          String operation,
          String target,
          String detail) {
    // No operation.
  }
}