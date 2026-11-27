package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Legacy version-1 checkpoint class.
 *
 * <p>Keep this class and its serialized fields stable so checkpoints written before the JDBC
 * source package reorganization can still be restored. New execution uses the split package.
 */
@Deprecated
public final class JdbcSourceSplit implements SourceSplit, Serializable {

    private static final long serialVersionUID = 1L;

    private final String splitId;
    private final TableId tableId;
    private final Schema schema;
    private final String splitColumn;
    private final BigDecimal lowerBound;
    private final BigDecimal upperBound;
    private final boolean upperInclusive;
    private final BigDecimal lastReadValue;

    public JdbcSourceSplit(
            String splitId,
            TableId tableId,
            Schema schema,
            String splitColumn,
            BigDecimal lowerBound,
            BigDecimal upperBound,
            boolean upperInclusive,
            BigDecimal lastReadValue) {
        this.splitId = splitId;
        this.tableId = tableId;
        this.schema = schema;
        this.splitColumn = splitColumn;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.upperInclusive = upperInclusive;
        this.lastReadValue = lastReadValue;
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public TableId getTableId() {
        return tableId;
    }

    public Schema getSchema() {
        return schema;
    }

    public String getSplitColumn() {
        return splitColumn;
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public boolean isUpperInclusive() {
        return upperInclusive;
    }

    public BigDecimal getLastReadValue() {
        return lastReadValue;
    }
}
