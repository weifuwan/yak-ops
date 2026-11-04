package org.apache.flink.cdc.runtime.operators;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.operators.MailboxExecutor;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.functions.async.AsyncFunction;
import org.apache.flink.streaming.api.operators.StreamOperatorParameters;
import org.apache.flink.streaming.api.operators.async.AsyncWaitOperator;
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeService;
import org.apache.flink.streaming.util.retryable.AsyncRetryStrategies;

/** Flink 1.20 adapter for an ordered async operator with state-consistent checkpoints. */
@Internal
public class AsyncWaitOperatorAdapter<IN, OUT> extends AsyncWaitOperator<IN, OUT> {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
    public AsyncWaitOperatorAdapter(
            StreamOperatorParameters<OUT> parameters,
            AsyncFunction<IN, OUT> asyncFunction,
            long timeout,
            int capacity,
            ProcessingTimeService processingTimeService,
            MailboxExecutor mailboxExecutor) {
        super(
                asyncFunction,
                timeout,
                capacity,
                AsyncDataStream.OutputMode.ORDERED,
                AsyncRetryStrategies.NO_RETRY_STRATEGY,
                processingTimeService,
                mailboxExecutor);
        setup(parameters.getContainingTask(), parameters.getStreamConfig(), parameters.getOutput());
    }

    @Override
    public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {
        // The async function owns schema state, so it must not advance beyond records retained in
        // AsyncWaitOperator's recovery queue.
        endInput();
        super.prepareSnapshotPreBarrier(checkpointId);
    }
}
