package io.yak.ops.core.api.connector.source;

import java.util.List;

/**
 * 发现、生成并分配 SourceSplit 的 Connector 实例。
 *
 * <p>不提供 nextSplit() 轮询模型。Enumerator 通过 SplitEnumeratorContext 将 Split
 * 分配给 Reader。Runtime 在协调器线程中串行调用本接口的生命周期和状态方法。
 * 较慢的元数据查询应使用 Context 的异步执行能力，不应阻塞协调器线程。
 *
 * <p>Enumerator 负责分配工作；分配完成不代表 Reader 完成数据读取。
 *
 * @param <SplitT> 分片类型
 * @param <EnumStateT> Enumerator 的检查点状态类型
 * @author weifuwan
 */
public interface SplitEnumerator<SplitT extends SourceSplit, EnumStateT> extends AutoCloseable {

    /** 开始发现或分配分片；从检查点恢复时同样只调用一次。 */
    void start() throws Exception;

    /** 处理 Reader 的分片请求，可以分配分片，也可以等待后续数据可用。 */
    void handleSplitRequest(int subtaskId) throws Exception;

    /** 有 Reader 注册或重新注册时调用。 */
    void addReader(int subtaskId) throws Exception;

    /**
     * Reader 故障时接收需要重新分配的分片。
     *
     * <p>传入的 Split 必须对应正确的恢复进度，不允许简单地从头重新读取，
     * 除非该 Connector 明确支持这种重放语义。
     */
    void addSplitsBack(List<SplitT> splits, int subtaskId) throws Exception;

    /**
     * 快照尚未分配的工作、已完成的枚举进度以及其它必要协调状态。
     * Reader 已接收的分片进度由 Reader.snapshotState() 保存，不能重复计入两侧。
     */
    EnumStateT snapshotState(long checkpointId) throws Exception;

    /** 对应 Checkpoint 已持久化并成功完成后的通知；默认不需要处理。 */
    default void notifyCheckpointComplete(long checkpointId) throws Exception {}

    /** 释放 Enumerator 的资源。失败、取消或正常结束时均应尽力调用。 */
    @Override
    void close() throws Exception;
}
