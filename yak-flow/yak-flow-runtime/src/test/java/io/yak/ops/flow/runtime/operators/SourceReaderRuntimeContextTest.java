package io.yak.ops.flow.runtime.operators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import io.yak.ops.flow.runtime.source.event.SourceEventWrapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SourceReaderRuntimeContextTest {

    @Test
    void shouldSendAttemptAwareRequestWithTaskParallelismAndConfigurationCopy() {
        RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 5, 1, 2, 3);
        Configuration configuration = new Configuration();
        configuration.set(CoreOptions.DEFAULT_PARALLELISM, 9);
        TaskEnvironment environment = new TaskEnvironment(info, configuration);
        AtomicReference<OperatorEvent> sent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SourceReaderRuntimeContext context = new SourceReaderRuntimeContext(environment, event -> {
            sent.set(event);
            return CompletableFuture.completedFuture(null);
        }, failure::set);

        context.sendSplitRequest();

        assertEquals(new RequestSplitEvent(1, 3), sent.get());
        assertEquals(1, context.getIndexOfSubtask());
        assertEquals(2, context.currentParallelism());
        assertEquals(9, (int) context.getConfiguration().get(CoreOptions.DEFAULT_PARALLELISM));
        context.getConfiguration().set(CoreOptions.DEFAULT_PARALLELISM, 7);
        configuration.set(CoreOptions.DEFAULT_PARALLELISM, 16);
        assertEquals(9, (int) context.getConfiguration().get(CoreOptions.DEFAULT_PARALLELISM));
        assertNull(failure.get());
    }

    @Test
    void shouldSendConnectorSourceEventThroughTheAttemptAwareGateway() {
        RuntimeTaskInfo info = new RuntimeTaskInfo(JobID.generate(), 5, 0, 1, 4);
        AtomicReference<OperatorEvent> sent = new AtomicReference<>();
        SourceReaderRuntimeContext context = new SourceReaderRuntimeContext(
                new TaskEnvironment(info, new Configuration()), event -> {
                    sent.set(event);
                    return CompletableFuture.completedFuture(null);
                }, failure -> { throw new AssertionError(failure); });

        ProbeSourceEvent ping = new ProbeSourceEvent("hello");
        context.sendSourceEventToCoordinator(ping);
        assertEquals(new SourceEventWrapper(ping), sent.get());
    }

    private record ProbeSourceEvent(String message) implements SourceEvent {}

    @Test
    void shouldReportSynchronousAndAsynchronousGatewayFailure() {
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), 5, 0, 1, 0), new Configuration());
        AtomicReference<Throwable> synchronousFailure = new AtomicReference<>();
        SourceReaderRuntimeContext synchronous = new SourceReaderRuntimeContext(environment, event -> {
            throw new IllegalStateException("gateway closed");
        }, synchronousFailure::set);
        synchronous.sendSplitRequest();
        assertNotNull(synchronousFailure.get());
        assertTrue(synchronousFailure.get().getMessage().contains("gateway closed"));

        AtomicReference<Throwable> asynchronousFailure = new AtomicReference<>();
        CompletableFuture<Void> reply = new CompletableFuture<>();
        SourceReaderRuntimeContext asynchronous =
                new SourceReaderRuntimeContext(environment, event -> reply, asynchronousFailure::set);
        asynchronous.sendSplitRequest();
        assertNull(asynchronousFailure.get());

        reply.completeExceptionally(new IllegalStateException("mailbox rejected"));
        assertNotNull(asynchronousFailure.get());
        assertTrue(asynchronousFailure.get().getMessage().contains("mailbox rejected"));
    }

    @Test
    void shouldIgnoreRequestAndLateFailureAfterTaskCancellation() {
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), 5, 0, 1, 1), new Configuration());
        AtomicBoolean canceled = new AtomicBoolean();
        AtomicInteger submitted = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CompletableFuture<Void> reply = new CompletableFuture<>();
        SourceReaderRuntimeContext context = new SourceReaderRuntimeContext(
                environment.withCancellation(canceled::get), event -> {
                    submitted.incrementAndGet();
                    return reply;
                }, failure::set);

        context.sendSplitRequest();
        assertEquals(1, submitted.get());
        canceled.set(true);
        reply.completeExceptionally(new IllegalStateException("late reply"));
        assertNull(failure.get());
        context.sendSplitRequest();
        assertEquals(1, submitted.get());
    }
}
