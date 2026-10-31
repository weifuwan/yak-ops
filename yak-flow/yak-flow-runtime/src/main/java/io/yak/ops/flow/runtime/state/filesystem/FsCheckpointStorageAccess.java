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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Flink FsCheckpointStorageAccess narrowed to one locally owned Task Instance.
 * No JobManager, TaskManager, Savepoint, or distributed state transfer is required.
 */
public final class FsCheckpointStorageAccess {
    private final Path checkpointsDirectory;

    public FsCheckpointStorageAccess(Path taskInstanceDirectory) {
        Objects.requireNonNull(taskInstanceDirectory, "taskInstanceDirectory");
        this.checkpointsDirectory = taskInstanceDirectory.resolve("checkpoints");
    }

    public void initializeBaseLocationsForCheckpoint() throws IOException {
        Files.createDirectories(checkpointsDirectory);
    }

    public FsCheckpointStorageLocation initializeLocationForCheckpoint(long checkpointId) throws IOException {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be positive");
        }
        initializeBaseLocationsForCheckpoint();
        Path location = checkpointsDirectory.resolve("chk-" + checkpointId);
        Files.createDirectories(location);
        return new FsCheckpointStorageLocation(location);
    }

    public FsCompletedCheckpointStorageLocation resolveCheckpoint(String externalPointer) throws IOException {
        Path path;
        try {
            path = Path.of(java.net.URI.create(externalPointer))
                    .toAbsolutePath()
                    .normalize();
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid completed checkpoint external pointer", failure);
        }
        if (!path.startsWith(checkpointsDirectory.toAbsolutePath().normalize())
                || !path.getParent()
                        .equals(checkpointsDirectory.toAbsolutePath().normalize())
                || !path.getFileName().toString().matches("chk-[1-9][0-9]*")) {
            throw new IOException("Checkpoint pointer escapes the task instance directory");
        }
        Path metadata = path.resolve("_metadata");
        if (!Files.isRegularFile(metadata)) {
            throw new IOException("Completed checkpoint metadata is missing: " + metadata);
        }
        return new FsCompletedCheckpointStorageLocation(path, metadata, Files.size(metadata));
    }
}
