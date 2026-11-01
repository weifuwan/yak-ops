package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.api.connector.sink.BatchExecutionHookException;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/** Durable bounded-job phases, independent of replayable operator checkpoints. */
public final class BatchExecutionJournal implements AutoCloseable {
    private static final int MAGIC = 0x59414b42;
    private static final int VERSION = 1;
    private static final byte PENDING = 0;
    private static final byte STARTED = 1;
    private static final byte COMPLETED = 2;
    private static final byte FAILED = 3;
    private final Path directory;
    private final Path stateFile;
    private final String signature;
    private final List<BatchExecutionHook> hooks;
    private final byte[] states;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private byte dataState;

    /**
     * Reports whether an instance owns batch phase state, including unreadable or corrupt payloads.
     * Callers enable explicit recovery when this returns true; only the runtime validates the bytes.
     *
     * @param directory trusted instance-local state directory
     * @return whether a journal entry exists
     * @throws IOException when the directory cannot be inspected safely
     */
    public static boolean hasExecutionState(Path directory) throws IOException {
        try {
            Files.readAttributes(
                    directory.resolve("batch-execution.bin"),
                    java.nio.file.attribute.BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (java.nio.file.NoSuchFileException missing) {
            return false;
        }
    }

    BatchExecutionJournal(JobGraph graph, List<BatchExecutionHook> hooks) throws IOException {
        if (!graph.isBounded() || hooks.isEmpty()) {
            throw new IllegalArgumentException("Execution hooks require a bounded job and at least one operation");
        }
        String stateDirectory = graph.configuration().get(CheckpointingOptions.STATE_DIRECTORY);
        String identity = graph.configuration().get(CheckpointingOptions.DEFINITION_IDENTITY);
        if (stateDirectory == null || stateDirectory.isBlank() || identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Execution hooks require a durable instance directory and identity");
        }
        this.directory = Path.of(stateDirectory).toAbsolutePath().normalize();
        this.stateFile = directory.resolve("batch-execution.bin");
        this.hooks = List.copyOf(hooks);
        if (hooks.size() > 10_000) throw new IllegalArgumentException("Too many batch execution hooks");
        this.states = new byte[hooks.size()];
        this.signature = signature(graph, this.hooks);
        Files.createDirectories(directory);
        this.lockChannel = FileChannel.open(
                directory.resolve(".batch-execution.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException conflict) {
            acquired = null;
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IOException("Batch execution directory is owned by another running job");
        }
        this.lock = acquired;
        try {
            if (Files.exists(stateFile)) {
                if (!graph.configuration().get(CheckpointingOptions.RESTORE_LATEST)) {
                    throw new IOException("Batch execution state already exists; explicit recovery is required");
                }
                load();
            } else {
                if (graph.configuration().get(CheckpointingOptions.RESTORE_LATEST)) {
                    try (LocalCompletedCheckpointStore checkpoints = new LocalCompletedCheckpointStore(directory)) {
                        String graphSignature = graphSignature(graph);
                        if (checkpoints.loadLatest(graphSignature).isPresent()) {
                            throw new IOException("Checkpoint exists without its required batch execution journal");
                        }
                    }
                }
                persist();
            }
        } catch (IOException | RuntimeException failure) {
            try {
                close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    boolean dataStarted() {
        return dataState == STARTED;
    }

    boolean dataCompleted() {
        return dataState == COMPLETED;
    }

    boolean hasUnconfirmedHook() {
        for (byte state : states) if (state == STARTED) return true;
        return false;
    }

    void beginData() throws IOException {
        if (dataState == COMPLETED) throw new IllegalStateException("Completed data cannot be redeployed");
        dataState = STARTED;
        persist();
    }

    void completeData() throws IOException {
        dataState = COMPLETED;
        persist();
    }

    void execute(BatchExecutionHook.Phase phase, BooleanSupplier canceled, Consumer<BatchExecutionHook> active)
            throws Exception {
        for (int index = 0; index < hooks.size(); index++) {
            BatchExecutionHook hook = hooks.get(index);
            if (hook.phase() != phase || states[index] == COMPLETED) continue;
            if (states[index] == STARTED) {
                throw new BatchExecutionHookException(
                        "A prior batch SQL operation has an unknown outcome and cannot be replayed", null, true);
            }
            if (canceled.getAsBoolean()) throw new CancellationException("Batch execution was cancelled");
            states[index] = STARTED;
            persist();
            active.accept(hook);
            try {
                hook.execute();
            } catch (BatchExecutionHookException failure) {
                if (!failure.isOutcomeUnknown()) {
                    states[index] = FAILED;
                    persist();
                }
                throw failure;
            } finally {
                active.accept(null);
            }
            states[index] = COMPLETED;
            persist();
        }
    }

    private void load() throws IOException {
        if (Files.size(stateFile) > 65_536) throw new IOException("Batch journal exceeds its supported size");
        byte[] contents = Files.readAllBytes(stateFile);
        if (contents.length < 8) throw new IOException("Batch journal is incomplete");
        CRC32 crc = new CRC32();
        crc.update(contents, 0, contents.length - Long.BYTES);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(contents))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || !signature.equals(in.readUTF())) {
                throw new IOException("Batch journal does not match the submitted definition and hooks");
            }
            dataState = in.readByte();
            if (dataState < PENDING || dataState > COMPLETED || in.readInt() != states.length) {
                throw new IOException("Invalid batch execution state");
            }
            in.readFully(states);
            for (byte state : states) {
                if (state < PENDING || state > FAILED) throw new IOException("Invalid batch hook state");
            }
            for (int index = 0; index < states.length; index++) {
                if ((dataState != PENDING
                                && hooks.get(index).phase() == BatchExecutionHook.Phase.BEFORE_DATA
                                && states[index] != COMPLETED)
                        || (dataState != COMPLETED
                                && hooks.get(index).phase() == BatchExecutionHook.Phase.AFTER_DATA
                                && states[index] != PENDING)) {
                    throw new IOException("Batch hook states do not match the durable data phase");
                }
            }
            if (in.readLong() != crc.getValue() || in.available() != 0) {
                throw new IOException("Batch journal checksum or length is invalid");
            }
        }
    }

    private void persist() throws IOException {
        byte[] contents;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeUTF(signature);
            output.writeByte(dataState);
            output.writeInt(states.length);
            output.write(states);
            output.flush();
            CRC32 crc = new CRC32();
            crc.update(bytes.toByteArray());
            output.writeLong(crc.getValue());
            output.flush();
            contents = bytes.toByteArray();
        }
        Path temporary = directory.resolve(".batch-execution-" + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel output =
                    FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(contents);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) {
                parent.force(true);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String signature(JobGraph graph, List<BatchExecutionHook> hooks) {
        StringBuilder value = new StringBuilder(graphSignature(graph));
        for (BatchExecutionHook hook : hooks) {
            if (hook.phase() == null
                    || hook.fingerprint() == null
                    || !hook.fingerprint().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Batch hook requires a stable operation identity");
            }
            value.append('|').append(hook.phase()).append('|').append(hook.fingerprint());
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static String graphSignature(JobGraph graph) {
        return LocalCompletedCheckpointStore.graphSignature(
                graph.graph(),
                graph.configuration().get(PipelineOptions.MAX_PARALLELISM),
                graph.configuration().get(CheckpointingOptions.DEFINITION_IDENTITY));
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            lockChannel.close();
        }
    }
}
