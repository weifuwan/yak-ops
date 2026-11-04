package org.apache.flink.cdc.connectors.base.config;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.options.StartupOptions;

import java.io.Serializable;

/** The source configuration which offers basic source configuration. */
@Experimental
public interface SourceConfig extends Serializable {

    StartupOptions getStartupOptions();

    int getSplitSize();

    int getSplitMetaGroupSize();

    boolean isIncludeSchemaChanges();

    boolean isCloseIdleReaders();

    boolean isSkipSnapshotBackfill();

    boolean isScanNewlyAddedTableEnabled();

    boolean isAssignUnboundedChunkFirst();

    /**
     * Whether releasing the snapshot split metadata held by the source coordinator once the stream
     * split is assembled and checkpoint-covered is enabled. Default {@code false} so existing jobs
     * and configs that do not set the option are unaffected.
     */
    default boolean isReleaseSnapshotMetadataEnabled() {
        return false;
    }

    /** Factory for the {@code SourceConfig}. */
    @FunctionalInterface
    interface Factory<C extends SourceConfig> extends Serializable {

        C create(int subtask);
    }
}
