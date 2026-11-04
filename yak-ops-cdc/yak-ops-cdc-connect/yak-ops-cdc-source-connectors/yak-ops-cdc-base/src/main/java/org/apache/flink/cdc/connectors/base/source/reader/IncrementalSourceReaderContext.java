package org.apache.flink.cdc.connectors.base.source.reader;

import org.apache.flink.api.connector.source.SourceReaderContext;

/**
 * A wrapper class that wraps {@link SourceReaderContext} for sharing message between {@link
 * IncrementalSourceReader} and {@link IncrementalSourceSplitReader}.
 */
public class IncrementalSourceReaderContext {
    private final SourceReaderContext sourceReaderContext;
    private volatile boolean isStreamSplitReaderSuspended;

    private volatile boolean hasAssignedStreamSplit;

    public IncrementalSourceReaderContext(SourceReaderContext sourceReaderContext) {
        this.sourceReaderContext = sourceReaderContext;
        this.isStreamSplitReaderSuspended = false;
        this.hasAssignedStreamSplit = false;
    }

    public SourceReaderContext getSourceReaderContext() {
        return sourceReaderContext;
    }

    public boolean isStreamSplitReaderSuspended() {
        return isStreamSplitReaderSuspended;
    }

    public void suspendStreamSplitReader() {
        this.isStreamSplitReaderSuspended = true;
    }

    public void wakeupSuspendedStreamSplitReader() {
        this.isStreamSplitReaderSuspended = false;
    }

    public boolean isHasAssignedStreamSplit() {
        return hasAssignedStreamSplit;
    }

    public void setHasAssignedStreamSplit(boolean hasAssignedStreamSplit) {
        this.hasAssignedStreamSplit = hasAssignedStreamSplit;
    }
}
