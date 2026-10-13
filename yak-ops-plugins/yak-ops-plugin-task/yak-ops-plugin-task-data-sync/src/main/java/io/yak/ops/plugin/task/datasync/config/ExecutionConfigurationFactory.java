package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates the YakFlow execution configuration for an admitted Task Instance.
 *
 * <p>The Task platform owns cross-process recovery. New instances do not restore another
 * instance's checkpoint; restarts inside one Job require completed checkpoints.
 */
public final class ExecutionConfigurationFactory {

    private final ReadonlyConfig options;
    private final Path stateRoot;

    /** Creates an engine configuration factory with application-owned execution settings. */
    public ExecutionConfigurationFactory(ReadonlyConfig options, Path stateRoot) {
        this.options = Objects.requireNonNull(options, "options");
        ConfigValidator.of(options).validate(JobOptions.RUNTIME_RULE);
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
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(boundedness, "boundedness");
        Path stateDirectory = stateDirectory(identity.instanceId());
        Configuration configuration = new Configuration()
                .set(PipelineOptions.AUTO_GENERATE_UIDS, false)
                .set(CheckpointingOptions.STATE_DIRECTORY, stateDirectory.toString())
                .set(ExecutionOptions.LOG_ID, identity.instanceId())
                .set(ExecutionOptions.DEBUG, debug)
                .set(CheckpointingOptions.RESTORE_LATEST, false);

        if (boundedness == Boundedness.BOUNDED) {
            return configuration
                    .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.BATCH)
                    .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ZERO)
                    .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 0);
        }

        return configuration
                .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.STREAMING)
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, options.get(JobOptions.CHECKPOINT_INTERVAL))
                .set(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS, 1)
                .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, options.get(JobOptions.MAX_RESTARTS));
    }

    private Path stateDirectory(String instanceId) {
        if (!instanceId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid DATA_SYNC Instance identifier for checkpoint storage");
        }
        return stateRoot.resolve(instanceId);
    }
}
