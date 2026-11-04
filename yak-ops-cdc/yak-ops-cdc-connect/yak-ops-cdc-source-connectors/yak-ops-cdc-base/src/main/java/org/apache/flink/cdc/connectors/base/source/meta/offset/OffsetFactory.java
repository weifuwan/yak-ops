package org.apache.flink.cdc.connectors.base.source.meta.offset;

import java.io.Serializable;
import java.util.Map;

/** An offset factory class create {@link Offset} instance. */
public abstract class OffsetFactory implements Serializable {
    private static final long serialVersionUID = 1L;

    public OffsetFactory() {}

    public abstract Offset newOffset(Map<String, String> offset);

    public abstract Offset newOffset(String filename, Long position);

    public abstract Offset newOffset(Long position);

    public abstract Offset createTimestampOffset(long timestampMillis);

    public abstract Offset createInitialOffset();

    public abstract Offset createNoStoppingOffset();
}
