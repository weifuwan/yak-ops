package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.graph.StreamGraph;
import io.yak.ops.core.graph.StreamGraphGenerator;
import io.yak.ops.core.graph.StreamNode;
import java.time.Duration;
import java.util.Objects;

/**
 * 单次本地 Job 提交的不可变执行输入。
 *
 * <p>只持有本次作业 JobID、已解析的逻辑图、运行模式和配置快照，不管理线程、Task 或 Connector 连接。
 * 读取配置时返回独立副本，避免 Runner 或调用方修改本次提交的有效配置。
 */
public final class CompiledJobPlan {

    private final JobID jobID;
    private final StreamGraph graph;
    private final RuntimeExecutionMode runtimeMode;
    private final Configuration configuration;

    private CompiledJobPlan(JobID jobID, StreamGraph graph, RuntimeExecutionMode runtimeMode,
            Configuration configuration) {
        this.jobID = Objects.requireNonNull(jobID, "jobID 不能为空");
        this.graph = graph;
        this.runtimeMode = runtimeMode;
        this.configuration = new Configuration(configuration);
    }

    /**
     * 冻结提交配置，校验它与已生成的 StreamGraph 相容，再解析实际运行模式。
     *
     * <p>只有继承默认并行度的节点要求构图和提交的默认值一致；显式指定的节点不受影响。
     */
    public static CompiledJobPlan compile(StreamGraph graph, Configuration configuration) {
        Objects.requireNonNull(graph, "graph 不能为空");
        Configuration snapshot = new Configuration(Objects.requireNonNull(configuration, "configuration 不能为空"));
        validateConfiguration(graph, snapshot);
        RuntimeExecutionMode mode = StreamGraphGenerator.resolveRuntimeMode(graph, snapshot);
        return new CompiledJobPlan(JobID.generate(), graph, mode, snapshot);
    }

    /** 每次编译/提交获得独立的 JobID，与 LocalJobClient 和各 TaskInfo 一致。 */
    public JobID jobID() {
        return jobID;
    }

    public StreamGraph graph() {
        return graph;
    }

    public RuntimeExecutionMode runtimeMode() {
        return runtimeMode;
    }

    /** 返回配置快照的防御性副本，不能通过此引用修改内部配置。 */
    public Configuration configuration() {
        return new Configuration(configuration);
    }

    private static void validateConfiguration(StreamGraph graph, Configuration configuration) {
        int defaultParallelism = configuration.get(CoreOptions.DEFAULT_PARALLELISM);
        if (defaultParallelism <= 0) {
            throw new IllegalArgumentException("parallelism.default 必须为正整数");
        }

        boolean autoGenerateUids = configuration.get(PipelineOptions.AUTO_GENERATE_UIDS);
        for (StreamNode node : graph.getStreamNodes()) {
            if (node.usesDefaultParallelism() && node.getParallelism() != defaultParallelism) {
                throw new IllegalArgumentException(
                        "StreamNode " + node.getId() + " 的默认并行度与提交配置不一致："
                                + node.getParallelism() + " != " + defaultParallelism);
            }
            if (!autoGenerateUids && node.getUid() == null) {
                throw new IllegalArgumentException("关闭自动 UID 生成后，所有节点必须指定稳定 UID：" + node.getId());
            }
        }

        Duration interval = configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL);
        if (interval.isNegative()) {
            throw new IllegalArgumentException("Checkpoint 间隔不能为负数");
        }
        Duration timeout = configuration.get(CheckpointingOptions.CHECKPOINTING_TIMEOUT);
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Checkpoint 超时时间必须大于零");
        }
        Duration minPause = configuration.get(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS);
        if (minPause.isNegative()) {
            throw new IllegalArgumentException("Checkpoint 最小间隔不能为负数");
        }
        int maxConcurrent = configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS);
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("Checkpoint 并发上限必须大于零");
        }
    }
}
