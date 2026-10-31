package io.yak.ops.flow.runtime.state.filesystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the relocated Flink 1.20.3 local persist/commit protocol without Flink dependencies. */
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
    void canResumeAndRecoverCommitUsingFlinkRecoverables() throws Exception {
        LocalRecoverableWriter writer = new LocalRecoverableWriter();
        Path target = directory.resolve("recoverable");
        RecoverableWriter.ResumeRecoverable resume;
        try (RecoverableFsDataOutputStream output = writer.open(target)) {
            output.write(new byte[] {4, 5});
            resume = output.persist();
        }
        byte[] serialized = writer.getResumeRecoverableSerializer().serialize(resume);
        RecoverableWriter.ResumeRecoverable restored =
                writer.getResumeRecoverableSerializer().deserialize(
                        writer.getResumeRecoverableSerializer().getVersion(), serialized);
        try (RecoverableFsDataOutputStream output = writer.recover(restored)) {
            output.write(6);
            output.closeForCommit().commit();
        }
        assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(target));
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
        assertTrue(access.resolveCheckpoint(finalized.getExternalPointer())
                .getMetadataFilePath().equals(checkpointDirectory.resolve("_metadata")));
    }
}
