package io.yak.ops.flow.api.sink;

import io.yak.ops.flow.api.row.YakRow;
import java.util.List;

/**
 * 将 YakRow 写入目标端的任务级 Writer，具体 Connector 自行解释 RowKind 并决定批次策略。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface SinkWriter extends AutoCloseable {

    /**
     * 初始化 Writer 持有的目标端资源。
     *
     * @throws Exception 初始化失败
     */
    default void open() throws Exception {}

    /**
     * 写入一批 YakRow；调用成功只表示该批次被 Writer 接受，不隐含 exactly-once 承诺。
     *
     * @param rows 待写入数据
     * @throws Exception 写入失败
     */
    void write(List<YakRow> rows) throws Exception;

    /**
     * 把 Writer 当前缓冲的数据刷新到目标端。
     *
     * @throws Exception 刷新失败
     */
    default void flush() throws Exception {}

    @Override
    void close() throws Exception;
}
