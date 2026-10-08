package io.yak.ops.core.api.connector.source;

/**
 * Runtime 提供给单个 SourceReader 的最小运行上下文。
 *
 * <p>这里不包含数据库连接、具体 Split 或产品任务信息。
 *
 * @author weifuwan
 */
public interface SourceReaderContext {

    /** 返回当前 Reader 的子任务编号，从 0 开始。 */
    int getIndexOfSubtask();

    /** 返回 Source 的当前并行度。 */
    int currentParallelism();

    /** 由 Runtime 转发一个分片请求给对应的 SplitEnumerator。 */
    void sendSplitRequest();
}
