package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 在当前 JVM 内提交并执行 StreamGraph 的 PipelineExecutor。
 *
 * <p>仅负责验证提交参数、冻结配置、解析运行模式、分配 JobID 和启动工作线程。
 * Source Reader、Operator、SinkWriter 的装配与执行交给 JobRunner。
 *
 * <p>Future 完成只表示本地工作线程已经提交，不能视为数据处理完成。
 *
 * @author weifuwan
 */
public final class EmbeddedPipelineExecutor implements PipelineExecutor {

    private final JobRunner runner;

    /** 使用内置单节点 Runner；只支持已具备本地装配语义的线性单并行拓扑。 */
    public EmbeddedPipelineExecutor() {
        this(new StreamJobRunner());
    }

    /**
     * @param runner 真正执行本地 StreamGraph 的 Runtime 实现；不能为 null
     */
    public EmbeddedPipelineExecutor(JobRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner 不能为空");
    }

    @Override
    public CompletableFuture<JobClient> execute(Pipeline pipeline, Configuration configuration) {
        try {
            Objects.requireNonNull(pipeline, "pipeline 不能为空");
            Objects.requireNonNull(configuration, "configuration 不能为空");
            if (!(pipeline instanceof StreamGraph graph)) {
                throw new IllegalArgumentException("EmbeddedPipelineExecutor 只支持 StreamGraph");
            }

            CompiledJobPlan plan = CompiledJobPlan.compile(graph, configuration);
            runner.validate(plan);
            EmbeddedJobClient client = new EmbeddedJobClient(plan.jobID());
            client.start(plan, runner);
            return CompletableFuture.completedFuture(client);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
