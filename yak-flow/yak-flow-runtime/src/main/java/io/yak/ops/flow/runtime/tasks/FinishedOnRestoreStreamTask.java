package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.StreamTaskNetworkInput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import java.util.concurrent.CompletableFuture;

/**
 * Replays only the terminal network protocol of an operator already finished in a checkpoint.
 *
 * <p>No SourceReader, StreamOperator or SinkWriter is constructed, so terminal output and
 * external commits cannot be repeated. EndOfData and checkpoint-control events retain their
 * normal per-channel ordering until physical EndOfPartition.
 */
public final class FinishedOnRestoreStreamTask extends StreamTask {

    private final StreamTaskNetworkInput<Object> input;
    private final RecordWriterOutput<Object> output;
    private CompletableFuture<Void> dataAcknowledged = CompletableFuture.completedFuture(null);

    private final InputGate.BarrierHandler barrierHandler = new InputGate.BarrierHandler() {
        @Override
        public void onBarrier(long checkpointId) {
            if (output != null) {
                output.broadcastBarrier(checkpointId);
            }
        }

        @Override
        public void onEndOfData() throws Exception {
            endData();
        }

        @Override
        public void onCheckpointDeclined(long checkpointId) {
            if (input != null) {
                input.declineCheckpoint(checkpointId);
            }
        }
    };

    public FinishedOnRestoreStreamTask(
            TaskEnvironment environment, InputGate<Object> input, RecordWriterOutput<Object> output) {
        super(environment);
        this.input = input == null ? null : new StreamTaskNetworkInput<>(input);
        this.output = output;
    }

    @Override
    protected void openTask() {}

    @Override
    protected InputStatus processInput() throws Exception {
        if (input == null) {
            return InputStatus.END_OF_INPUT;
        }
        return input.emitNext(
                ignored -> {
                    throw new IllegalStateException("Finished-on-Restore received a business record");
                },
                barrierHandler);
    }

    @Override
    protected CompletableFuture<Void> getAvailableFuture() {
        if (input == null) {
            return CompletableFuture.completedFuture(null);
        }
        return input.getAvailableFuture();
    }

    @Override
    protected void finishTask() {
        if (output != null) {
            dataAcknowledged = output.endData();
        }
    }

    @Override
    protected CompletableFuture<Void> endOfDataAcknowledgement() {
        return dataAcknowledged;
    }

    @Override
    protected void finishOutput() {
        if (output != null) {
            output.finish();
        }
    }

    @Override
    protected void closeTask() {}
}
