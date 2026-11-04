package io.yak.ops.server.master.engine.command;

import static java.util.concurrent.CompletableFuture.supplyAsync;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.thread.BaseDaemonThread;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.config.MasterServerLoadProtection;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.WorkflowEventBusCoordinator;
import io.yak.ops.server.master.engine.exceptions.CommandDuplicateHandleException;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.execution.WorkflowExecutionFactory;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowStartLifecycleEvent;
import io.yak.ops.server.master.metrics.MasterServerMetrics;
import io.yak.ops.server.master.metrics.WorkflowInstanceMetrics;
import io.yak.ops.service.command.CommandService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Master scheduler thread, this thread will consume the commands from database and trigger processInstance executed.
 */
@Service
@Slf4j
public class CommandEngine extends BaseDaemonThread implements AutoCloseable {

    @Autowired
    private ICommandFetcher commandFetcher;

    @Autowired
    private CommandService commandService;

    @Autowired
    private MasterConfig masterConfig;

    @Autowired
    private MasterServerLoadProtection masterServerLoadProtection;

    @Autowired
    private IWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private WorkflowExecutionFactory workflowExecutionFactory;

    @Autowired
    private MetricsProvider metricsProvider;

    @Autowired
    private WorkflowEventBusCoordinator workflowEventBusCoordinator;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ExecutorService commandHandleThreadPool;

    private boolean flag = false;

    protected CommandEngine() {
        super("MasterCommandLoopThread");
    }

    @Override
    public synchronized void start() {
        log.info("MasterSchedulerBootstrap starting..");
        this.commandHandleThreadPool = ThreadUtils.newDaemonFixedThreadExecutor("MasterCommandHandleThreadPool",
                Runtime.getRuntime().availableProcessors());
        flag = true;
        super.start();
        log.info("MasterSchedulerBootstrap started...");
    }

    @Override
    public void close() throws Exception {
        log.info("MasterSchedulerBootstrap stopping...");
        flag = false;
        log.info("MasterSchedulerBootstrap stopped...");
    }

    @Override
    public void run() {
        while (flag) {
            try {
                // todo: if the workflow event queue is much, we need to handle the back pressure
                SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
                if (masterServerLoadProtection.isOverload(systemMetrics)) {
                    log.warn("The current server is overload, cannot consumes commands.");
                    MasterServerMetrics.incMasterOverload();
                    Thread.sleep(Constants.SLEEP_TIME_MILLIS);
                    continue;
                }
                List<Command> commands = commandFetcher.fetchCommands();
                if (CollectionUtils.isEmpty(commands)) {
                    // indicate that no command ,sleep for 1s
                    Thread.sleep(Constants.SLEEP_TIME_MILLIS);
                    continue;
                }

                List<CompletableFuture<Void>> allCompleteFutures = new ArrayList<>();
                for (Command command : commands) {
                    CompletableFuture<Void> completableFuture = supplyAsync(() -> {
                        LogUtils.setWorkflowInstanceIdMDC(command.getWorkflowInstanceId());
                        return command;
                    }, commandHandleThreadPool)
                            .thenApply(this::bootstrapCommand)
                            .thenAccept(this::bootstrapWorkflowExecution)
                            .thenAccept((unused) -> bootstrapSuccess(command))
                            .exceptionally(throwable -> bootstrapError(command, throwable))
                            .whenComplete((result, throwable) -> LogUtils.removeWorkflowInstanceIdMDC());
                    allCompleteFutures.add(completableFuture);
                }
                CompletableFuture.allOf(allCompleteFutures.toArray(new CompletableFuture[0])).join();
            } catch (InterruptedException interruptedException) {
                log.warn("Master schedule bootstrap interrupted, close the loop", interruptedException);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Master schedule workflow error", e);
                // sleep for 1s here to avoid the database down cause the exception boom
                ThreadUtils.sleep(Constants.SLEEP_TIME_MILLIS);
            }
        }
    }

    private IWorkflowExecution bootstrapCommand(Command command) {
        return workflowExecutionFactory.createWorkflowExecuteRunnable(command);
    }

    private CompletableFuture<Void> bootstrapWorkflowExecution(IWorkflowExecution workflowExecution) {
        final WorkflowInstance workflowInstance =
                workflowExecution.getWorkflowExecuteContext().getWorkflowInstance();

        if (workflowInstance.getState() == WorkflowExecutionStatus.SERIAL_WAIT) {
            log.info("The workflow {} state is: {} will not be trigger now",
                    workflowInstance.getName(),
                    workflowInstance.getState());
            return CompletableFuture.completedFuture(null);
        }

        WorkflowInstanceMetrics.recordWorkflowInstanceSubmit(workflowInstance.getWorkflowDefinitionCode());
        workflowRepository.put(workflowExecution);
        workflowEventBusCoordinator.registerWorkflowEventBus(workflowExecution);
        workflowExecution.getWorkflowEventBus()
                .publish(WorkflowStartLifecycleEvent.of(workflowExecution));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> bootstrapSuccess(Command command) {
        log.info("Success bootstrap command {}", JSONUtils.toPrettyJsonString(command));
        MasterServerMetrics.incMasterConsumeCommand(1);
        return CompletableFuture.completedFuture(null);
    }

    private Void bootstrapError(Command command, Throwable throwable) {
        // The exception is raised inside a CompletableFuture chain, so it arrives here wrapped in
        // CompletionException, which a direct instanceof check cannot see through. See #18570.
        if (ExceptionUtils.throwableOfType(throwable, CommandDuplicateHandleException.class) != null) {
            log.warn("Handle command failed, the command: {} has been handled by other master",
                    command,
                    throwable);
            return null;
        }

        transactionTemplate.execute(status -> {
            log.warn("Failed bootstrap command {} ", JSONUtils.toPrettyJsonString(command), throwable);
            final int workflowInstanceId = command.getWorkflowInstanceId();

            workflowInstanceDao.forceUpdateWorkflowInstanceState(workflowInstanceId, WorkflowExecutionStatus.FAILURE);
            WorkflowInstanceMetrics.recordWorkflowInstanceFinish(
                    WorkflowExecutionStatus.FAILURE,
                    command.getWorkflowDefinitionCode());
            log.info("Set workflow instance {} state to FAILURE", workflowInstanceId);

            commandService.moveToErrorCommand(command, ExceptionUtils.getStackTrace(throwable));
            log.info("Move command {} to error command table", command.getId());
            return null;
        });
        return null;
    }

}
