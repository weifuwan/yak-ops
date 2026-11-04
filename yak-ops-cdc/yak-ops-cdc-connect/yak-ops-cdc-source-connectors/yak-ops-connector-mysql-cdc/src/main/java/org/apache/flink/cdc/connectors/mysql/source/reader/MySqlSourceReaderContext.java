package org.apache.flink.cdc.connectors.mysql.source.reader;

import org.apache.flink.api.connector.source.SourceReaderContext;

/**
 * A wrapper class that wraps {@link SourceReaderContext} for sharing message between {@link
 * MySqlSourceReader} and {@link MySqlSplitReader}.
 */
public class MySqlSourceReaderContext {

    private final SourceReaderContext sourceReaderContext;
    private volatile boolean isBinlogSplitReaderSuspended;
    private volatile boolean hasAssignedBinlogSplit;

    public MySqlSourceReaderContext(final SourceReaderContext sourceReaderContext) {
        this.sourceReaderContext = sourceReaderContext;
        this.isBinlogSplitReaderSuspended = false;
        this.hasAssignedBinlogSplit = false;
    }

    public SourceReaderContext getSourceReaderContext() {
        return sourceReaderContext;
    }

    public boolean isBinlogSplitReaderSuspended() {
        return isBinlogSplitReaderSuspended;
    }

    public void suspendBinlogSplitReader() {
        this.isBinlogSplitReaderSuspended = true;
    }

    public void wakeupSuspendedBinlogSplitReader() {
        this.isBinlogSplitReaderSuspended = false;
    }

    public boolean isHasAssignedBinlogSplit() {
        return hasAssignedBinlogSplit;
    }

    public void setHasAssignedBinlogSplit(boolean hasAssignedBinlogSplit) {
        this.hasAssignedBinlogSplit = hasAssignedBinlogSplit;
    }
}
