package io.yak.ops.flow.runtime.operators;

/**
 * 创建单输入 Operator 运行实例的工厂。
 *
 * <p>工厂是可复用的算子定义，不能充当运行实例。
 * 每次创建都应返回相互独立的 Operator，以免多个作业或并行子任务共享可变状态。
 *
 * <p>执行上下文、Checkpoint 恢复和分布式序列化协议尚未定义，
 * 本阶段不要求工厂实现 Serializable，也不预先加入运行上下文参数。
 *
 * @param <IN> 输入记录的数据类型
 * @param <OUT> 输出记录的数据类型
 * @author weifuwan
 */
@FunctionalInterface
public interface OneInputOperatorFactory<IN, OUT> {

    /**
     * 为一次算子运行创建新的 Operator 实例。
     *
     * <p>返回值不得为 null。Runtime 负责检查这一约束，
     * 并在实例创建成功后管理其生命周期。
     *
     * @return 新建的 Operator 实例
     * @throws Exception 创建 Operator 失败
     */
    OneInputStreamOperator<IN, OUT> createOperator() throws Exception;
}
