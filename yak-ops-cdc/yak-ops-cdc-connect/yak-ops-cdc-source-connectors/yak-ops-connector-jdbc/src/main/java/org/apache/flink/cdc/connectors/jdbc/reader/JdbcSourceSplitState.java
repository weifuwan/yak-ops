package org.apache.flink.cdc.connectors.jdbc.reader;

import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit;

import java.math.BigDecimal;

/** Mutable reader-side progress for one JDBC split. */
public final class JdbcSourceSplitState {

    private final JdbcSourceSplit split;
    private BigDecimal lastReadValue;

    public JdbcSourceSplitState(JdbcSourceSplit split) {
        this.split = split;
        this.lastReadValue = split.getLastReadValue();
    }

    public void setLastReadValue(BigDecimal lastReadValue) {
        this.lastReadValue = lastReadValue;
    }

    public JdbcSourceSplit toSourceSplit() {
        return split.withLastReadValue(lastReadValue);
    }
}
