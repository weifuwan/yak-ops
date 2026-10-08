package io.yak.ops.core.api.connector.source;

/** SourceReader 一次非阻塞 pollNext() 的结果。 */
public enum InputStatus {
    /** 当前仍有数据可立即处理。 */
    MORE_AVAILABLE,
    /** 当前没有数据，应等待 isAvailable()。 */
    NOTHING_AVAILABLE,
    /** 所有分片已处理完且不再有新分片，输入正式结束。 */
    END_OF_INPUT
}
