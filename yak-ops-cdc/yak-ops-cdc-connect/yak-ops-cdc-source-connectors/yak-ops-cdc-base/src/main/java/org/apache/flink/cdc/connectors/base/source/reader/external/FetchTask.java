package org.apache.flink.cdc.connectors.base.source.reader.external;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.config.SourceConfig;
import org.apache.flink.cdc.connectors.base.dialect.DataSourceDialect;
import org.apache.flink.cdc.connectors.base.source.meta.offset.Offset;
import org.apache.flink.cdc.connectors.base.source.meta.split.FinishedSnapshotSplitInfo;
import org.apache.flink.cdc.connectors.base.source.meta.split.SourceSplitBase;

import io.debezium.connector.base.ChangeEventQueue;
import io.debezium.pipeline.DataChangeEvent;
import io.debezium.relational.TableId;
import io.debezium.relational.Tables;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The task to fetching data of a Split. */
@Experimental
public interface FetchTask<Split> {

    /** Execute current task. */
    void execute(Context context) throws Exception;

    /** Returns current task is running or not. */
    boolean isRunning();

    /** Returns the split that the task used. */
    Split getSplit();

    /** Close current task. * */
    void close();

    /** Base context used in the execution of fetch task. */
    interface Context {

        void configure(SourceSplitBase sourceSplitBase);

        ChangeEventQueue<DataChangeEvent> getQueue();

        TableId getTableId(SourceRecord record);

        Tables.TableFilter getTableFilter();

        Offset getStreamOffset(SourceRecord record);

        boolean isDataChangeRecord(SourceRecord record);

        boolean isRecordBetween(SourceRecord record, Object[] splitStart, Object[] splitEnd);

        /**
         * Returns true if the implementation can provide a split key that is comparable with {@link
         * FinishedSnapshotSplitInfo} boundaries, enabling binary search optimizations.
         */
        default boolean supportsSplitKeyOptimization() {
            return false;
        }

        /**
         * Returns the chunk key extracted from the given {@link SourceRecord}. Only invoked when
         * {@link #supportsSplitKeyOptimization()} returns true.
         */
        default Object[] getSplitKey(SourceRecord record) {
            throw new UnsupportedOperationException(
                    "Split key extraction is not supported by this fetch task context.");
        }

        void rewriteOutputBuffer(Map<Struct, SourceRecord> outputBuffer, SourceRecord changeRecord);

        List<SourceRecord> formatMessageTimestamp(Collection<SourceRecord> snapshotRecords);

        void close() throws Exception;

        DataSourceDialect getDataSourceDialect();

        SourceConfig getSourceConfig();
    }
}
