package io.yak.ops.core.api.connector.source;

/**
 * Source 输出的数据流有界性。
 *
 * <p>此属性描述 Source 的数据边界，不等同于作业最终选择的运行模式。
 */
public enum Boundedness {
    /** 有界数据流，例如一次全量快照读取。 */
    BOUNDED,

    /** 持续无界数据流，例如长期运行的 CDC 订阅。 */
    CONTINUOUS_UNBOUNDED
}
