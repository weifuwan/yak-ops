package io.yak.ops.flow.runtime.state.filesystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FsCheckpointStorageLocationTest {

    @TempDir
    Path directory;

    @Test
    void discardsStagingFilesAfterAbortingMetadata() throws Exception {
        FsCheckpointStorageLocation location =
                new FsCheckpointStorageAccess(directory).initializeLocationForCheckpoint(1);
        try (CheckpointMetadataOutputStream output = location.createMetadataOutputStream()) {
            output.write(new byte[] {1, 2, 3});
        }
        assertFalse(Files.exists(location.getMetadataFilePath()));
        try (Stream<Path> files = Files.list(location.getCheckpointDirectory())) {
            assertTrue(files.findAny().isPresent());
        }

        location.disposeOnFailure();

        assertFalse(Files.exists(location.getCheckpointDirectory()));
        location.disposeOnFailure();
    }

    @Test
    void discardsStagingFilesAfterMetadataCommitFails() throws Exception {
        FsCheckpointStorageLocation location =
                new FsCheckpointStorageAccess(directory).initializeLocationForCheckpoint(1);
        try (CheckpointMetadataOutputStream output = location.createMetadataOutputStream()) {
            output.write(new byte[] {4, 5, 6});
            Files.createDirectory(location.getMetadataFilePath());
            Files.write(location.getMetadataFilePath().resolve("occupied"), new byte[] {7});

            assertThrows(IOException.class, output::closeAndFinalizeCheckpoint);
            assertThrows(ClosedChannelException.class, output::getPos);
        }

        location.disposeOnFailure();

        assertFalse(Files.exists(location.getCheckpointDirectory()));
    }

    @Test
    void failedFinalizeClosesStreamBeforeCleanupAndPreservesFailures() throws Exception {
        FsCheckpointStorageLocation location =
                new FsCheckpointStorageAccess(directory).initializeLocationForCheckpoint(1);
        IOException syncFailure = new IOException("Injected metadata sync failure");
        IOException closeFailure = new IOException("Injected close failure");
        IOException cleanupFailure = new IOException("Injected cleanup failure");
        Path staging = location.getCheckpointDirectory().resolve("._metadata.inprogress.test");
        LocalRecoverableFsDataOutputStream stream = new LocalRecoverableFsDataOutputStream(
                location.getMetadataFilePath().toFile(), staging.toFile()) {
            @Override
            public void sync() throws IOException {
                throw syncFailure;
            }
        };
        MetadataOutputStreamWrapper wrapper = new RecoverableStreamWrapper(stream) {
            @Override
            public void closeAction() throws IOException {
                super.closeAction();
                throw closeFailure;
            }

            @Override
            public void cleanup() throws IOException {
                assertThrows(ClosedChannelException.class, stream::getPos);
                throw cleanupFailure;
            }
        };
        try (FsCheckpointMetadataOutputStream output = new FsCheckpointMetadataOutputStream(
                location.getMetadataFilePath(), location.getCheckpointDirectory(), wrapper)) {
            output.write(new byte[] {1, 2, 3});

            IOException failure = assertThrows(IOException.class, output::closeAndFinalizeCheckpoint);

            assertSame(syncFailure, failure.getCause());
            assertArrayEquals(new Throwable[] {closeFailure, cleanupFailure}, syncFailure.getSuppressed());
            assertThrows(ClosedChannelException.class, stream::getPos);
            assertFalse(Files.exists(location.getMetadataFilePath()));
        }

        location.disposeOnFailure();

        assertFalse(Files.exists(location.getCheckpointDirectory()));
    }

    @Test
    void discardsCompletedLocationWithoutRemovingSiblingCheckpoint() throws Exception {
        FsCheckpointStorageAccess access = new FsCheckpointStorageAccess(directory);
        FsCheckpointStorageLocation location = access.initializeLocationForCheckpoint(1);
        FsCheckpointStorageLocation sibling = access.initializeLocationForCheckpoint(2);
        byte[] retainedMetadata = new byte[] {8, 9};
        Files.write(sibling.getMetadataFilePath(), retainedMetadata);
        FsCompletedCheckpointStorageLocation completed;
        try (CheckpointMetadataOutputStream output = location.createMetadataOutputStream()) {
            output.write(new byte[] {1, 2, 3});
            completed = output.closeAndFinalizeCheckpoint();
        }
        Path nested = Files.createDirectories(location.getCheckpointDirectory().resolve("staging/nested"));
        Files.write(nested.resolve("unfinished"), new byte[] {4});

        completed.disposeStorageLocation();

        assertFalse(Files.exists(location.getCheckpointDirectory()));
        assertArrayEquals(retainedMetadata, Files.readAllBytes(sibling.getMetadataFilePath()));
        completed.disposeStorageLocation();
    }

    @Test
    void disposalDoesNotFollowSymbolicLinks() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class),
                "Symbolic link creation is unprivileged on POSIX filesystems");
        Path external = Files.createDirectory(directory.resolve("external"));
        byte[] retained = new byte[] {10, 11};
        Path retainedFile = Files.write(external.resolve("retained"), retained);
        FsCheckpointStorageLocation location =
                new FsCheckpointStorageAccess(directory).initializeLocationForCheckpoint(1);
        Files.createSymbolicLink(location.getCheckpointDirectory().resolve("linked-state"), external);

        location.disposeOnFailure();

        assertFalse(Files.exists(location.getCheckpointDirectory()));
        assertArrayEquals(retained, Files.readAllBytes(retainedFile));

        Path linkedRoot = Files.createSymbolicLink(directory.resolve("linked-checkpoint"), external);
        new FsCheckpointStorageLocation(linkedRoot).disposeOnFailure();

        assertFalse(Files.exists(linkedRoot, LinkOption.NOFOLLOW_LINKS));
        assertArrayEquals(retained, Files.readAllBytes(retainedFile));
    }

    @Test
    void checkpointFilesAreOwnerOnlyBeforeAndAfterCommitOnPosix() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView(PosixFileAttributeView.class));
        Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
        Path target = directory.resolve("_metadata");
        try (RecoverableFsDataOutputStream output = new LocalRecoverableWriter().open(target)) {
            output.write(new byte[] {12, 13});
            List<Path> staged;
            try (Stream<Path> files = Files.list(directory)) {
                staged = files.toList();
            }
            assertEquals(1, staged.size());
            assertEquals(ownerOnly, Files.getPosixFilePermissions(staged.get(0)));

            output.closeForCommit().commit();
        }

        assertEquals(ownerOnly, Files.getPosixFilePermissions(target));
        assertArrayEquals(new byte[] {12, 13}, Files.readAllBytes(target));
    }
}
