package io.yak.ops.core.api.connector.source;

/**
 * SourceReader 向 Runtime 发送记录的输出端口。
 *
 * <p>Runtime 负责背压和记录向下游的转发。当前只定义记录输出，
 * 事件时间、Watermark 和 Split 专属输出以后单独扩展。
 *
 * @param <T> 数据记录类型
 * @author weifuwan
 */
@FunctionalInterface
public interface ReaderOutput<T> {

    /** 发出一条记录；背压或下游失败可以通过异常传递。 */
    void collect(T record) throws Exception;
}
