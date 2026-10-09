package io.yak.ops.core.api.connector.sink;

/**
 * 可复用的 Sink 定义。每个执行子任务通过运行时上下文创建独立的 SinkWriter，
 * Sink 本身不得持有某一次执行专属的活动连接或 Writer。
 *
 * <p>Writer 通过 WriterInitContext 获取 TaskInfo 和 Configuration 的防御性副本。
 * Writer 状态恢复由 SupportsWriterState 等可选能力负责；基础 Sink 不承诺事务提交或 Exactly-once。
 *
 * @param <T> 输入记录类型
 */
@FunctionalInterface
public interface Sink<T> {

    /** 为当前执行子任务创建 Writer；禁止返回 null 或共享活动 Writer。 */
    SinkWriter<T> createWriter(WriterInitContext context) throws Exception;
}
