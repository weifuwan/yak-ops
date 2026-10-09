package io.yak.ops.core.execution;

import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import java.util.concurrent.CompletableFuture;

/**
 * Pipeline 统一执行入口。
 *
 * <p>负责根据运行配置提交 Pipeline，
 * 不区分批处理与流处理。
 *
 * @author weifuwan
 */
public interface PipelineExecutor {

    /**
     * 异步提交 Pipeline。
     *
     * <p>Future 完成代表作业提交成功，
     * 不代表作业已经执行结束。
     *
     * @param pipeline 待执行的 Pipeline
     * @param configuration 运行配置
     * @return 对应作业的 JobClient
     */
    CompletableFuture<JobClient> execute(Pipeline pipeline, Configuration configuration);
}
