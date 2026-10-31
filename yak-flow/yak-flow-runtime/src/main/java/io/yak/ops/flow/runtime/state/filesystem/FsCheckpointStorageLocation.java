/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Apache Flink release-1.20.3 local checkpoint storage sources.
 */
package io.yak.ops.flow.runtime.state.filesystem;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Local-only counterpart of Flink's FsCheckpointStorageLocation. */
public final class FsCheckpointStorageLocation {
    private final Path checkpointDirectory;

    public FsCheckpointStorageLocation(Path checkpointDirectory) {
        this.checkpointDirectory = Objects.requireNonNull(checkpointDirectory);
    }

    public Path getCheckpointDirectory() {
        return checkpointDirectory;
    }

    public Path getMetadataFilePath() {
        return checkpointDirectory.resolve("_metadata");
    }

    public CheckpointMetadataOutputStream createMetadataOutputStream() throws IOException {
        return new FsCheckpointMetadataOutputStream(getMetadataFilePath(), checkpointDirectory);
    }

    public void disposeOnFailure() throws IOException {
        deleteCheckpointDirectory(checkpointDirectory);
    }

    /** Deletes the exclusively owned directory, including recoverable staging files, without following links. */
    static void deleteCheckpointDirectory(Path checkpointDirectory) throws IOException {
        Files.walkFileTree(checkpointDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException) {
                    return FileVisitResult.CONTINUE;
                }
                throw failure;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
