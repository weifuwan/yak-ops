package io.yak.ops.core.api.connector.source;

/**
 * Source 组件的最小设计契约。
 *
 * <p>本阶段仅为 SourceTransformation 提供类型化组件引用和有界性声明。
 * Reader、Split、Enumerator、Checkpoint 等执行协议尚未设计，
 * 不能仅凭此接口读取数据。后续设计完整 Source API 时再扩展。
 *
 * @param <T> Source 产生的数据类型
 */
public interface Source<T> {

    /** 返回 Source 产生的数据流是有界还是持续无界。 */
    Boundedness getBoundedness();
}
