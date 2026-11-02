/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.flink.cdc.runtime.typeutils;

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializerSchemaCompatibility;
import org.apache.flink.cdc.common.memory.DataInputViewStreamWrapper;
import org.apache.flink.cdc.common.memory.DataOutputViewStreamWrapper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Compatibility adapter for Flink-only TypeInformation code paths.
 * The standalone CDC serializers and their snapshots use the native CDC API.
 * This adapter is not used by the standalone execution engine.
 */
public final class FlinkSerializerAdapter<T> extends TypeSerializer<T> {
    private static final long serialVersionUID = 1L;

    private final org.apache.flink.cdc.common.serialization.TypeSerializer<T> delegate;

    public FlinkSerializerAdapter(
            org.apache.flink.cdc.common.serialization.TypeSerializer<T> delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
    }

    @Override
    public boolean isImmutableType() {
        return delegate.isImmutableType();
    }

    @Override
    public TypeSerializer<T> duplicate() {
        return new FlinkSerializerAdapter<>(delegate.duplicate());
    }

    @Override
    public T createInstance() {
        return delegate.createInstance();
    }

    @Override
    public T copy(T from) {
        return delegate.copy(from);
    }

    @Override
    public T copy(T from, T reuse) {
        return delegate.copy(from, reuse);
    }

    @Override
    public int getLength() {
        return delegate.getLength();
    }

    @Override
    public void serialize(T record, org.apache.flink.core.memory.DataOutputView out)
            throws IOException {
        delegate.serialize(record, wrapOutput(out));
    }

    @Override
    public T deserialize(org.apache.flink.core.memory.DataInputView in) throws IOException {
        return delegate.deserialize(wrapInput(in));
    }

    @Override
    public T deserialize(T reuse, org.apache.flink.core.memory.DataInputView in)
            throws IOException {
        return delegate.deserialize(reuse, wrapInput(in));
    }

    @Override
    public void copy(
            org.apache.flink.core.memory.DataInputView in,
            org.apache.flink.core.memory.DataOutputView out) throws IOException {
        delegate.copy(wrapInput(in), wrapOutput(out));
    }

    @Override
    public TypeSerializerSnapshot<T> snapshotConfiguration() {
        return new AdapterSnapshot<>(delegate.snapshotConfiguration());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FlinkSerializerAdapter
                && delegate.equals(((FlinkSerializerAdapter<?>) other).delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    private static DataInputViewStreamWrapper wrapInput(
            final org.apache.flink.core.memory.DataInputView input) {
        return new DataInputViewStreamWrapper(new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    return input.readUnsignedByte();
                } catch (EOFException e) {
                    return -1;
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return input.read(b, off, len);
            }
        });
    }

    private static DataOutputViewStreamWrapper wrapOutput(
            final org.apache.flink.core.memory.DataOutputView output) {
        return new DataOutputViewStreamWrapper(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                output.write(value);
            }

            @Override
            public void write(byte[] bytes, int off, int len) throws IOException {
                output.write(bytes, off, len);
            }
        });
    }

    /**
     * Snapshot bridge for Flink's TypeInformation adapter. The underlying
     * CDC serializer snapshot retains its upstream versioned binary format.
     */
    public static final class AdapterSnapshot<T> implements TypeSerializerSnapshot<T> {
        private org.apache.flink.cdc.common.serialization.TypeSerializerSnapshot<T> nativeSnapshot;

        public AdapterSnapshot() {}

        private AdapterSnapshot(
                org.apache.flink.cdc.common.serialization.TypeSerializerSnapshot<T> snapshot) {
            this.nativeSnapshot = snapshot;
        }

        @Override
        public int getCurrentVersion() {
            return 1;
        }

        @Override
        public void writeSnapshot(org.apache.flink.core.memory.DataOutputView out)
                throws IOException {
            org.apache.flink.cdc.common.serialization.TypeSerializerSnapshot
                    .writeVersionedSnapshot(wrapOutput(out), nativeSnapshot);
        }

        @Override
        public void readSnapshot(
                int version,
                org.apache.flink.core.memory.DataInputView in,
                ClassLoader classLoader) throws IOException {
            if (version != 1) {
                throw new IOException("Unknown Flink adapter snapshot version: " + version);
            }
            nativeSnapshot =
                    org.apache.flink.cdc.common.serialization.TypeSerializerSnapshot
                            .readVersionedSnapshot(wrapInput(in), classLoader);
        }

        @Override
        public TypeSerializer<T> restoreSerializer() {
            return new FlinkSerializerAdapter<>(nativeSnapshot.restoreSerializer());
        }

        @Override
        public TypeSerializerSchemaCompatibility<T> resolveSchemaCompatibility(
                TypeSerializerSnapshot<T> oldSnapshot) {
            if (!(oldSnapshot instanceof AdapterSnapshot)) {
                return TypeSerializerSchemaCompatibility.incompatible();
            }
            @SuppressWarnings("unchecked")
            AdapterSnapshot<T> previous = (AdapterSnapshot<T>) oldSnapshot;
            org.apache.flink.cdc.common.serialization.TypeSerializerSchemaCompatibility<T>
                    result = nativeSnapshot.resolveSchemaCompatibility(previous.nativeSnapshot);
            if (result.isCompatibleAsIs()) {
                return TypeSerializerSchemaCompatibility.compatibleAsIs();
            }
            if (result.isCompatibleAfterMigration()) {
                return TypeSerializerSchemaCompatibility.compatibleAfterMigration();
            }
            if (result.isCompatibleWithReconfiguredSerializer()) {
                return TypeSerializerSchemaCompatibility.compatibleWithReconfiguredSerializer(
                        new FlinkSerializerAdapter<>(result.getReconfiguredSerializer()));
            }
            return TypeSerializerSchemaCompatibility.incompatible();
        }
    }
}
