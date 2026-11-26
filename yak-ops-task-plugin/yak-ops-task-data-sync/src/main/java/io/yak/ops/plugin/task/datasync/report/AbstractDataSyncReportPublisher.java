package io.yak.ops.plugin.task.datasync.report;

import io.yak.ops.extract.master.transportor.DataSyncReportAck;
import io.yak.ops.extract.master.transportor.DataSyncReportRequest;

import lombok.extern.slf4j.Slf4j;

/** Shared retry policy for report transports. */
@Slf4j
public abstract class AbstractDataSyncReportPublisher implements DataSyncReportPublisher {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_INTERVAL_MILLIS = 500L;

    @Override
    public final DataSyncReportAck publish(DataSyncReportRequest request) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                DataSyncReportAck ack = doPublish(request);
                if (ack == null) {
                    throw new IllegalStateException("Master returned an empty CDC report acknowledgement");
                }
                return ack;
            } catch (Exception exception) {
                lastException = exception;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                log.warn("CDC report delivery failed, executionId={}, sequence={}, attempt={}",
                        request.getExecutionId(), request.getSequence(), attempt, exception);
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            }
        }
        throw lastException;
    }

    protected abstract DataSyncReportAck doPublish(DataSyncReportRequest request) throws Exception;
}
