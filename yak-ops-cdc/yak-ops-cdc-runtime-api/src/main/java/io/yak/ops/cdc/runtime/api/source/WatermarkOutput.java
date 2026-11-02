package io.yak.ops.cdc.runtime.api.source;

/** Watermark signaling contract inherited by source outputs. */
public interface WatermarkOutput {
    void emitWatermark(Watermark watermark);

    void markIdle();
}
