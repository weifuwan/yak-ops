package io.yak.ops.flow.api.sink;

import io.yak.ops.flow.api.row.YakTableSchema;

/**
 * YakFlow 目标端契约，批量行和 CDC changelog 均通过同一 SinkWriter 写入。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface Sink {

    /**
     * 根据 Source 的逻辑表结构创建独立 Writer。
     *
     * @param schema 输入表结构
     * @return Sink Writer
     */
    SinkWriter createWriter(YakTableSchema schema);
}
