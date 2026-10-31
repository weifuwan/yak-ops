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

import io.yak.ops.flow.runtime.state.filesystem.RecoverableFsDataOutputStream.Committer;

import io.yak.ops.core.api.io.SimpleVersionedSerializer;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/** A {@link RecoverableWriter} for the {@link LocalFileSystem}. */
public class LocalRecoverableWriter implements RecoverableWriter {

    public LocalRecoverableWriter() {}

    @Override
    public RecoverableFsDataOutputStream open(java.nio.file.Path filePath) throws IOException {
        final File targetFile = filePath.toFile();
        final File tempFile = generateStagingTempFilePath(targetFile);

        // try to create the parent
        final File parent = tempFile.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Failed to create the parent directory: " + parent);
        }

        return new LocalRecoverableFsDataOutputStream(targetFile, tempFile);
    }

    @Override
    public RecoverableFsDataOutputStream recover(ResumeRecoverable recoverable) throws IOException {
        if (recoverable instanceof LocalRecoverable) {
            return new LocalRecoverableFsDataOutputStream((LocalRecoverable) recoverable);
        } else {
            throw new IllegalArgumentException(
                    "LocalFileSystem cannot recover recoverable for other file system: "
                            + recoverable);
        }
    }

    @Override
    public boolean requiresCleanupOfRecoverableState() {
        return false;
    }

    @Override
    public boolean cleanupRecoverableState(ResumeRecoverable resumable) throws IOException {
        return false;
    }

    @Override
    public Committer recoverForCommit(CommitRecoverable recoverable) throws IOException {
        if (recoverable instanceof LocalRecoverable) {
            return new LocalRecoverableFsDataOutputStream.LocalCommitter(
                    (LocalRecoverable) recoverable);
        } else {
            throw new IllegalArgumentException(
                    "LocalFileSystem cannot recover recoverable for other file system: "
                            + recoverable);
        }
    }

    @Override
    public SimpleVersionedSerializer<CommitRecoverable> getCommitRecoverableSerializer() {
        @SuppressWarnings("unchecked")
        SimpleVersionedSerializer<CommitRecoverable> typedSerializer =
                (SimpleVersionedSerializer<CommitRecoverable>)
                        (SimpleVersionedSerializer<?>) LocalRecoverableSerializer.INSTANCE;

        return typedSerializer;
    }

    @Override
    public SimpleVersionedSerializer<ResumeRecoverable> getResumeRecoverableSerializer() {
        @SuppressWarnings("unchecked")
        SimpleVersionedSerializer<ResumeRecoverable> typedSerializer =
                (SimpleVersionedSerializer<ResumeRecoverable>)
                        (SimpleVersionedSerializer<?>) LocalRecoverableSerializer.INSTANCE;

        return typedSerializer;
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    @VisibleForTesting
    public static File generateStagingTempFilePath(File targetFile) {
        if (targetFile.isDirectory()) { throw new IllegalArgumentException("targetFile must not be a directory"); }

        final File parent = targetFile.getParentFile();
        final String name = targetFile.getName();

        if (parent == null) { throw new IllegalArgumentException("targetFile must not be the root directory"); }

        while (true) {
            File candidate =
                    new File(parent, "." + name + ".inprogress." + UUID.randomUUID().toString());
            if (!candidate.exists()) {
                return candidate;
            }
        }
    }
}
