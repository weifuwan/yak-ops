package io.yak.ops.flow.runtime.checkpoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * YakFlow's versioned state payload codec; distinct from Flink's file write and commit lifecycle.
 * This is not Flink CheckpointMetadata's wire format and cannot be restored by a native Flink job.
 */
final class CheckpointMetadataSerializer {
    private static final int MAGIC = 0x59414B43;
    private static final int FORMAT_VERSION = 2;
    static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_STATE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_GROUPS = 64;
    private static final int MAX_SPLITS_PER_GROUP = 100_000;

    private CheckpointMetadataSerializer() {}

    static CheckpointSnapshot deserialize(byte[] fileBytes) throws IOException {
        if (fileBytes.length < 16 || fileBytes.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid checkpoint file size");
        }
        long expectedCrc;
        byte[] payload;
        try (DataInputStream raw = new DataInputStream(new ByteArrayInputStream(fileBytes))) {
            payload = raw.readNBytes(fileBytes.length - Long.BYTES);
            expectedCrc = raw.readLong();
        }
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        if (checksum.getValue() != expectedCrc) {
            throw new IOException("Checkpoint file CRC verification failed");
        }
        CheckpointSnapshot snapshot;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Invalid checkpoint file magic number");
            }
            int format = in.readInt();
            if (format != FORMAT_VERSION) {
                throw new IOException("Unsupported checkpoint file format or version");
            }
            long checkpointId = in.readLong();
            String signature = in.readUTF();
            long completedAt = in.readLong();
            CheckpointSnapshot.SerializedState enumerator = readState(in);
            Map<Integer, List<CheckpointSnapshot.SerializedState>> readers = readGroups(in);
            Map<Integer, List<CheckpointSnapshot.SerializedState>> assignments = readGroups(in);
            Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> operatorStates =
                    readOperatorStates(in);
            Set<String> fullyFinished = readFinishedOperators(in);
            Set<CheckpointSnapshot.OperatorSubtask> finishedSubtasks = readFinishedSubtasks(in);
            CheckpointSnapshot.SerializedState schemaManagerState = in.readBoolean() ? readState(in) : null;
            if (in.available() != 0) {
                throw new IOException("Checkpoint file contains trailing bytes");
            }
            snapshot = new CheckpointSnapshot(
                    checkpointId,
                    signature,
                    enumerator,
                    readers,
                    assignments,
                    completedAt,
                    operatorStates,
                    fullyFinished,
                    finishedSubtasks,
                    schemaManagerState);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid checkpoint state contents", failure);
        }
        return snapshot;
    }

    static byte[] serialize(CheckpointSnapshot checkpoint) throws IOException {
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
            writeOperatorStates(out, checkpoint.operatorStates());
            writeFinishedOperators(out, checkpoint.fullyFinishedOperators());
            writeFinishedSubtasks(out, checkpoint.finishedSubtasks());
            out.writeBoolean(checkpoint.schemaManagerState() != null);
            if (checkpoint.schemaManagerState() != null) {
                writeState(out, checkpoint.schemaManagerState());
            }
            out.flush();
            payload = bytes.toByteArray();
        }
        if (payload.length + Long.BYTES > MAX_FILE_BYTES) {
            throw new IOException("Checkpoint exceeds the state file size limit");
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        byte[] metadata;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(payload);
            out.writeLong(crc.getValue());
            out.flush();
            metadata = bytes.toByteArray();
        }

        return metadata;
    }

    private static void writeGroups(DataOutputStream out, Map<Integer, List<CheckpointSnapshot.SerializedState>> groups)
            throws IOException {
        if (groups.size() > MAX_GROUPS) {
            throw new IOException("Checkpoint subtask count exceeds the limit");
        }
        out.writeInt(groups.size());
        for (Map.Entry<Integer, List<CheckpointSnapshot.SerializedState>> group : groups.entrySet()) {
            out.writeInt(group.getKey());
            if (group.getValue().size() > MAX_SPLITS_PER_GROUP) {
                throw new IOException("Checkpoint split count exceeds the limit");
            }
            out.writeInt(group.getValue().size());
            for (CheckpointSnapshot.SerializedState state : group.getValue()) {
                writeState(out, state);
            }
        }
    }

    private static Map<Integer, List<CheckpointSnapshot.SerializedState>> readGroups(DataInputStream in)
            throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_GROUPS) {
            throw new IOException("Invalid checkpoint subtask count");
        }
        Map<Integer, List<CheckpointSnapshot.SerializedState>> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int index = in.readInt();
            int size = in.readInt();
            if (index < 0 || size < 0 || size > MAX_SPLITS_PER_GROUP || result.containsKey(index)) {
                throw new IOException("Invalid or duplicate checkpoint reader state");
            }
            List<CheckpointSnapshot.SerializedState> states = new java.util.ArrayList<>(size);
            for (int j = 0; j < size; j++) {
                states.add(readState(in));
            }
            result.put(index, List.copyOf(states));
        }
        return result;
    }

    private static void writeOperatorStates(
            DataOutputStream out,
            Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> groups)
            throws IOException {
        if (groups.size() > MAX_GROUPS) {
            throw new IOException("Checkpoint Operator subtask count exceeds limit");
        }
        out.writeInt(groups.size());
        for (Map.Entry<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> entry :
                groups.entrySet()) {
            out.writeUTF(entry.getKey().uid());
            out.writeInt(entry.getKey().subtaskIndex());
            if (entry.getValue().size() > MAX_SPLITS_PER_GROUP) {
                throw new IOException("Checkpoint Operator state count exceeds limit");
            }
            out.writeInt(entry.getValue().size());
            for (Map.Entry<String, CheckpointSnapshot.SerializedState> named :
                    entry.getValue().entrySet()) {
                out.writeUTF(named.getKey());
                writeState(out, named.getValue());
            }
        }
    }

    private static Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>>
            readOperatorStates(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_GROUPS) {
            throw new IOException("Invalid checkpoint operator count");
        }
        Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> groups =
                new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            CheckpointSnapshot.OperatorSubtask id = new CheckpointSnapshot.OperatorSubtask(in.readUTF(), in.readInt());
            int size = in.readInt();
            if (size < 0 || size > MAX_SPLITS_PER_GROUP || groups.containsKey(id)) {
                throw new IOException("Invalid or duplicate checkpoint operator subtask");
            }
            Map<String, CheckpointSnapshot.SerializedState> named = new LinkedHashMap<>();
            for (int j = 0; j < size; j++) {
                String name = in.readUTF();
                if (name.isBlank() || named.putIfAbsent(name, readState(in)) != null) {
                    throw new IOException("Invalid or duplicate checkpoint state name");
                }
            }
            groups.put(id, Map.copyOf(named));
        }
        return Map.copyOf(groups);
    }

    private static void writeFinishedOperators(DataOutputStream out, Set<String> operators) throws IOException {
        if (operators.size() > MAX_GROUPS) {
            throw new IOException("Too many fully finished operators");
        }
        out.writeInt(operators.size());
        for (String uid : operators.stream().sorted().toList()) {
            out.writeUTF(uid);
        }
    }

    private static Set<String> readFinishedOperators(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_GROUPS) {
            throw new IOException("Invalid fully finished operator count");
        }
        Set<String> operators = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            String uid = in.readUTF();
            if (uid.isBlank() || !operators.add(uid)) {
                throw new IOException("Invalid or duplicate fully finished operator");
            }
        }
        return Set.copyOf(operators);
    }

    private static void writeFinishedSubtasks(DataOutputStream out, Set<CheckpointSnapshot.OperatorSubtask> subtasks)
            throws IOException {
        if (subtasks.size() > MAX_SPLITS_PER_GROUP) {
            throw new IOException("Too many finished operator subtasks");
        }
        out.writeInt(subtasks.size());
        for (CheckpointSnapshot.OperatorSubtask subtask : subtasks.stream()
                .sorted(java.util.Comparator.comparing(CheckpointSnapshot.OperatorSubtask::uid)
                        .thenComparingInt(CheckpointSnapshot.OperatorSubtask::subtaskIndex))
                .toList()) {
            out.writeUTF(subtask.uid());
            out.writeInt(subtask.subtaskIndex());
        }
    }

    private static Set<CheckpointSnapshot.OperatorSubtask> readFinishedSubtasks(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_SPLITS_PER_GROUP) {
            throw new IOException("Invalid finished subtask count");
        }
        Set<CheckpointSnapshot.OperatorSubtask> result = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            CheckpointSnapshot.OperatorSubtask subtask =
                    new CheckpointSnapshot.OperatorSubtask(in.readUTF(), in.readInt());
            if (!result.add(subtask)) {
                throw new IOException("Duplicate finished operator subtask");
            }
        }
        return Set.copyOf(result);
    }

    private static void writeState(DataOutputStream out, CheckpointSnapshot.SerializedState state) throws IOException {
        byte[] bytes = state.bytes();
        if (bytes.length > MAX_STATE_BYTES) {
            throw new IOException("Checkpoint state block exceeds the size limit");
        }
        out.writeInt(state.version());
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static CheckpointSnapshot.SerializedState readState(DataInputStream in) throws IOException {
        int version = in.readInt();
        int count = in.readInt();
        if (version < 0 || count < 0 || count > MAX_STATE_BYTES || count > in.available()) {
            throw new IOException("Corrupted checkpoint state block");
        }
        return new CheckpointSnapshot.SerializedState(version, in.readNBytes(count));
    }
}
