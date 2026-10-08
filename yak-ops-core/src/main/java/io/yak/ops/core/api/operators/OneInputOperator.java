package io.yak.ops.core.api.operators;

/**
 * 处理单路输入数据的运行时 Operator。
 *
 * <p>Operator 每个并行子任务持有独立实例。Runtime 必须保证同一实例的
 * 生命周期回调与数据处理方法不被并发调用。
 *
 * <p>Operator 负责数据转换，不负责线程调度、Checkpoint 协调和作业状态持久化。
 *
 * @param <IN> 输入记录的数据类型
 * @param <OUT> 输出记录的数据类型
 * @author weifuwan
 */
public interface OneInputOperator<IN, OUT> extends AutoCloseable {

    /**
     * 在处理第一条记录之前初始化算子。
     *
     * @throws Exception 初始化失败
     */
    default void open() throws Exception {}

    /**
     * 处理一条输入记录，可以输出零条、一条或多条记录。
     *
     * <p>输出必须通过 Collector 同步发送，不得保存 Collector 后异步使用。
     * 任意处理异常应向上抛出，由 Runtime 决定作业失败与后续清理。
     *
     * @param element 输入记录
     * @param output 下游输出通道
     * @throws Exception 数据处理或下游输出失败
     */
    void processElement(IN element, Collector<OUT> output) throws Exception;

    /**
     * 输入正常结束后调用，可通过 Collector 输出剩余缓冲数据。
     *
     * <p>作业失败或被强制取消时不应调用此方法；此方法不代表事务提交。
     *
     * @param output 下游输出通道
     * @throws Exception 剩余数据处理失败
     */
    default void finish(Collector<OUT> output) throws Exception {}

    /**
     * 释放 Operator 持有的资源，正常结束、失败与取消后均应尝试调用。
     *
     * <p>不允许在此方法输出记录；即使 open() 或 processElement() 失败，
     * Runtime 也应尽力调用 close()。
     *
     * @throws Exception 资源释放失败
     */
    @Override
    default void close() throws Exception {}
}
