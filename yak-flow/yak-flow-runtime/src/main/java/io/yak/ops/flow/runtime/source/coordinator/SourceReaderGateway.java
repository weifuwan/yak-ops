package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * SourceCoordinator 向某个 Reader Task 投递事件的异步通道。
 *
 * <p>此接口由 Runtime 的 Reader Task 实现，不是 Connector 的 SourceReader。
 * 方法必须立即返回，不得在协调器线程里执行阻塞读取。
 * Future 正常完成表示该事件已经由目标 Reader Task 实际处理，而不只是放入邮箱。
 *
 * @param <SplitT> Source 分片类型
 * @author weifuwan
 */
public interface SourceReaderGateway<SplitT extends SourceSplit> {

    /**
     * 向指定 Reader Task 交付分片。
     *
     * <p>Future 成功完成表示 SourceReader.addSplits 已经执行成功；
     * 不代表 Split 已读取完成，也不代表记录已经持久化。
     */
    CompletionStage<Void> addSplits(List<SplitT> splits);

    /**
     * 通知 Reader 不会收到新的分片。该通知必须排在之前的分片交付之后处理。
     * Future 成功完成表示 SourceReader.notifyNoMoreSplits 已调用完成。
     */
    CompletionStage<Void> noMoreSplits();
}
