package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.graph.StreamGraph;
import io.yak.ops.core.graph.StreamGraphGenerator;
import io.yak.ops.core.graph.StreamNode;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 在当前 JVM 内提交并执行 StreamGraph 的 PipelineExecutor。
 *
 * <p>仅负责验证提交参数、冻结配置、解析运行模式、分配 JobID 和启动工作线程。
 * Source Reader、Operator、SinkWriter 的装配与执行交给 LocalJobRunner。
 *
 * <p>Future 完成只表示本地工作线程已经提交，不能视为数据处理完成。
 *
 * @author weifuwan
 */
public final class LocalPipelineExecutor implements PipelineExecutor {

    private final LocalJobRunner runner;

    /**
     * @param runner 真正执行本地 StreamGraph 的 Runtime 实现；不能为 null
     */
    public LocalPipelineExecutor(LocalJobRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner 不能为空");
    }

    @Override
    public CompletableFuture<JobClient> execute(Pipeline pipeline, Configuration configuration) {
        try {
            Objects.requireNonNull(pipeline, "pipeline 不能为空");
            Objects.requireNonNull(configuration, "configuration 不能为空");
            if (!(pipeline instanceof StreamGraph graph)) {
                throw new IllegalArgumentException("LocalPipelineExecutor 只支持 StreamGraph");
            }

            Configuration snapshot = new Configuration(configuration);
            validateSubmission(graph, snapshot);
            RuntimeExecutionMode mode = StreamGraphGenerator.resolveRuntimeMode(graph, snapshot);

            LocalJobClient client = new LocalJobClient(JobID.generate());
            client.start(graph, mode, snapshot, runner);
            return CompletableFuture.completedFuture(client);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void validateSubmission(StreamGraph graph, Configuration configuration) {
        Integer parallelism = configuration.get(ExecutionOptions.DEFAULT_PARALLELISM);
        if (parallelism == null || parallelism <= 0) {
            throw new IllegalArgumentException("默认并行度必须大于 0");
        }
        if (Boolean.FALSE.equals(configuration.get(PipelineOptions.AUTO_GENERATE_UIDS))) {
            for (StreamNode node : graph.getStreamNodes()) {
                if (node.getUid() == null) {
                    throw new IllegalArgumentException("关闭自动 UID 生成后，所有节点必须指定 UID：" + node.getId());
                }
            }
        }
    }
}
