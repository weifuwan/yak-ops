package io.yak.ops.composer.yakflow;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates the YakFlow execution configuration for an admitted Task Instance.
 *
 * <p>Checkpoints are scoped to a persistent Task Instance. A relaunched Instance restores its
 * existing checkpoint; newly submitted Instances start without restored state.
 */
public final class ExecutionConfigurationFactory {

    private final ReadonlyConfig options;
    private final Path stateRoot;

    /** Creates an engine configuration factory with application-owned execution settings. */
    public ExecutionConfigurationFactory(ReadonlyConfig options, Path stateRoot) {
        this.options = Objects.requireNonNull(options, "options");
        ConfigValidator.of(options).validate(EnvironmentOptions.RULE);
        this.stateRoot =
                Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
    }

    /**
     * Creates a fresh typed configuration for one physical submission.
     *
     * @param identity trusted Task Instance identity
     * @param boundedness Source boundedness declared by its factory
     * @param debug detailed diagnostics for the submitted Instance
     * @return independently mutable YakFlow configuration
     */
    public Configuration create(TaskExecutionIdentity identity, Boundedness boundedness, boolean debug) {
        return create(identity, boundedness, debug, false);
    }

    /** Enables schema coordination only for explicitly configured continuous tasks. */
    public Configuration create(
            TaskExecutionIdentity identity, Boundedness boundedness, boolean debug, boolean schemaChangesEnabled) {
        return create(identity, boundedness, debug, schemaChangesEnabled, false);
    }

    /** Destructive target DDL is permitted only when both runtime flags are active. */
    public Configuration create(
            TaskExecutionIdentity identity,
            Boundedness boundedness,
            boolean debug,
            boolean schemaChangesEnabled,
            boolean allowDropColumns) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(boundedness, "boundedness");
        if (schemaChangesEnabled && boundedness == Boundedness.BOUNDED) {
            throw new IllegalArgumentException("Batch pipelines cannot enable runtime schema changes");
        }
        if (allowDropColumns && !schemaChangesEnabled) {
            throw new IllegalArgumentException("DROP COLUMN requires runtime schema evolution");
        }
        Path stateDirectory = stateDirectory(identity.instanceId());
        Configuration configuration = new Configuration()
                .set(PipelineOptions.AUTO_GENERATE_UIDS, false)
                .set(CheckpointingOptions.STATE_DIRECTORY, stateDirectory.toString())
                .set(CheckpointingOptions.DEFINITION_IDENTITY, checkpointDefinitionIdentity(identity))
                .set(ExecutionOptions.LOG_ID, identity.instanceId())
                .set(ExecutionOptions.DEBUG, debug)
                .set(SchemaEvolutionOptions.ENABLED, schemaChangesEnabled)
                .set(SchemaEvolutionOptions.ALLOW_DROP_COLUMNS, allowDropColumns)
                .set(CheckpointingOptions.RESTORE_LATEST, false);

        if (boundedness == Boundedness.BOUNDED) {
            return configuration
                    .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.BATCH)
                    .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ZERO)
                    .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 0);
        }

        // Only the same Instance can own this directory. A corrupted checkpoint must fail restore,
        // not silently restart CDC from the beginning and duplicate committed target rows.
        return configuration
                .set(CheckpointingOptions.RESTORE_LATEST, hasCheckpointData(stateDirectory))
                .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.STREAMING)
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, options.get(EnvironmentOptions.CHECKPOINT_INTERVAL))
                .set(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS, 1)
                .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, options.get(EnvironmentOptions.MAX_RESTARTS));
    }

    private static boolean hasCheckpointData(Path directory) {
        try {
            return LocalCompletedCheckpointStore.hasCheckpointData(directory);
        } catch (java.io.IOException failure) {
            // State inspection failures must not allow a new CDC run at a fresh offset.
            throw new IllegalStateException("Cannot inspect existing checkpoint state", failure);
        }
    }

    private static String checkpointDefinitionIdentity(TaskExecutionIdentity identity) {
        return identity.workspaceId() + ":" + identity.definitionId() + ":" + identity.definitionVersion() + ":"
                + identity.instanceId();
    }

    private Path stateDirectory(String instanceId) {
        if (!instanceId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid DATA_SYNC Instance identifier for checkpoint storage");
        }
        return stateRoot.resolve(instanceId);
    }
}
