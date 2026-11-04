package io.yak.ops.server.worker.executor;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskCallBack;
import io.yak.ops.plugin.task.api.log.TaskLogMarkers;
import io.yak.ops.plugin.task.api.model.ApplicationInfo;
import io.yak.ops.plugin.task.api.resource.ResourceContext;
import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.server.worker.utils.TaskExecutionContextUtils;
import io.yak.ops.server.worker.utils.TenantUtils;
import io.yak.ops.task.executor.AbstractTaskExecutor;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.TaskExecutorState;
import io.yak.ops.task.executor.TaskExecutorStateMappings;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;

import java.util.ArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PhysicalTaskExecutor extends AbstractTaskExecutor {

    private final WorkerConfig workerConfig;

    private final StorageOperator storageOperator;

    @Getter
    private AbstractTask physicalTask;

    private final PhysicalTaskPluginFactory physicalTaskPluginFactory;

    public PhysicalTaskExecutor(final PhysicalTaskExecutorBuilder physicalTaskExecutorBuilder) {
        super(physicalTaskExecutorBuilder.getTaskExecutionContext(),
                physicalTaskExecutorBuilder.getTaskExecutorEventBus());
        this.workerConfig = physicalTaskExecutorBuilder.getWorkerConfig();
        this.storageOperator = physicalTaskExecutorBuilder.getStorageOperator();
        this.physicalTaskPluginFactory = physicalTaskExecutorBuilder.getPhysicalTaskPluginFactory();
    }

    @Override
    protected void initializeTaskPlugin() {
        this.physicalTask = physicalTaskPluginFactory.createPhysicalTask(this);
        log.info("Initialized physicalTask: {} successfully", taskExecutionContext.getTaskType());

        this.physicalTask.init();

        this.physicalTask.getParameters().setVarPool(new ArrayList<>());
        log.info("Set taskVarPool: {} successfully", taskExecutionContext.getVarPool());
    }

    @Override
    protected void doTriggerTaskPlugin() {
        final ITaskExecutor taskExecutor = this;
        physicalTask.handle(new TaskCallBack() {

            @Override
            public void updateRemoteApplicationInfo(final int taskInstanceId, final ApplicationInfo applicationInfo) {
                taskExecutionContext.setAppIds(applicationInfo.getAppIds());
                taskExecutorEventBus.publish(TaskExecutorRuntimeContextChangedLifecycleEvent.of(taskExecutor));
            }

            @Override
            public void updateTaskInstanceInfo(final int taskInstanceId) {
                taskExecutorEventBus.publish(TaskExecutorRuntimeContextChangedLifecycleEvent.of(taskExecutor));
            }
        });
    }

    @Override
    protected TaskExecutorState doTrackTaskPluginStatus() {
        return TaskExecutorStateMappings.mapState(physicalTask.getExitStatus());
    }

    @Override
    public void pause() {
        log.warn("The physical doesn't support pause");
    }

    @Override
    public void kill() {
        if (physicalTask != null) {
            physicalTask.cancel();
        }
    }

    @Override
    public void finalizeTask() {
        clearTaskInstanceWorkingDirectoryIfNeeded();
    }

    private void clearTaskInstanceWorkingDirectoryIfNeeded() {
        boolean isDevelopment = PropertyUtils.getBoolean(Constants.DEVELOPMENT_STATE, true);
        if (!isDevelopment) {
            TaskExecutionContextUtils.clearTaskInstanceWorkingDirectory(taskExecutionContext);
        }
    }

    @Override
    protected void initializeTaskContext() {
        super.initializeTaskContext();

        taskExecutionContext.setTaskAppId(String.valueOf(taskExecutionContext.getTaskInstanceId()));

        taskExecutionContext.setTenantCode(TenantUtils.getOrCreateActualTenant(workerConfig, taskExecutionContext));
        log.info("TenantCode: {} check successfully", taskExecutionContext.getTenantCode());

        TaskExecutionContextUtils.createTaskInstanceWorkingDirectory(taskExecutionContext);
        log.info("TaskInstance working directory: {} create successfully", taskExecutionContext.getExecutePath());

        final ResourceContext resourceContext = TaskExecutionContextUtils.downloadResourcesIfNeeded(
                physicalTaskPluginFactory.getTaskChannel(this),
                storageOperator,
                taskExecutionContext);
        taskExecutionContext.setResourceContext(resourceContext);
        log.info("Download resources successfully: {}", taskExecutionContext.getResourceContext());

        log.info(TaskLogMarkers.excludeInTaskLog(), "Initialized Task Context{}",
                JSONUtils.toPrettyJsonString(taskExecutionContext));

    }

    @Override
    public String toString() {
        return "PhysicalTaskExecutor{" +
                "id=" + taskExecutionContext.getTaskInstanceId() +
                ", name=" + taskExecutionContext.getTaskName() +
                ", stat=" + taskExecutorState.get() +
                '}';
    }

}
