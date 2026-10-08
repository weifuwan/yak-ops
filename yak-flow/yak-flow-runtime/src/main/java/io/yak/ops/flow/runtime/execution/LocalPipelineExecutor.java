package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.graph.StreamGraph;
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

            CompiledJobPlan plan = CompiledJobPlan.compile(graph, configuration);
            LocalJobClient client = new LocalJobClient(JobID.generate());
            client.start(plan, runner);
            return CompletableFuture.completedFuture(client);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
