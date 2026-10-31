/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.flow.runtime.state.filesystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Local writer contract tests adapted from Apache Flink release-1.20.3's
 * {@code AbstractRecoverableWriterTest}, using JDK paths and JUnit assertions.
 */
class LocalRecoverableWriterTest {

    @TempDir Path directory;

    @Test
    void commitsOnlyAfterCloseForCommit() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("_metadata");
        byte[] payload = new byte[] {1, 2, 3};
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(payload);
            assertFalse(Files.exists(target));
            RecoverableFsDataOutputStream.Committer committer = output.closeForCommit();
            assertFalse(Files.exists(target));
            committer.commit();
        }
        assertArrayEquals(payload, Files.readAllBytes(target));
    }

    @Test
    void resumesUsingSerializedRecoverable() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("recoverable");
        RecoverableWriter.ResumeRecoverable resume;
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {4, 5});
            resume = output.persist();
        }
        SimpleVersionedSerializer<RecoverableWriter.ResumeRecoverable> serializer =
                writer.getResumeRecoverableSerializer();
        byte[] serialized = serializer.serialize(resume);
        LocalRecoverableWriter recoveredWriter = new LocalRecoverableWriter();
        RecoverableWriter.ResumeRecoverable restored =
                recoveredWriter.getResumeRecoverableSerializer().deserialize(serializer.getVersion(), serialized);
        try (RecoverableFsDataOutputStream output = recoveredWriter.recover(restored)) {
            output.write(6);
            output.closeForCommit().commit();
        }
        assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(target));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void resumesFromEachPersistedOffset(int persistIndex) throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("part-0");
        RecoverableWriter.ResumeRecoverable[] recoverables = new RecoverableWriter.ResumeRecoverable[4];
        byte[][] expectedContents = {new byte[0], new byte[] {1, 2}, new byte[] {1, 2}, new byte[] {1, 2, 3, 4}};
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            recoverables[0] = output.persist();
            output.write(new byte[] {1, 2});
            recoverables[1] = output.persist();
            recoverables[2] = output.persist();
            output.write(new byte[] {3, 4});
            recoverables[3] = output.persist();
        }

        SimpleVersionedSerializer<RecoverableWriter.ResumeRecoverable> serializer =
                writer.getResumeRecoverableSerializer();
        byte[] serialized = serializer.serialize(recoverables[persistIndex]);
        LocalRecoverableWriter recoveredWriter = new LocalRecoverableWriter();
        RecoverableWriter.ResumeRecoverable restored =
                recoveredWriter.getResumeRecoverableSerializer().deserialize(serializer.getVersion(), serialized);
        byte[] expected = expectedContents[persistIndex];
        try (RecoverableFsDataOutputStream output = recoveredWriter.recover(restored)) {
            assertFalse(Files.exists(target));
            Path staging = singleFileInDirectory(directory);
            assertTrue(staging.getFileName().toString().startsWith(".part-0.inprogress."));
            assertEquals(expected.length, output.getPos());
            assertArrayEquals(expected, Files.readAllBytes(staging));
            output.write(5);
            output.closeForCommit().commit();
        }
        byte[] committed = Arrays.copyOf(expected, expected.length + 1);
        committed[expected.length] = 5;
        assertEquals(target, singleFileInDirectory(directory));
        assertArrayEquals(committed, Files.readAllBytes(target));
    }

    @Test
    void commitsAfterRecoveryUsingSerializedCommitRecoverable() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("part-0");
        RecoverableWriter.CommitRecoverable recoverable;
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {1, 2});
            output.persist();
            output.persist();
            output.write(new byte[] {3, 4});
            recoverable = output.closeForCommit().getRecoverable();
        }
        SimpleVersionedSerializer<RecoverableWriter.CommitRecoverable> serializer =
                writer.getCommitRecoverableSerializer();
        byte[] serialized = serializer.serialize(recoverable);
        LocalRecoverableWriter recoveredWriter = new LocalRecoverableWriter();
        RecoverableWriter.CommitRecoverable restored =
                recoveredWriter.getCommitRecoverableSerializer().deserialize(serializer.getVersion(), serialized);
        assertFalse(Files.exists(target));
        recoveredWriter.recoverForCommit(restored).commitAfterRecovery();
        assertEquals(target, singleFileInDirectory(directory));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(target));

        new LocalRecoverableWriter().recoverForCommit(restored).commitAfterRecovery();
        assertEquals(target, singleFileInDirectory(directory));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(target));
    }

    @Test
    void recoveredCommitTruncatesDataAfterPersistedOffset() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("part-0");
        RecoverableWriter.ResumeRecoverable recoverable;
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {1, 2});
            recoverable = output.persist();
            output.write(new byte[] {3, 4});
        }
        assertFalse(Files.exists(target));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(singleFileInDirectory(directory)));
        SimpleVersionedSerializer<RecoverableWriter.CommitRecoverable> serializer =
                writer.getCommitRecoverableSerializer();
        byte[] serialized = serializer.serialize(recoverable);
        LocalRecoverableWriter recoveredWriter = new LocalRecoverableWriter();
        RecoverableWriter.CommitRecoverable restored =
                recoveredWriter.getCommitRecoverableSerializer().deserialize(serializer.getVersion(), serialized);

        recoveredWriter.recoverForCommit(restored).commitAfterRecovery();

        assertEquals(target, singleFileInDirectory(directory));
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(target));
    }

    @Test
    void rejectsRecoveryWhenPersistedDataIsMissing() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("part-0");
        RecoverableWriter.ResumeRecoverable earlier;
        RecoverableWriter.ResumeRecoverable later;
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {1, 2});
            earlier = output.persist();
            output.write(new byte[] {3, 4});
            later = output.persist();
        }
        try (RecoverableFsDataOutputStream output = writer.recover(earlier)) {
            assertEquals(2, output.getPos());
        }

        assertThrows(IOException.class, () -> writer.recover(later));
        assertThrows(IOException.class, () -> writer.recoverForCommit(later).commitAfterRecovery());
        assertFalse(Files.exists(target));
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(singleFileInDirectory(directory)));
    }

    @Test
    void rejectsWritesAfterCloseForCommit() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("part-0");
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {1, 2});
            RecoverableFsDataOutputStream.Committer committer = output.closeForCommit();
            assertThrows(IOException.class, () -> output.write(new byte[] {3, 4}));
            committer.commit();
        }
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(target));
    }

    @Test
    void rejectsResumeAfterCommit() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("part-0");
        RecoverableWriter.ResumeRecoverable recoverable;
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {1, 2});
            recoverable = output.persist();
            output.write(new byte[] {3, 4});
            output.closeForCommit().commit();
        }

        assertThrows(IOException.class, () -> writer.recover(recoverable));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(target));
    }

    @Test
    void finalizesCheckpointMetadataViaFlinkOutputStream() throws Exception {
        Path checkpointDirectory = directory.resolve("checkpoints/chk-17");
        FsCheckpointStorageAccess access = new FsCheckpointStorageAccess(directory);
        FsCheckpointStorageLocation target = access.initializeLocationForCheckpoint(17L);
        FsCompletedCheckpointStorageLocation finalized;
        try (CheckpointMetadataOutputStream output = target.createMetadataOutputStream()) {
            output.write(new byte[] {7, 8, 9});
            assertFalse(Files.exists(target.getMetadataFilePath()));
            finalized = output.closeAndFinalizeCheckpoint();
        }
        assertTrue(Files.exists(finalized.getMetadataFilePath()));
        assertArrayEquals(new byte[] {7, 8, 9}, Files.readAllBytes(finalized.getMetadataFilePath()));
        assertEquals(
                checkpointDirectory.resolve("_metadata"),
                access.resolveCheckpoint(finalized.getExternalPointer()).getMetadataFilePath());
    }

    private static Path singleFileInDirectory(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> contents = files.toList();
            assertEquals(1, contents.size());
            return contents.get(0);
        }
    }
}
