package org.apache.flink.cdc.connectors.base.source.meta.wartermark;

/** The watermark kind. */
public enum WatermarkKind {
    LOW,
    HIGH,
    END;

    public WatermarkKind fromString(String kindString) {
        if (LOW.name().equalsIgnoreCase(kindString)) {
            return LOW;
        } else if (HIGH.name().equalsIgnoreCase(kindString)) {
            return HIGH;
        } else {
            return END;
        }
    }
}
