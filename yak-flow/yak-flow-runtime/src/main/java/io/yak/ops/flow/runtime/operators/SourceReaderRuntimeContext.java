package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEventGateway;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Runtime context passed to one SourceReader.
 *
 * <p>Identity and configuration come from the owning TaskEnvironment. Split requests and
 * Source events are sent asynchronously to the coordinator; the task mailbox must not wait
 * for the coordinator acknowledgment.
 */
public final class SourceReaderRuntimeContext implements SourceReaderContext {

    private final TaskEnvironment environment;
    private final OperatorEventGateway eventGateway;
    private final Consumer<Throwable> asyncFailureHandler;

    public SourceReaderRuntimeContext(
            TaskEnvironment environment, OperatorEventGateway eventGateway, Consumer<Throwable> asyncFailureHandler) {
        this.environment = Objects.requireNonNull(environment, "environment 不能为空");
        this.eventGateway = Objects.requireNonNull(eventGateway, "eventGateway 不能为空");
        this.asyncFailureHandler = Objects.requireNonNull(asyncFailureHandler, "asyncFailureHandler 不能为空");
    }

    @Override
    public Configuration getConfiguration() {
        return environment.configuration();
    }

    @Override
    public int getIndexOfSubtask() {
        return environment.taskInfo().subtaskIndex();
    }

    @Override
    public int currentParallelism() {
        return environment.taskInfo().parallelism();
    }

    @Override
    public void sendSourceEventToCoordinator(SourceEvent event) {
        send(new SourceEventWrapper(Objects.requireNonNull(event, "event")));
    }

    @Override
    public void sendSplitRequest() {
        send(new RequestSplitEvent(getIndexOfSubtask(), environment.taskInfo().attemptNumber()));
    }

    private void send(io.yak.ops.flow.runtime.operators.coordination.OperatorEvent event) {
        if (environment.isCancellationRequested()) {
            return;
        }
        CompletionStage<Void> response;
        try {
            response =
                    Objects.requireNonNull(eventGateway.sendEventToCoordinator(event), "OperatorEventGateway 返回了 null");
        } catch (Throwable failure) {
            asyncFailureHandler.accept(failure);
            return;
        }
        response.whenComplete((unused, failure) -> {
            if (failure != null && !environment.isCancellationRequested()) {
                asyncFailureHandler.accept(failure);
            }
        });
    }
}
