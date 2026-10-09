package io.yak.ops.flow.connector.cdc.mysql.debezium;

import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import java.util.List;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Debezium Engine 一次 ChangeConsumer batch 及其延迟确认状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class DebeziumBatch {

    private final List<RecordChangeEvent<SourceRecord>> records;
    private final DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> committer;
    private int deliveredCount;
    private int acknowledgedCount;
    private boolean batchFinished;

    DebeziumBatch(
            List<RecordChangeEvent<SourceRecord>> records,
            DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> committer) {
        this.records = List.copyOf(records);
        this.committer = committer;
    }

    boolean hasNext() {
        return deliveredCount < records.size();
    }

    RecordChangeEvent<SourceRecord> peekNext() {
        return records.get(deliveredCount);
    }

    void markDelivered() {
        deliveredCount++;
    }

    int deliveredCount() {
        return deliveredCount;
    }

    boolean fullyAcknowledged() {
        return batchFinished;
    }

    void acknowledgeThrough(int count) throws InterruptedException {
        int target = Math.min(count, deliveredCount);
        for (int index = acknowledgedCount; index < target; index++) {
            committer.markProcessed(records.get(index));
        }
        acknowledgedCount = target;
        if (!batchFinished && acknowledgedCount == records.size()) {
            committer.markBatchFinished();
            batchFinished = true;
        }
    }
}
