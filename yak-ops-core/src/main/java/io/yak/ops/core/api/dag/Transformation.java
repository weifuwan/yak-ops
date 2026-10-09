package io.yak.ops.core.api.dag;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据流中的一个逻辑转换节点。
 *
 * <p>Transformation 只描述算子及其上游依赖，不执行数据处理，
 * 也不持有线程、连接或运行状态。具体子类负责声明输入关系。
 *
 * <p>构图期间允许配置名称、并行度和 UID；生成执行图后，
 * 调用方不得再修改该节点的配置。
 *
 * @param <T> 当前节点产生的数据类型
 * @author weifuwan
 */
public abstract class Transformation<T> {

    /** 未显式指定并行度时，使用执行配置中的默认值。 */
    public static final int DEFAULT_PARALLELISM = -1;

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** 当前 JVM 内自动分配的构图标识，不作为持久化状态的稳定标识。 */
    private final int id;

    /** 算子名称，用于日志和拓扑展示。 */
    private String name;

    /** 算子输出的 Java 类型；具体字段 Schema 由后续类型系统描述。 */
    private final Class<T> outputType;

    /** 算子并行度，-1 表示继承默认并行度。 */
    private int parallelism;

    /** 用户指定的稳定算子标识，用于后续状态恢复与算子匹配。 */
    private String uid;

    protected Transformation(String name, Class<T> outputType) {
        this(name, outputType, DEFAULT_PARALLELISM);
    }

    protected Transformation(String name, Class<T> outputType, int parallelism) {
        this.id = ID_COUNTER.incrementAndGet();
        this.outputType = Objects.requireNonNull(outputType, "outputType 不能为空");
        setName(name);
        setParallelism(parallelism);
    }

    /** 获取构图标识。 */
    public final int getId() {
        return id;
    }

    /** 获取算子名称。 */
    public final String getName() {
        return name;
    }

    /** 设置算子名称。 */
    public final void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        this.name = name;
    }

    /** 获取当前节点输出的 Java 类型。 */
    public final Class<T> getOutputType() {
        return outputType;
    }

    /** 获取算子并行度；-1 表示使用执行配置中的默认值。 */
    public final int getParallelism() {
        return parallelism;
    }

    /** 设置算子并行度；允许 -1 或正整数。 */
    public final void setParallelism(int parallelism) {
        if (parallelism != DEFAULT_PARALLELISM && parallelism <= 0) {
            throw new IllegalArgumentException("parallelism 必须为 -1 或正整数");
        }
        this.parallelism = parallelism;
    }

    /** 获取稳定算子 UID；未设置时返回 null。 */
    public final String getUid() {
        return uid;
    }

    /** 设置稳定算子 UID，同一个 Pipeline 内不得重复。 */
    public final void setUid(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid 不能为空");
        }
        this.uid = uid;
    }

    /**
     * 返回直接上游的逻辑转换节点。
     *
     * <p>Source 节点返回空列表；单输入节点返回一个元素；
     * 多输入节点返回全部直接上游。不得返回 null。
     *
     * <p>子类应返回不可修改的列表，避免外部破坏图结构。
     *
     * @return 当前节点的直接上游
     */
    public abstract List<Transformation<?>> getInputs();
}
