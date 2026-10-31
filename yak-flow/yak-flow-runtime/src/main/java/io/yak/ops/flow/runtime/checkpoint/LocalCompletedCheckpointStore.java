package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.state.filesystem.CheckpointMetadataOutputStream;
import io.yak.ops.flow.runtime.state.filesystem.FsCheckpointStorageAccess;
import io.yak.ops.flow.runtime.state.filesystem.FsCheckpointStorageLocation;
import io.yak.ops.flow.runtime.state.filesystem.FsCompletedCheckpointStorageLocation;
import io.yak.ops.flow.runtime.state.filesystem.LocalRecoverableWriter;
import io.yak.ops.flow.runtime.state.filesystem.RecoverableFsDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exclusive local checkpoint store for a single running job.
 *
 * <p>Uses versioned binary data with CRC validation, without Java object serialization.
 * A checkpoint is published via Flink 1.20.3's recoverable writer and an immutable completion marker.
 * Corrupted or incompatible state is rejected rather than silently ignored.
 */
public final class LocalCompletedCheckpointStore implements CompletedCheckpointStore {

    private static final Logger LOG = LoggerFactory.getLogger(LocalCompletedCheckpointStore.class);

    private static final int MAGIC = 0x59414B43; // YAKC
    private static final int POINTER_VERSION = 2;
    private static final String CHECKPOINT_DIRECTORY = "checkpoints";
    private static final String METADATA_FILE = "_metadata";
    private static final String COMPLETED_DIRECTORY = "completed";
    private static final String LEGACY_POINTER = "checkpoint.bin";

    private final Path directory;
    private final FsCheckpointStorageAccess checkpointStorage;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;
    private boolean latestCheckpointKnown;
    private long lastCheckpointId;

    public LocalCompletedCheckpointStore(Path directory) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory must not be null")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(this.directory);
        this.checkpointStorage = new FsCheckpointStorageAccess(this.directory);
        lockChannel = FileChannel.open(
                this.directory.resolve(".checkpoint.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException error) {
            acquired = null;
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IllegalStateException("Checkpoint state directory is locked by another job");
        }
        lock = acquired;
    }

    /**
     * Loads the most recently completed checkpoint.
     *
     * <p>Rejects snapshots whose topology, stable operator UIDs or parallelism differ
     * from the current job's graph signature.
     *
     * @param expectedGraphSignature the fingerprint of the job being restored
     * @return the latest completed checkpoint if present
     * @throws IOException if the persisted data is corrupt or unreadable
     */
    public Optional<CheckpointSnapshot> loadLatest(String expectedGraphSignature) throws IOException {
        ensureOpen();
        Objects.requireNonNull(expectedGraphSignature, "expectedGraphSignature must not be null");
        Path completedDirectory = directory.resolve(COMPLETED_DIRECTORY);
        long publishedId = latestCompletedId(completedDirectory);
        if (publishedId == 0L) {
            if (Files.exists(directory.resolve(LEGACY_POINTER))) {
                throw new IOException("Legacy checkpoint.bin exists. Explicit state migration is required");
            }
            return Optional.empty();
        }
        Path marker = completedDirectory.resolve("chk-" + publishedId);
        String pointer = readCompletedPointer(marker, publishedId);
        FsCompletedCheckpointStorageLocation location = checkpointStorage.resolveCheckpoint(pointer);
        Path file = location.getMetadataFilePath();
        CheckpointSnapshot snapshot = CheckpointMetadataSerializer.deserialize(Files.readAllBytes(file));
        if (snapshot.checkpointId() != publishedId) {
            throw new IOException("Checkpoint pointer references a different checkpoint ID");
        }
        if (!expectedGraphSignature.equals(snapshot.graphSignature())) {
            throw new IllegalStateException(
                    "Checkpoint graph, operator UID or parallelism does not match the current job");
        }
        return Optional.of(snapshot);
    }

