package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * 单个本地 Job 状态目录的独占检查点存储。
 *
 * <p>版本化自定义二进制编码与 CRC 校验，不使用 JDK 对象序列化。
 * 只以原子 rename 发布已完整写入并 fsync 的状态；损坏/不兼容状态显式拒绝。
 * 同一目录只能由一个正在运行的 Job 持有。
 */
public final class FileCheckpointStore implements AutoCloseable {

    private static final int MAGIC = 0x59414B43; // YAKC
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_STATE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_GROUPS = 64;
    private static final int MAX_SPLITS_PER_GROUP = 100_000;
    private static final String FILE_NAME = "checkpoint.bin";

    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;

    public FileCheckpointStore(Path directory) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory 不能为空").toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve(".checkpoint.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException error) {
            acquired = null;
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IllegalStateException("Checkpoint 状态目录正在被其它 Job 使用");
        }
        lock = acquired;
    }

    /** 恢复最近一次完整发布的 Checkpoint；拓扑、UID 或并行度不一致必须拒绝。 */
    public Optional<CheckpointSnapshot> loadLatest(String expectedGraphSignature) throws IOException {
        ensureOpen();
        Objects.requireNonNull(expectedGraphSignature, "expectedGraphSignature 不能为空");
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        long fileSize = Files.size(file);
        if (fileSize < 16 || fileSize > MAX_FILE_BYTES) {
            throw new IOException("Checkpoint 文件大小非法");
        }
        byte[] fileBytes = Files.readAllBytes(file);
        long expectedCrc;
        byte[] payload;
        try (DataInputStream raw = new DataInputStream(new ByteArrayInputStream(fileBytes))) {
            payload = raw.readNBytes(fileBytes.length - Long.BYTES);
            expectedCrc = raw.readLong();
        }
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        if (checksum.getValue() != expectedCrc) {
            throw new IOException("Checkpoint 文件 CRC 校验失败");
        }
        CheckpointSnapshot snapshot;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) {
                throw new IOException("Checkpoint 格式或版本不兼容");
            }
            long checkpointId = in.readLong();
            String signature = in.readUTF();
            long completedAt = in.readLong();
            CheckpointSnapshot.SerializedState enumerator = readState(in);
            Map<Integer, List<CheckpointSnapshot.SerializedState>> readers = readGroups(in);
            Map<Integer, List<CheckpointSnapshot.SerializedState>> assignments = readGroups(in);
            if (in.available() != 0) {
                throw new IOException("Checkpoint 文件包含多余字节");
            }
            snapshot = new CheckpointSnapshot(
                    checkpointId, signature, enumerator, readers, assignments, completedAt);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Checkpoint 状态内容不合法", failure);
        }
        if (!expectedGraphSignature.equals(snapshot.graphSignature())) {
            throw new IllegalStateException("Checkpoint 拓扑/算子 UID/并行度与当前作业不匹配");
        }
        return Optional.of(snapshot);
    }

    /** 保存成功并以原子替换发布后才能向 Reader/Enumerator 宣告 Checkpoint 完成。 */
    public void save(CheckpointSnapshot checkpoint) throws IOException {
        ensureOpen();
        Objects.requireNonNull(checkpoint, "checkpoint 不能为空");
        byte[] payload;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeLong(checkpoint.checkpointId());
            out.writeUTF(checkpoint.graphSignature());
            out.writeLong(checkpoint.completedAtMillis());
            writeState(out, checkpoint.enumeratorState());
            writeGroups(out, checkpoint.readerSplits());
            writeGroups(out, checkpoint.assignments());
            out.flush();
            payload = bytes.toByteArray();
        }
        if (payload.length + Long.BYTES > MAX_FILE_BYTES) {
            throw new IOException("Checkpoint 超出状态文件上限");
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        Path temp = Files.createTempFile(directory, ".checkpoint-", ".pending");
        try {
            makePrivate(temp);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                byte[] complete;
                try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(bytes)) {
                    out.write(payload);
                    out.writeLong(crc.getValue());
                    out.flush();
                    complete = bytes.toByteArray();
                }
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(complete);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, directory.resolve(FILE_NAME),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                // 非原子替换可能留下半成品；不对不支持的存储介质宣称 Durable Checkpoint。
                throw new IOException("状态目录不支持 Checkpoint 原子提交", failure);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void makePrivate(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // 不支持 POSIX 权限的平台仍采用文件系统访问控制，不输出状态内容。
        }
    }

    private static void writeGroups(DataOutputStream out,
            Map<Integer, List<CheckpointSnapshot.SerializedState>> groups) throws IOException {
        if (groups.size() > MAX_GROUPS) {
            throw new IOException("Checkpoint 子任务数量超限");
        }
        out.writeInt(groups.size());
        for (var group : groups.entrySet()) {
            out.writeInt(group.getKey());
            if (group.getValue().size() > MAX_SPLITS_PER_GROUP) {
                throw new IOException("Checkpoint Split 数量超限");
            }
            out.writeInt(group.getValue().size());
            for (var state : group.getValue()) {
                writeState(out, state);
            }
        }
    }

    private static Map<Integer, List<CheckpointSnapshot.SerializedState>> readGroups(
            DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_GROUPS) {
            throw new IOException("Checkpoint 子任务数量非法");
        }
        Map<Integer, List<CheckpointSnapshot.SerializedState>> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int index = in.readInt();
            int size = in.readInt();
            if (index < 0 || size < 0 || size > MAX_SPLITS_PER_GROUP || result.containsKey(index)) {
                throw new IOException("Checkpoint Reader 状态非法或重复");
            }
            var states = new java.util.ArrayList<CheckpointSnapshot.SerializedState>(size);
            for (int j = 0; j < size; j++) {
                states.add(readState(in));
            }
            result.put(index, List.copyOf(states));
        }
        return result;
    }

    private static void writeState(DataOutputStream out, CheckpointSnapshot.SerializedState state)
            throws IOException {
        byte[] bytes = state.bytes();
        if (bytes.length > MAX_STATE_BYTES) {
            throw new IOException("单个 Checkpoint 状态块过大");
        }
        out.writeInt(state.version());
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static CheckpointSnapshot.SerializedState readState(DataInputStream in) throws IOException {
        int version = in.readInt();
        int count = in.readInt();
        if (version < 0 || count < 0 || count > MAX_STATE_BYTES || count > in.available()) {
            throw new IOException("Checkpoint 状态块损坏");
        }
        return new CheckpointSnapshot.SerializedState(version, in.readNBytes(count));
    }

    /**
     * 用稳定算子 UID、算子种类、并行度、边路由及有界性计算拓扑指纹；
     * 无 UID 的图不能用于跨作业恢复。
     */
    public static String graphSignature(StreamGraph graph) {
        return graphSignature(graph, 0);
    }

    /**
     * A keyed graph also commits to its KeyGroup hash algorithm and maxParallelism.
     * Existing non-keyed snapshot fingerprints are byte-for-byte unchanged.
     */
    public static String graphSignature(StreamGraph graph, int maxParallelism) {
        Objects.requireNonNull(graph, "graph 不能为空");
        StringBuilder canonical = new StringBuilder("yak-local-v1");
        for (StreamNode node : graph.getTopologicalNodes()) {
            if (node.getUid() == null) {
                throw new IllegalArgumentException("持久化 Checkpoint 要求所有算子指定稳定 UID");
            }
            canonical.append('|').append(node.getUid())
                    .append(':').append(node.isSource() ? "SOURCE" : node.isSink() ? "SINK" : "OPERATOR")
                    .append(':').append(node.getParallelism())
                    .append(':').append(node.getOutputType().getName());
            if (node.isSource()) {
                canonical.append(':').append(node.getBoundedness().orElseThrow());
            }
        }
        for (StreamEdge edge : graph.getStreamEdges()) {
            canonical.append('|').append(graph.getStreamNode(edge.sourceId()).getUid())
                    .append("->").append(graph.getStreamNode(edge.targetId()).getUid())
                    .append(':').append(edge.partitioning());
        }
        if (maxParallelism > 0 && graph.getStreamEdges().stream()
                .anyMatch(edge -> edge.partitioning() == io.yak.ops.flow.runtime.graph.StreamPartitioning.KEYED)) {
            canonical.append("|keygroups-murmur3-v1:").append(maxParallelism);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 不支持 SHA-256", impossible);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("CheckpointStore 已关闭");
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
