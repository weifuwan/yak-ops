package io.yak.ops.task.execution.dispatch.embedded;

import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.dao.task.TaskGroupSlotReleasedEvent;
import io.yak.ops.task.execution.dispatch.TaskExecutionCoordinator;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Master-side TaskGroupCoordinator for the embedded DolphinScheduler Worker.
 *
 * <p>Waiting queues use upstream WAIT_QUEUE/ACQUIRE_SUCCESS states and priority
 * ordering. The Master acquires all resources in one transaction before notifying
 * the local Worker; release notifications are delivered only after commit.
 */
@Component
@DependsOn("embeddedTaskDispatcher")
public final class TaskGroupCoordinator implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TaskGroupCoordinator.class);
    private static final int SCAN_LIMIT = 1000;

    private final EmbeddedExecutionOwner owner;

    private final TaskGroupQueueRepository queues;

    private final TaskExecutionCoordinator dispatcher;

    public TaskGroupCoordinator(
            EmbeddedExecutionOwner owner, TaskGroupQueueRepository queues, TaskExecutionCoordinator dispatcher) {
        this.owner = owner;
        this.queues = queues;
        this.dispatcher = dispatcher;
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("task-group-coordinator").factory());
    private final AtomicBoolean started = new AtomicBoolean();
    // Owned by the single coordinator executor. Persist progress across scan ticks,
    // otherwise blocked entries in the first page starve unrelated free resources.
    private TaskGroupQueueEntity scanCursor;

    @PostConstruct
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        owner.incarnation();
        // The current owner repairs counters from durable acquisition rows. Unknown
        // physical outcomes keep their rows and therefore keep their reserved slots.
        queues.reconcile();
        executor.scheduleWithFixedDelay(this::scanSafely, 0, 1, TimeUnit.SECONDS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotReleased(TaskGroupSlotReleasedEvent ignored) {
        if (started.get()) {
            executor.execute(this::scanSafely);
        }
    }

    private void scanSafely() {
        if (!started.get()) {
            return;
        }
        try {
            scanWaiting();
        } catch (RuntimeException failure) {
            LOG.error(
                    "TaskGroup waiting queue scan failed, exceptionType={}",
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    void scanWaiting() {
        owner.incarnation();
        List<TaskGroupQueueEntity> waiting = queues.waiting(SCAN_LIMIT, scanCursor);
        if (waiting.isEmpty() && scanCursor != null) {
            // Reached the end of a stable priority/time/id sweep. Restart at
            // the head without resetting progress on every one-second tick.
            scanCursor = null;
            waiting = queues.waiting(SCAN_LIMIT, null);
        }
        if (waiting.isEmpty()) {
            return;
        }
        // Advance before attempting admission: a single corrupt or unavailable
        // task must not permanently keep the cursor on the same page.
        scanCursor = waiting.get(waiting.size() - 1);
        Set<String> attempted = new HashSet<>();
        for (TaskGroupQueueEntity queue : waiting) {
            if (!attempted.add(queue.getInstanceId())) {
                continue;
            }
            // Losing the embedded owner is a global safety condition, not a
            // per-task failure. Abort the scan rather than dispatching blindly.
            owner.incarnation();
            try {
                if (queues.tryAcquire(queue.getWorkspaceId(), queue.getInstanceId())) {
                    dispatcher.dispatch(queue.getWorkspaceId(), queue.getInstanceId());
                }
            } catch (RuntimeException failure) {
                LOG.error(
                        "TaskGroup queued Instance admission failed, instanceId={}, exceptionType={}",
                        queue.getInstanceId(),
                        failure.getClass().getSimpleName(),
                        failure);
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        started.set(false);
        executor.shutdownNow();
    }
}
