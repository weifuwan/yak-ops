package io.yak.ops.core.api.connector.sink;

/**
 * Sink 组件的定义与 Writer 创建入口。
 *
 * <p>Sink 可以被同一个 Pipeline 的多次执行复用，但不得作为共享的可变写入实例。
 * 连接、缓冲区和其他运行资源应由新创建的 SinkWriter 独立持有。
 *
 * <p>本阶段只约定本地创建方式；运行上下文、Writer 状态恢复和分布式序列化
 * 将在相关执行协议确定后再设计。
 *
 * @param <T> Sink 消费的记录类型
 * @author weifuwan
 */
@FunctionalInterface
public interface Sink<T> {

    /**
     * 为一个执行子任务创建独立的 SinkWriter。
     *
     * <p>每次调用必须返回新的非 null Writer，不得复用已运行或已关闭的实例。
     * Writer 的生命周期由 Runtime 管理。
     *
     * @return 新创建的 Writer
     * @throws Exception Writer 创建失败
     */
    SinkWriter<T> createWriter() throws Exception;
}
