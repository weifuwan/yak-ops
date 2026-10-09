package io.yak.ops.flow.connector.cdc.mysql.source;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import io.yak.ops.flow.api.source.SourceSplitEnumerator;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.Optional;

/**
 * MySQL CDC 单表 split 枚举器；分片本身持续运行，因此分配完成不代表 Source 结束。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class MySqlCdcSplitEnumerator implements SourceSplitEnumerator<MySqlCdcSplit> {

    private final DataSourceTablePath table;
    private boolean assigned;

    MySqlCdcSplitEnumerator(DataSourceTablePath table) {
        this.table = table;
    }

    @Override
    public Optional<MySqlCdcSplit> nextSplit() {
        if (assigned) return Optional.empty();
        assigned = true;
        return Optional.of(new MySqlCdcSplit(table));
    }

    @Override
    public boolean isFinished() {
        return assigned;
    }

    @Override
    public CheckpointState snapshotState(long checkpointId) {
        return new MySqlCdcEnumeratorState(assigned);
    }

    @Override
    public void restore(CheckpointState state) {
        if (!(state instanceof MySqlCdcEnumeratorState cdcState)) {
            throw new IllegalArgumentException("MySQL CDC Enumerator checkpoint state 类型不匹配");
        }
        assigned = cdcState.assigned();
    }
}
