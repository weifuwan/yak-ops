package io.yak.ops.core.transformations;

import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.dag.Transformation;
import java.util.List;
import java.util.Objects;

/**
 * 描述数据流中没有上游输入的 Source 节点。
 *
 * <p>本类只记录 Source 组件及其逻辑属性，不负责打开连接、创建 Reader
 * 或启动读取线程。真正的运行资源由执行阶段创建和释放。
 *
 * @param <T> Source 产生的数据类型
 * @author weifuwan
 */
public final class SourceTransformation<T> extends Transformation<T> {

    /** Source 组件定义；不应是已启动的读取实例。 */
    private final Source<T> source;

    public SourceTransformation(String name, Source<T> source, Class<T> outputType) {
        this(name, source, outputType, DEFAULT_PARALLELISM);
    }

    public SourceTransformation(String name, Source<T> source, Class<T> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.source = Objects.requireNonNull(source, "source 不能为空");
    }

    /** 获取 Source 组件定义。 */
    public Source<T> getSource() {
        return source;
    }

    /** 根据 Source 的数据有界性判断批流属性，而不是使用任务名称区分。 */
    public Boundedness getBoundedness() {
        return Objects.requireNonNull(source.getBoundedness(), "Source 必须声明 Boundedness");
    }

    /** Source 没有上游 Transformation。 */
    @Override
    public List<Transformation<?>> getInputs() {
        return List.of();
    }
}
