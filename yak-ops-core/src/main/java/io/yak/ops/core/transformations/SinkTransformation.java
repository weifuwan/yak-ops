
package io.yak.ops.core.transformations;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.dag.Transformation;
import java.util.List;
import java.util.Objects;

/**
 * 描述数据流末端的 Sink 逻辑节点。
 *
 * <p>本类只保存唯一上游和 Sink 组件定义，不负责创建 Writer、打开连接或执行写入。
 * Sink 不向下游输出记录，因此逻辑节点的输出类型为 {@link Void}。
 *
 * <p>上游关系和 Sink 定义在构造后不可修改。生成执行图时应确保
 * Sink 只能作为数据流终点，不能作为其他算子的输入。
 *
 * @param <T> Sink 消费的输入记录类型
 * @author weifuwan
 */
public final class SinkTransformation<T> extends Transformation<Void> {

    /** 唯一的上游逻辑转换节点。 */
    private final Transformation<T> input;

    /** Sink 组件定义，不是运行中的 Writer 实例。 */
    private final Sink<T> sink;

    public SinkTransformation(Transformation<T> input, String name, Sink<T> sink) {
        this(input, name, sink, DEFAULT_PARALLELISM);
    }

    public SinkTransformation(Transformation<T> input, String name, Sink<T> sink, int parallelism) {
        super(name, Void.class, parallelism);
        this.input = Objects.requireNonNull(input, "input 不能为空");
        this.sink = Objects.requireNonNull(sink, "sink 不能为空");
    }

    /** 获取唯一的上游 Transformation。 */
    public Transformation<T> getInput() {
        return input;
    }

    /** 获取 Sink 消费的记录类型，即上游节点的输出类型。 */
    public Class<T> getInputType() {
        return input.getOutputType();
    }

    /** 获取 Sink 组件定义；此方法不会创建 Writer。 */
    public Sink<T> getSink() {
        return sink;
    }

    /** 返回只包含上游节点的不可修改列表。 */
    @Override
    public List<Transformation<?>> getInputs() {
        return List.of(input);
    }
}
