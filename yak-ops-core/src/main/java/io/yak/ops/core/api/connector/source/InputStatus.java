package io.yak.ops.core.api.connector.source;

/** Result of one non-blocking {@link SourceReader#pollNext(ReaderOutput)} call. */
public enum InputStatus {
    /** More data can be consumed immediately. */
    MORE_AVAILABLE,
    /** No data is ready; suspend polling until the availability future completes. */
    NOTHING_AVAILABLE,
    /** All assigned splits are exhausted and no further splits will arrive. */
    END_OF_INPUT
}
