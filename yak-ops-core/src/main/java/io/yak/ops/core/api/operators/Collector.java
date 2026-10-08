
package io.yak.ops.core.api.operators;

/**
 * 接收 Operator 产生的数据，并交给下游执行组件。
 *
 * <p>Collector 由 Runtime 提供和管理。调用方可以在一次处理过程中
 * 同步输出零条、一条或多条记录，但不能缓存 Collector 用于异步输出。
 *
 * <p>下游处理或背压传递失败时允许抛出异常，由 Runtime 负责处理失败与清理资源。
 *
 * @param <T> 输出记录的数据类型
 * @author weifuwan
 */
@FunctionalInterface
public interface Collector<T> {

    /**
     * 输出一条记录。
     *
     * @param record 待输出的记录
     * @throws Exception 下游处理失败
     */
    void collect(T record) throws Exception;
}
