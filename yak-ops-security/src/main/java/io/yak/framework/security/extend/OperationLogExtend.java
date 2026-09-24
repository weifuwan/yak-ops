package io.yak.framework.security.extend;

/**
 * 安全操作日志扩展点。
 *
 * <p>宿主应用可以通过实现该接口，将安全相关操作写入数据库、
 * 消息队列或外部审计系统。</p>
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface OperationLogExtend {

  /**
   * 记录操作日志。
   *
   * <p>{@code detail} 中不应记录密码、Token、Cookie 等敏感信息。</p>
   *
   * @param operator 操作人
   * @param operation 操作类型
   * @param target 操作目标
   * @param detail 操作详情
   */
  void record(
          String operator,
          String operation,
          String target,
          String detail);
}