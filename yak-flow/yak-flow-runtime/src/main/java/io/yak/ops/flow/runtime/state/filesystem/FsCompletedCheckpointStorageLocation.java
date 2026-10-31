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

/** Flink-style finalized checkpoint location backed by an immutable metadata file. */
public final class FsCompletedCheckpointStorageLocation {
    private final Path checkpointDirectory;
    private final Path metadataFilePath;
    private final long metadataSize;

    public FsCompletedCheckpointStorageLocation(Path checkpointDirectory, Path metadataFilePath, long metadataSize) {
        this.checkpointDirectory = Objects.requireNonNull(checkpointDirectory);
        this.metadataFilePath = Objects.requireNonNull(metadataFilePath);
        this.metadataSize = metadataSize;
    }

    public String getExternalPointer() {
        return checkpointDirectory.toUri().toString();
    }

    public Path getMetadataFilePath() {
        return metadataFilePath;
    }

    public long getMetadataSize() {
        return metadataSize;
    }

    public void disposeStorageLocation() throws IOException {
        Files.deleteIfExists(metadataFilePath);
        Files.deleteIfExists(checkpointDirectory);
    }
}
