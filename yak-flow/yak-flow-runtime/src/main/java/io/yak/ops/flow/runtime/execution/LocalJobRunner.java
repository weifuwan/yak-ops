package io.yak.ops.flow.runtime.execution;

import io.yak.ops.flow.runtime.checkpoint.LocalCheckpointCoordinator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 单次本地作业的数据处理入口。
 *
 * <p>内置 LocalStreamJobRunner 已支持线性单 Task 运行；后续本地 Runtime 可以按独立契约实现更复杂的图，
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
     * 执行一次已冻结的本地作业计划。
     *
     * @param plan 同时包含 StreamGraph、运行模式和配置快照的执行计划
     * @param cancellationRequested 是否已请求取消；应在运行循环中检查
     * @throws Exception 执行或清理失败
     */
    void run(CompiledJobPlan plan, BooleanSupplier cancellationRequested) throws Exception;

    /**
     * 向 JobClient 发布本次运行的 CheckpointController；默认仍兼容无检查点的测试 Runner。
     * 每次执行只能在启动后注册属于该 Job 的独立 Controller。
     */
    default void run(CompiledJobPlan plan, BooleanSupplier cancellationRequested,
            Consumer<LocalCheckpointCoordinator> registerCheckpoint) throws Exception {
        run(plan, cancellationRequested);
    }

    /** 提交线程中提前验证该 Runner 的物理执行能力，不得创建运行资源。 */
    default void validate(CompiledJobPlan plan) {}

}
