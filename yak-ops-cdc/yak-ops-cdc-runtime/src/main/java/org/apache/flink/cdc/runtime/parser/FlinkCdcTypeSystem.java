package org.apache.flink.cdc.runtime.parser;

import org.apache.flink.cdc.common.pipeline.DecimalPrecisionMode;

import org.apache.calcite.rel.type.RelDataTypeSystemImpl;

/** A customized version of {@link org.apache.calcite.rel.type.RelDataTypeSystem}. */
public class FlinkCdcTypeSystem extends RelDataTypeSystemImpl {

    public static final FlinkCdcTypeSystem UP_TO_38 = new FlinkCdcTypeSystem(38);
    public static final FlinkCdcTypeSystem UP_TO_19 = new FlinkCdcTypeSystem(19);

    private final int maxPrecision;

    private FlinkCdcTypeSystem(int maxPrecision) {
        this.maxPrecision = maxPrecision;
    }

    public static FlinkCdcTypeSystem of(DecimalPrecisionMode mode) {
        switch (mode) {
            case UP_TO_38:
                return UP_TO_38;
            case UP_TO_19:
                return UP_TO_19;
            default:
                throw new IllegalArgumentException("Unexpected decimal precision mode: " + mode);
        }
    }

    @Override
    public int getMaxNumericPrecision() {
        return maxPrecision;
    }

    @Override
    public int getMaxNumericScale() {
        return maxPrecision;
    }
}
