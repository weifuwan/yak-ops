package io.yak.ops.core.api.connector.source;

import io.yak.ops.core.configuration.Configuration;

/**
 * Runtime 提供给单个 SourceReader 的最小运行上下文。
 *
 * <p>这里不包含数据库连接、具体 Split 或产品任务信息。
 *
 * @author weifuwan
 */
public interface SourceReaderContext {

    /** 返回任务提交时冻结的配置快照副本，修改返回对象不能影响 Runtime。 */
    Configuration getConfiguration();

    /** 返回当前 Reader 的子任务编号，从 0 开始。 */
    int getIndexOfSubtask();

    /** 返回 Source 的当前并行度。 */
    int currentParallelism();

    /** 由 Runtime 转发一个分片请求给对应的 SplitEnumerator。 */
    void sendSplitRequest();

    /** Send a connector-specific event via the Runtime's attempt-aware coordinator gateway. */
    default void sendSourceEventToCoordinator(SourceEvent event) {
        throw new UnsupportedOperationException("This context does not support SourceEvent transport");
    }
}
