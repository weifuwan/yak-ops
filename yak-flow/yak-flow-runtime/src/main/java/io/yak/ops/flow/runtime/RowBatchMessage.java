package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.YakRow;
import java.util.List;
import java.util.Objects;

/**
 * Local Execution Engine Channel 中的一批行数据。
 *
 * @param rows 不为空的 YakRow 批次
 * @author weifuwan
 * @since 2026-09-27
 */
record RowBatchMessage(List<YakRow> rows) implements ChannelMessage {

    RowBatchMessage {
        Objects.requireNonNull(rows, "rows must not be null");
        rows = List.copyOf(rows);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows must not be empty");
        }
    }
}
