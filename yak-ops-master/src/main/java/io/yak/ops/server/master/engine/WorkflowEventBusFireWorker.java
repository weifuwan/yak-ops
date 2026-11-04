package io.yak.ops.server.master.engine;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.master.engine.exceptions.WorkflowEventFireException;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.event.TaskFatalLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.runner.IWorkflowExecuteContext;
import io.yak.ops.server.master.utils.ExceptionUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowEventBusFireWorker {

    private final Map<Integer, IWorkflowExecution> registeredWorkflowExecuteRunnableMap =
            new ConcurrentHashMap<>();

    private final Map<ILifecycleEventType, ILifecycleEventHandler> eventHandlerMap = new ConcurrentHashMap<>();

    public void registerEventHandler(ILifecycleEventHandler eventHandler) {
        checkArgument(eventHandler != null, "event handler cannot be null");
        checkArgument(eventHandler.matchEventType() != null, "event type cannot be null");
        eventHandlerMap.put(eventHandler.matchEventType(), eventHandler);
    }

    public void registerWorkflowEventBus(IWorkflowExecution workflowExecution) {
        final IWorkflowExecuteContext workflowExecuteContext = workflowExecution.getWorkflowExecuteContext();
        final WorkflowInstance workflowInstance = workflowExecuteContext.getWorkflowInstance();
        final Integer workflowInstanceId = workflowInstance.getId();
        final String workflowInstanceName = workflowInstance.getName();
        checkState(!registeredWorkflowExecuteRunnableMap.containsKey(workflowInstanceId),
                "WorkflowExecuteRunnable(%s/%s already registered at WorkflowEventBusFireWorker", workflowInstanceId,
                workflowInstanceName);
        registeredWorkflowExecuteRunnableMap.put(workflowInstanceId, workflowExecution);
    }

    public void unRegisterWorkflowEventBus(IWorkflowExecution workflowExecution) {
        final IWorkflowExecuteContext workflowExecuteContext = workflowExecution.getWorkflowExecuteContext();
        final WorkflowInstance workflowInstance = workflowExecuteContext.getWorkflowInstance();
        final Integer workflowInstanceId = workflowInstance.getId();
        registeredWorkflowExecuteRunnableMap.remove(workflowInstanceId, workflowExecution);
    }

    public void fireAllRegisteredEvent() {
        final List<IWorkflowExecution> workflowExecutions = getWaitingFireWorkflowExecutions();
        if (CollectionUtils.isEmpty(workflowExecutions)) {
            return;
        }
        for (final IWorkflowExecution workflowExecution : workflowExecutions) {
            final Integer workflowInstanceId = workflowExecution.getId();
            final String workflowInstanceName = workflowExecution.getName();
            try {
                LogUtils.setWorkflowInstanceIdMDC(workflowInstanceId);
                doFireSingleWorkflowEventBus(workflowExecution);
            } catch (Exception ex) {
                log.error("Fire event failed for WorkflowExecuteRunnable: {}", workflowInstanceName, ex);
            } finally {
                LogUtils.removeWorkflowInstanceIdMDC();
            }
        }
    }

    public int getRegisteredWorkflowExecuteRunnableSize() {
        return registeredWorkflowExecuteRunnableMap.size();
    }

    private List<IWorkflowExecution> getWaitingFireWorkflowExecutions() {
        if (MapUtils.isEmpty(registeredWorkflowExecuteRunnableMap)) {
            return Collections.emptyList();
        }
        return registeredWorkflowExecuteRunnableMap.values()
                .stream()
                .filter(workflowExecuteRunnable -> !workflowExecuteRunnable.getWorkflowEventBus().isEmpty())
                .collect(Collectors.toList());
    }

    private void doFireSingleWorkflowEventBus(final IWorkflowExecution workflowExecution) {
        final WorkflowEventBus workflowEventBus = workflowExecution.getWorkflowEventBus();
        while (!workflowEventBus.isEmpty()) {
            Optional<AbstractLifecycleEvent> eventOptional = workflowEventBus.poll();
            if (!eventOptional.isPresent()) {
                return;
            }
            final AbstractLifecycleEvent lifecycleEvent = eventOptional.get();
            try {
                // Since we will print the event count at FinalizeEventHandler
                // So we increase the event count before the event fired then we can get the correct event count
                // And if the event handle failed we will decrease the success event count
                workflowEventBus.getWorkflowEventBusSummary().increaseFireSuccessEventCount();
                doFireSingleEvent(workflowExecution, lifecycleEvent);
            } catch (Exception ex) {
                // If the database connection is failed, do not remove the event from the event bus
                // so that the event can be fired again when the database connection is recovered
                if (ExceptionUtils.isDatabaseConnectedFailedException(ex)) {
                    workflowEventBus.publish(lifecycleEvent);
                    ThreadUtils.sleep(5_000);
                    return;
                }

                // If task context init fails, publish a fatal error event
                if (ExceptionUtils.isTaskExecutionContextCreateException(ex)) {
                    AbstractTaskLifecycleEvent taskLifecycleEvent = (AbstractTaskLifecycleEvent) lifecycleEvent;
                    final TaskFatalLifecycleEvent taskFatalEvent = TaskFatalLifecycleEvent.builder()
                            .taskExecution(taskLifecycleEvent.getTaskExecution())
                            .endTime(new Date())
                            .build();
                    workflowEventBus.publish(taskFatalEvent);
                }

                workflowEventBus.getWorkflowEventBusSummary().decreaseFireSuccessEventCount();
                workflowEventBus.getWorkflowEventBusSummary().increaseFireFailedEventCount();
                throw new WorkflowEventFireException(lifecycleEvent, ex);
            }
        }
    }

    private void doFireSingleEvent(final IWorkflowExecution workflowExecution,
                                   final AbstractLifecycleEvent event) {
        final ILifecycleEventHandler lifecycleEventHandler = eventHandlerMap.get(event.getEventType());
        if (lifecycleEventHandler == null) {
            throw new RuntimeException("No EventHandler found for event: " + event.getEventType());
        }
        lifecycleEventHandler.handle(workflowExecution, event);
    }

}
