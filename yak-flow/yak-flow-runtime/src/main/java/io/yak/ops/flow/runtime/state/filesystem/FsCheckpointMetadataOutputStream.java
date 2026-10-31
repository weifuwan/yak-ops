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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A {@link CheckpointMetadataOutputStream} that writes a specified file and directory, and returns
 * a {@link FsCompletedCheckpointStorageLocation} upon closing.
 */
public final class FsCheckpointMetadataOutputStream extends CheckpointMetadataOutputStream {

    private static final Logger LOG =
            LoggerFactory.getLogger(FsCheckpointMetadataOutputStream.class);

    // ------------------------------------------------------------------------

    private final Path metadataFilePath;

    private final Path exclusiveCheckpointDir;

    private volatile boolean closed;

    private final MetadataOutputStreamWrapper outputStreamWrapper;

    public FsCheckpointMetadataOutputStream(
            Path metadataFilePath, Path exclusiveCheckpointDir)
            throws IOException {

        this.metadataFilePath = requireNonNull(metadataFilePath);
        this.exclusiveCheckpointDir = requireNonNull(exclusiveCheckpointDir);

        this.outputStreamWrapper = getOutputStreamWrapper(metadataFilePath);
    }

    // ------------------------------------------------------------------------
    //  I/O
    // ------------------------------------------------------------------------

    @Override
    public final void write(int b) throws IOException {
        outputStreamWrapper.getOutput().write(b);
    }

    @Override
    public final void write(byte[] b, int off, int len) throws IOException {
        outputStreamWrapper.getOutput().write(b, off, len);
    }

    @Override
    public long getPos() throws IOException {
        return outputStreamWrapper.getOutput().getPos();
    }

    @Override
    public void flush() throws IOException {
        outputStreamWrapper.getOutput().flush();
    }

    @Override
    public void sync() throws IOException {
        outputStreamWrapper.getOutput().sync();
    }

    // ------------------------------------------------------------------------
    //  Closing
    // ------------------------------------------------------------------------

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;

            try {
                outputStreamWrapper.close();
                outputStreamWrapper.cleanup();
            } catch (Throwable t) {
                LOG.warn("Could not close the state stream for {}.", metadataFilePath, t);
            }
        }
    }

    @Override
    public FsCompletedCheckpointStorageLocation closeAndFinalizeCheckpoint() throws IOException {
        synchronized (this) {
            if (!closed) {
                try {
                    // make a best effort attempt to figure out the size
                    long size = 0;
                    try {
                        size = outputStreamWrapper.getOutput().getPos();
                    } catch (Exception ignored) {
                    }

                    outputStreamWrapper.closeForCommit();

                    return new FsCompletedCheckpointStorageLocation(exclusiveCheckpointDir, metadataFilePath, size);
                } catch (Exception e) {
                    try {
                        outputStreamWrapper.cleanup();
                    } catch (Exception deleteException) {
                        LOG.warn(
                                "Could not delete the checkpoint stream file {}.",
                                metadataFilePath,
                                deleteException);
                    }

                    throw new IOException(
                            "Could not flush and close the file system "
                                    + "output stream to "
                                    + metadataFilePath
                                    + " in order to obtain the "
                                    + "stream state handle",
                            e);
                } finally {
                    closed = true;
                }
            } else {
                throw new IOException("Stream has already been closed and discarded.");
            }
        }
    }

    static MetadataOutputStreamWrapper getOutputStreamWrapper(Path metadataFilePath) throws IOException {
        if (java.nio.file.Files.exists(metadataFilePath)) {
            throw new IOException("Target file " + metadataFilePath + " already exists.");
        }
        return new RecoverableStreamWrapper(new LocalRecoverableWriter().open(metadataFilePath));
    }
}
