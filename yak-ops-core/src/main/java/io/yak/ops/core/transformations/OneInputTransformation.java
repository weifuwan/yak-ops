
package io.yak.ops.core.transformations;

import io.yak.ops.core.api.dag.Transformation;
import java.util.List;
import java.util.Objects;

/**
 * 描述只有一个上游输入的逻辑转换节点。
 *
 * <p>本类只保存上游 Transformation 及继承的节点属性，不负责实际数据处理。
 * 具体算子定义由后续子类提供，运行资源由执行阶段创建。
 *
 * <p>输入关系在构造后不可修改，避免生成 StreamGraph 时拓扑发生变化。
 *
 * @param <IN> 上游 Transformation 的输出类型，即当前节点的输入类型
 * @param <OUT> 当前节点的输出类型
 * @author weifuwan
 */
public abstract class OneInputTransformation<IN, OUT> extends Transformation<OUT> {

    /** 唯一的上游逻辑转换节点。 */
    private final Transformation<IN> input;

    protected OneInputTransformation(Transformation<IN> input, String name, Class<OUT> outputType) {
        this(input, name, outputType, DEFAULT_PARALLELISM);
    }

    protected OneInputTransformation(
            Transformation<IN> input, String name, Class<OUT> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.input = Objects.requireNonNull(input, "input 不能为空");
    }

    /** 获取唯一的上游 Transformation。 */
    public final Transformation<IN> getInput() {
        return input;
    }

    /** 获取当前节点的输入类型，与上游节点的输出类型一致。 */
    public final Class<IN> getInputType() {
        return input.getOutputType();
    }

    /** 返回仅包含唯一上游节点的不可修改列表。 */
    @Override
    public final List<Transformation<?>> getInputs() {
        return List.of(input);
    }
}
