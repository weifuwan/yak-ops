package org.apache.flink.cdc.connectors.base.source.utils.hooks;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.cdc.connectors.base.config.SourceConfig;
import org.apache.flink.util.function.BiConsumerWithException;

import java.io.Serializable;
import java.sql.SQLException;

/**
 * Hook to be invoked during different stages in the snapshot phase.
 *
 * <p>Please note that implementations should be serializable in order to be used in integration
 * tests, as the hook need to be serialized together with the source on job submission.
 */
@Internal
@FunctionalInterface
public interface SnapshotPhaseHook
        extends BiConsumerWithException<SourceConfig, SourceSplit, SQLException>, Serializable {}
