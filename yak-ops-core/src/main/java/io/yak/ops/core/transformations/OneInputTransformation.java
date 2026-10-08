package io.yak.ops.core.transformations;

import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.OneInputOperatorFactory;
import java.util.List;
import java.util.Objects;

/**
 * 描述将一个单输入 Operator 应用于上游 Transformation 的逻辑节点。
 *
 * <p>本类只持有上游节点和 Operator 工厂，不创建运行时 Operator，
 * 也不负责打开连接、启动线程或执行数据处理。
 *
 * <p>输入关系和工厂在构造后不可修改。Runtime 应在每次作业运行或
 * 并行子任务启动时，通过工厂创建独立的 Operator 实例。
 *
 * @param <IN> 上游 Transformation 的输出类型，即当前 Operator 的输入类型
 * @param <OUT> 当前 Operator 的输出类型
 * @author weifuwan
 */
public class OneInputTransformation<IN, OUT> extends Transformation<OUT> {

    /** 唯一的上游逻辑转换节点。 */
    private final Transformation<IN> input;

    /** 创建运行时 Operator 的工厂，不保存共享的 Operator 运行实例。 */
    private final OneInputOperatorFactory<IN, OUT> operatorFactory;

    public OneInputTransformation(
            Transformation<IN> input,
            String name,
            OneInputOperatorFactory<IN, OUT> operatorFactory,
            Class<OUT> outputType) {
        this(input, name, operatorFactory, outputType, DEFAULT_PARALLELISM);
    }

    public OneInputTransformation(
            Transformation<IN> input,
            String name,
            OneInputOperatorFactory<IN, OUT> operatorFactory,
            Class<OUT> outputType,
            int parallelism) {
        super(name, outputType, parallelism);
        this.input = Objects.requireNonNull(input, "input 不能为空");
        this.operatorFactory = Objects.requireNonNull(operatorFactory, "operatorFactory 不能为空");
    }

    /** 获取唯一的上游 Transformation。 */
    public final Transformation<IN> getInput() {
        return input;
    }

    /** 获取当前节点的输入类型，与上游节点的输出类型一致。 */
    public final Class<IN> getInputType() {
        return input.getOutputType();
    }

    /** 获取 Operator 工厂；此方法不创建运行时 Operator。 */
    public final OneInputOperatorFactory<IN, OUT> getOperatorFactory() {
        return operatorFactory;
    }

    /** 返回仅包含唯一上游节点的不可修改列表。 */
    @Override
    public final List<Transformation<?>> getInputs() {
        return List.of(input);
    }
}
