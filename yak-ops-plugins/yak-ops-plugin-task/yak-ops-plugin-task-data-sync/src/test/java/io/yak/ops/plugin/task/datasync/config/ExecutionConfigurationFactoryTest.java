package io.yak.ops.plugin.task.datasync.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies execution settings and checkpoint ownership independently of Connector settings. */
class ExecutionConfigurationFactoryTest {
    @TempDir
    Path stateRoot;

    private static final TaskExecutionIdentity FIRST =
            new TaskExecutionIdentity("ws", "definition", 1, "instance-100");

    @Test
    void boundedSourceRunsWithoutPeriodicCheckpointsOrAutomaticRestarts() {
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(JobOptions.defaults(), stateRoot);
        var configuration = factory.create(FIRST, Boundedness.BOUNDED, false);
        assertEquals(RuntimeExecutionMode.BATCH, configuration.get(ExecutionOptions.RUNTIME_MODE));
        assertEquals(false, configuration.get(ExecutionOptions.DEBUG));
        assertEquals(true, factory.create(FIRST, Boundedness.BOUNDED, true).get(ExecutionOptions.DEBUG));
        assertEquals(Duration.ZERO, configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(0, configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, configuration.get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(stateRoot.resolve(FIRST.instanceId()).toAbsolutePath().toString(), configuration.get(CheckpointingOptions.STATE_DIRECTORY));
    }
    @Test
    void continuousSourceUsesCheckpointGatedSameJobRestartsWithoutConnectorSettings() {
        ReadonlyConfig options = runtimeOptions(2, 1, Duration.ofMillis(500), 3);
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(options, stateRoot);
        var configuration = factory.create(FIRST, Boundedness.CONTINUOUS_UNBOUNDED, false);
        assertEquals(RuntimeExecutionMode.STREAMING, configuration.get(ExecutionOptions.RUNTIME_MODE));
        assertEquals(Duration.ofMillis(500), configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(1, configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS));
        assertEquals(3, configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, configuration.get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(false, configuration.contains(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE));
        assertEquals(false, configuration.contains(MySqlCdcSourceOptions.QUEUE_CAPACITY));
        assertEquals(2, options.get(JobOptions.SOURCE_PARALLELISM));
        assertEquals(1, options.get(JobOptions.SINK_PARALLELISM));
    }
    @Test
    void isolatesDifferentInstancesAndKeepsStateDirectoryStable() {
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(JobOptions.defaults(), stateRoot);
        TaskExecutionIdentity sameInstance =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-100");
        TaskExecutionIdentity nextInstance =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-101");

        String first = factory.create(FIRST, Boundedness.CONTINUOUS_UNBOUNDED, false)
                .get(CheckpointingOptions.STATE_DIRECTORY);
        String resumed = factory.create(sameInstance, Boundedness.CONTINUOUS_UNBOUNDED, false)
                .get(CheckpointingOptions.STATE_DIRECTORY);
        String next = factory.create(nextInstance, Boundedness.CONTINUOUS_UNBOUNDED, false)
                .get(CheckpointingOptions.STATE_DIRECTORY);

        assertEquals(first, resumed);
        assertNotEquals(first, next);
        assertEquals(false, factory.create(nextInstance, Boundedness.CONTINUOUS_UNBOUNDED, false).get(CheckpointingOptions.RESTORE_LATEST));
    }
    @Test
    void rejectsUnsafeInstanceIdentifiers() {
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(JobOptions.defaults(), stateRoot);
        TaskExecutionIdentity unsafe =
                new TaskExecutionIdentity("ws", "definition", 1, "../outside");

        assertThrows(IllegalArgumentException.class, () -> factory.create(unsafe, Boundedness.CONTINUOUS_UNBOUNDED, false));
    }
    @Test
    void rejectsInvalidSystemOwnedSettings() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionConfigurationFactory(runtimeOptions(0, 1, Duration.ofSeconds(10), 1), stateRoot));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionConfigurationFactory(runtimeOptions(1, 17, Duration.ofSeconds(10), 1), stateRoot));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionConfigurationFactory(runtimeOptions(1, 1, Duration.ZERO, 1), stateRoot));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionConfigurationFactory(runtimeOptions(1, 1, Duration.ofSeconds(10), -1), stateRoot));
        assertTrue(JobOptions.defaults().get(JobOptions.MAX_RESTARTS) > 0);
    }
    private static ReadonlyConfig runtimeOptions(int sourceParallelism, int sinkParallelism, Duration interval, int maxRestarts) {
        return new Configuration().set(JobOptions.SOURCE_PARALLELISM, sourceParallelism).set(JobOptions.SINK_PARALLELISM, sinkParallelism)
                .set(JobOptions.CHECKPOINT_INTERVAL, interval).set(JobOptions.MAX_RESTARTS, maxRestarts).toReadonlyConfig();
    }
}
