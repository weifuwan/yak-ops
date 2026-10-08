package io.yak.ops.core.api.connector.sink;

/**
 * 单个执行子任务中的实际写入实例。
 *
 * <p>Runtime 必须保证同一个 Writer 的写入、Flush 和关闭方法不会并发调用。
 * 写入方法可以使用缓冲区，但不得丢弃尚未成功刷出的记录。
 *
 * <p>本协议没有定义事务提交、状态恢复或 Exactly-once 保证。
 * flush() 的成功仅表示 Writer 按自身写入协议完成了相应数据的刷出处理。
 *
 * @param <T> 输入记录类型
 * @author weifuwan
 */
public interface SinkWriter<T> extends AutoCloseable {

    /**
     * 写入一条记录，具体 Sink 可以立即写入或暂存到缓冲区。
     *
     * @param element 输入记录
     * @throws Exception 写入失败
     */
    void write(T element) throws Exception;

    /**
     * 刷出 Writer 中尚未完成的缓冲数据。
     *
     * <p>Runtime 在 Checkpoint 对齐阶段或有界输入正常结束时调用此方法。
     * 仅正常结束时 endOfInput 为 true；Checkpoint 时为 false。
     * 不能将此方法的成功视为端到端 Exactly-once 提交证明。
     *
     * @param endOfInput 是否为正常输入结束时的最终 Flush
     * @throws Exception 刷出失败
     */
    void flush(boolean endOfInput) throws Exception;

    /**
     * 释放连接、缓冲区等资源，正常结束、失败与取消后都应尽力调用。
     *
     * <p>close() 不隐含成功 Flush 或事务提交；Runtime 必须在正常结束前
     * 显式调用 flush(true)。
     *
     * @throws Exception 资源释放失败
     */
    @Override
    void close() throws Exception;
}
