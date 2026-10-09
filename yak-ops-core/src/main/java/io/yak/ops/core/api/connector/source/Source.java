package io.yak.ops.core.api.connector.source;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;

/**
 * 批流统一的数据源定义，负责创建分片枚举器和读取器。
 *
 * <p>Source 是可复用的组件配置，不应持有某次作业专属的活动连接或线程。
 * Boundedness 描述数据是否有界，不等同于实际选择的 BATCH/STREAMING 运行模式。
 *
 * @param <T> 产出的记录类型
 * @param <SplitT> 分片类型
 * @param <EnumStateT> Enumerator 的检查点状态类型
 * @author weifuwan
 */
public interface Source<T, SplitT extends SourceSplit, EnumStateT> {

    /** 返回数据源的有界性。 */
    Boundedness getBoundedness();

    /**
     * 为一次全新执行创建 Enumerator，不得复用其他作业的运行实例。
     *
     * @param context Runtime 提供的协调上下文
     */
    SplitEnumerator<SplitT, EnumStateT> createEnumerator(SplitEnumeratorContext<SplitT> context) throws Exception;

    /**
     * 从已完成的检查点恢复 Enumerator。
     *
     * <p>返回对象尚未启动，由 Runtime 在恰当阶段调用 start()。
     *
     * @param context 本次运行的协调上下文
     * @param checkpointState 反序列化后的 Enumerator 状态
     */
    SplitEnumerator<SplitT, EnumStateT> restoreEnumerator(
            SplitEnumeratorContext<SplitT> context, EnumStateT checkpointState) throws Exception;

    /**
     * 为一个并行 Source 子任务创建独立 Reader。
     * Reader 恢复时由 Runtime 通过 addSplits() 重新交付包含进度的分片。
     */
    SourceReader<T, SplitT> createReader(SourceReaderContext context) throws Exception;

    /** 用于分片传输、Reader 状态快照持久化与恢复的版本化序列化器。 */
    SimpleVersionedSerializer<SplitT> getSplitSerializer();

    /** 用于 Enumerator 检查点状态持久化与恢复的版本化序列化器。 */
    SimpleVersionedSerializer<EnumStateT> getEnumeratorCheckpointSerializer();
}
