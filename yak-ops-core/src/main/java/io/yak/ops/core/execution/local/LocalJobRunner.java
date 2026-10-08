package io.yak.ops.core.execution.local;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.graph.StreamGraph;
import java.util.function.BooleanSupplier;

/**
 * 单次本地作业的数据处理入口。
 *
 * <p>此接口由后续本地 Runtime 实现，负责依据 StreamGraph 创建 Reader、Operator、Writer，
 * 运行数据流，并在返回或抛出异常前释放本次运行的资源。
 *
 * <p>调用会占用当前执行线程直至作业结束；无界数据流通常持续运行到取消或失败。
 * 同一 Runner 可以由多个 Job 并发调用，实现类必须自行隔离每个 Job 的可变状态。
 *
 * <p>Runner 必须配合取消请求和线程中断及时停止，不能把取消当作正常完成。
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface LocalJobRunner {

    /**
     * 执行一张 StreamGraph。
     *
     * @param graph 已生成且经过校验的执行图
     * @param mode 已解析的实际执行模式（BATCH 或 STREAMING）
     * @param configuration 本次提交的配置副本
     * @param cancellationRequested 是否已请求取消；应在运行循环中检查
     * @throws Exception 执行或清理失败
     */
    void run(
            StreamGraph graph,
            RuntimeExecutionMode mode,
            Configuration configuration,
            BooleanSupplier cancellationRequested) throws Exception;
}
