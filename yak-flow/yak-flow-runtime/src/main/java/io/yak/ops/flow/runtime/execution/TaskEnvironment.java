package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.configuration.Configuration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 本地 Task 的运行上下文：统一提供身份、配置快照和只读取消信号。
 *
 * <p>不持有 SourceCoordinator、Reader、Gateway 或数据连接；Source 专属事件通道
 * 仍由 Source Task 装配，避免把 Connector 协调职责放进所有 Task 的 Context。
 */
public final class TaskEnvironment implements WriterInitContext {

    private final RuntimeTaskInfo taskInfo;
    private final Configuration configuration;
    private final BooleanSupplier cancellationRequested;

    public TaskEnvironment(RuntimeTaskInfo taskInfo, Configuration configuration) {
        this(taskInfo, configuration, () -> false);
    }

    private TaskEnvironment(
            RuntimeTaskInfo taskInfo, Configuration configuration, BooleanSupplier cancellationRequested) {
        this.taskInfo = Objects.requireNonNull(taskInfo, "taskInfo 不能为空");
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration 不能为空"));
        this.cancellationRequested = Objects.requireNonNull(cancellationRequested, "cancellationRequested 不能为空");
    }

    public RuntimeTaskInfo taskInfo() {
        return taskInfo;
    }

    @Override
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration();
    }

    /** 返回配置快照的副本，外部修改不能影响当前 Task。 */
    public Configuration configuration() {
        return new Configuration(configuration);
    }

    /** 由所属 StreamTask 绑定实际取消信号；返回新的 Context，不修改传入的实例。 */
    public TaskEnvironment withCancellation(BooleanSupplier cancellationRequested) {
        return new TaskEnvironment(taskInfo, configuration, cancellationRequested);
    }

    /** 观察所属 Task 是否已收到取消请求；不允许 Connector 通过此入口取消 Task。 */
    public boolean isCancellationRequested() {
        return cancellationRequested.getAsBoolean();
    }
}