    /**
     * Publishes a completed checkpoint using an atomic file replacement.
     *
     * <p>Only after successful persistence may the runtime acknowledge completion
     * to the Reader and Enumerator.
     *
     * @throws IOException if durable publication fails
     */
    public void save(CheckpointSnapshot checkpoint) throws IOException {
        ensureOpen();
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        // Never overwrite a corrupt, incompatible or later durable checkpoint with a fresh run.
        if (!latestCheckpointKnown) {
            lastCheckpointId = loadLatest(checkpoint.graphSignature())
                    .map(CheckpointSnapshot::checkpointId)
                    .orElse(0L);
            latestCheckpointKnown = true;
        }
        if (checkpoint.checkpointId() <= lastCheckpointId) {
            throw new IOException(
                    "Checkpoint ID must advance beyond the latest durable checkpoint: " + lastCheckpointId);
        }
        byte[] metadata = CheckpointMetadataSerializer.serialize(checkpoint);
        // Flink's CheckpointStorageLocation / MetadataOutputStream owns file persistence.
        Path metadataFile = checkpointMetadata(checkpoint.checkpointId());
        Path marker = directory.resolve(COMPLETED_DIRECTORY).resolve("chk-" + checkpoint.checkpointId());
        if (Files.exists(marker)) {
            throw new IOException("Completed checkpoint marker already exists: " + marker);
        }
        // Interrupted earlier attempts can leave a committed but unpublished _metadata.
        // Only cleanup such orphan state while holding this task instance's exclusive lock.
        if (Files.exists(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unexpected checkpoint metadata object: " + metadataFile);
            }
            Files.delete(metadataFile);
        }
        FsCheckpointStorageLocation target =
                checkpointStorage.initializeLocationForCheckpoint(checkpoint.checkpointId());
        try (CheckpointMetadataOutputStream out = target.createMetadataOutputStream()) {
            out.write(metadata);
            out.closeAndFinalizeCheckpoint();
        } catch (IOException | RuntimeException failure) {
            try {
                target.disposeOnFailure();
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        long previousCheckpointId = lastCheckpointId;
        Files.createDirectories(marker.getParent());
        byte[] pointerBytes = completedPointer(
                checkpoint.checkpointId(),
                target.getCheckpointDirectory().toUri().toString());
        try (RecoverableFsDataOutputStream out = new LocalRecoverableWriter().open(marker)) {
            out.write(pointerBytes);
            out.closeForCommit().commit();
        }
        lastCheckpointId = checkpoint.checkpointId();
        try {
            discardSubsumedCheckpoints(checkpoint.checkpointId(), previousCheckpointId);
        } catch (IOException | RuntimeException cleanupFailure) {
            // A completed checkpoint must not become a failed one due to best-effort retention.
            LOG.warn(
                    "Completed checkpoint retention cleanup failed, checkpointId={}",
                    checkpoint.checkpointId(),
                    cleanupFailure);
        }
    }

    private Path checkpointMetadata(long checkpointId) throws IOException {
        if (checkpointId <= 0) {
            throw new IOException("Invalid checkpoint metadata ID");
        }
        return directory
                .resolve(CHECKPOINT_DIRECTORY)
                .resolve("chk-" + checkpointId)
                .resolve(METADATA_FILE);
    }

    /** Used at submission to fail closed when legacy or uncommitted checkpoint state exists. */
    public static boolean hasCheckpointData(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if (Files.exists(directory.resolve(LEGACY_POINTER))) return true;
        Path completed = directory.resolve(COMPLETED_DIRECTORY);
        if (latestCompletedId(completed) > 0) return true;
        Path checkpoints = directory.resolve(CHECKPOINT_DIRECTORY);
        if (Files.isDirectory(checkpoints)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(checkpoints, "chk-*")) {
                if (entries.iterator().hasNext()) return true;
            }
        }
        return false;
    }

    private static long latestCompletedId(Path completedDirectory) throws IOException {
        if (!Files.exists(completedDirectory)) return 0L;
        if (!Files.isDirectory(completedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Checkpoint completed index is not a directory");
        }
        long latest = 0L;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(completedDirectory, "chk-*")) {
            for (Path path : entries) {
                String name = path.getFileName().toString();
                if (!name.matches("chk-[1-9][0-9]*")) continue;
                long id;
                try {
                    id = Long.parseLong(name.substring(4));
                } catch (NumberFormatException invalid) {
                    throw new IOException("Invalid completed checkpoint index filename: " + name, invalid);
                }
                latest = Math.max(latest, id);
            }
        }
        return latest;
    }

    private static byte[] completedPointer(long checkpointId, String externalPointer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(POINTER_VERSION);
            out.writeLong(checkpointId);
            out.writeUTF(externalPointer);
        }
        byte[] content = bytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(content);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeLong(checksum.getValue());
        }
        return bytes.toByteArray();
    }

    private static String readCompletedPointer(Path marker, long expectedId) throws IOException {
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) > 16 * 1024
                || Files.size(marker) < 24) {
            throw new IOException("Invalid completed checkpoint marker: " + marker);
        }
        byte[] data = Files.readAllBytes(marker);
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - Long.BYTES);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != MAGIC || in.readInt() != POINTER_VERSION || in.readLong() != expectedId) {
                throw new IOException("Unsupported completed checkpoint marker or identity");
            }
            String pointer = in.readUTF();
            if (in.available() != Long.BYTES || in.readLong() != crc.getValue()) {
                throw new IOException("Completed checkpoint marker CRC verification failed");
            }
            return pointer;
        }
    }

    /**
     * Keep the latest published checkpoint and its preceding completed checkpoint.
     *
     * <p>Orphaned metadata from failed, unpublished attempts is not eligible for
     * restore. Cleanup is best effort and never invalidates a published checkpoint.
     */
    private void discardSubsumedCheckpoints(long current, long previous) throws IOException {
        Path checkpoints = directory.resolve(CHECKPOINT_DIRECTORY);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(checkpoints, "chk-*")) {
            for (Path child : entries) {
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (!name.matches("chk-[1-9][0-9]*")) {
                    continue;
                }
                long id;
                try {
                    id = Long.parseLong(name.substring(4));
                } catch (NumberFormatException invalid) {
                    continue;
                }
                if (id == current || id == previous) {
                    continue;
                }
                // Never remove arbitrary user files from an unexpected checkpoint directory.
                try (DirectoryStream<Path> metadata = Files.newDirectoryStream(child)) {
                    for (Path file : metadata) {
                        String filename = file.getFileName().toString();
                        if (filename.equals(METADATA_FILE)
                                || (filename.startsWith(".checkpoint-") && filename.endsWith(".pending"))
                                || filename.startsWith("._metadata.inprogress.")) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
                Files.deleteIfExists(child);
            }
        }
        Path completed = directory.resolve(COMPLETED_DIRECTORY);
        if (Files.isDirectory(completed)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(completed, "chk-*")) {
                for (Path marker : entries) {
                    String name = marker.getFileName().toString();
                    if (!name.matches("chk-[1-9][0-9]*")) continue;
                    long id;
                    try {
                        id = Long.parseLong(name.substring(4));
                    } catch (NumberFormatException invalid) {
                        continue;
                    }
                    if (id != current && id != previous && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                        Files.deleteIfExists(marker);
                    }
                }
            }
        }
    }

    /**
     * Computes a stable graph fingerprint from operator UIDs, types, parallelism,
     * partition routing and source boundedness.
     *
     * <p>All operators must have stable UIDs for cross-job recovery.
     */
    public static String graphSignature(StreamGraph graph) {
        return graphSignature(graph, 0);
    }

    /**
     * A keyed graph also commits to its KeyGroup hash algorithm and maxParallelism.
     * Existing non-keyed snapshot fingerprints are byte-for-byte unchanged.
     */
    public static String graphSignature(StreamGraph graph, int maxParallelism) {
        return graphSignature(graph, maxParallelism, "");
    }

    /**
     * Binds a checkpoint to both physical graph topology and its immutable Task Definition.
     *
     * <p>Definition identity belongs to the runtime restore contract, not the connector
     * Split or Enumerator State.
     */
    public static String graphSignature(StreamGraph graph, int maxParallelism, String definitionIdentity) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(definitionIdentity, "definitionIdentity");
        StringBuilder canonical = new StringBuilder("yak-local-v1");
        for (StreamNode node : graph.getTopologicalNodes()) {
            if (node.getUid() == null) {
                throw new IllegalArgumentException("Persistent checkpoints require stable UIDs for all operators");
            }
            canonical
                    .append('|')
                    .append(node.getUid())
                    .append(':')
                    .append(node.isSource() ? "SOURCE" : node.isSink() ? "SINK" : "OPERATOR")
                    .append(':')
                    .append(node.getParallelism())
                    .append(':')
                    .append(node.getOutputType().getName());
            if (node.isSource()) {
                canonical.append(':').append(node.getBoundedness().orElseThrow());
            }
        }
        for (StreamEdge edge : graph.getStreamEdges()) {
            canonical
                    .append('|')
                    .append(graph.getStreamNode(edge.sourceId()).getUid())
                    .append("->")
                    .append(graph.getStreamNode(edge.targetId()).getUid())
                    .append(':')
                    .append(edge.partitioning());
        }
        if (maxParallelism > 0
                && graph.getStreamEdges().stream()
                        .anyMatch(edge ->
                                edge.partitioning() == io.yak.ops.flow.runtime.graph.StreamPartitioning.KEYED)) {
            canonical.append("|keygroups-murmur3-v1:").append(maxParallelism);
        }
        if (!definitionIdentity.isEmpty()) {
            canonical
                    .append("|task-definition-v1:")
                    .append(definitionIdentity.length())
                    .append(':')
                    .append(definitionIdentity);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not supported by this JVM", impossible);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Checkpoint store is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                lock.release();
            } finally {
                lockChannel.close();
            }
        }
    }
}
