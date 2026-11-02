package io.yak.ops.cdc.runtime.api.source;

import java.io.Serializable;

/** Source event-time watermark value. The standalone runtime can reject unsupported usage. */
public final class Watermark implements Serializable {
    private static final long serialVersionUID = 1L;
    private final long timestamp;

    public Watermark(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
