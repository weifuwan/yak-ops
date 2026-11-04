package org.apache.flink.cdc.connectors.base.source.utils.hooks;

import javax.annotation.Nullable;

import java.io.Serializable;

/**
 * A container class for hooks applied in the snapshot phase, including:
 *
 * <ul>
 *   <li>{@link #preHighWatermarkAction}: Hook to run before emitting high watermark, which is for
 *       testing whether stream events created within snapshot phase are backfilled correctly.
 *   <li>{@link #postHighWatermarkAction}: Hook to run after emitting high watermark, which is for
 *       testing actions handling stream events between snapshot splits.
 * </ul>
 */
public class SnapshotPhaseHooks implements Serializable {
    private static final long serialVersionUID = 1L;

    private SnapshotPhaseHook preLowWatermarkAction;
    private SnapshotPhaseHook postLowWatermarkAction;
    private SnapshotPhaseHook preHighWatermarkAction;
    private SnapshotPhaseHook postHighWatermarkAction;

    public void setPreHighWatermarkAction(SnapshotPhaseHook preHighWatermarkAction) {
        this.preHighWatermarkAction = preHighWatermarkAction;
    }

    public void setPostHighWatermarkAction(SnapshotPhaseHook postHighWatermarkAction) {
        this.postHighWatermarkAction = postHighWatermarkAction;
    }

    public void setPreLowWatermarkAction(SnapshotPhaseHook preLowWatermarkAction) {
        this.preLowWatermarkAction = preLowWatermarkAction;
    }

    public void setPostLowWatermarkAction(SnapshotPhaseHook postLowWatermarkAction) {
        this.postLowWatermarkAction = postLowWatermarkAction;
    }

    @Nullable
    public SnapshotPhaseHook getPreHighWatermarkAction() {
        return preHighWatermarkAction;
    }

    @Nullable
    public SnapshotPhaseHook getPostHighWatermarkAction() {
        return postHighWatermarkAction;
    }

    @Nullable
    public SnapshotPhaseHook getPreLowWatermarkAction() {
        return preLowWatermarkAction;
    }

    @Nullable
    public SnapshotPhaseHook getPostLowWatermarkAction() {
        return postLowWatermarkAction;
    }

    public static SnapshotPhaseHooks empty() {
        return new SnapshotPhaseHooks();
    }
}
