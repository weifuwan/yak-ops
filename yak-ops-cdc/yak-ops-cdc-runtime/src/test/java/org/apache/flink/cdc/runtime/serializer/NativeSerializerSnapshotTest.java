/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.flink.cdc.runtime.serializer;

import org.apache.flink.cdc.common.memory.DataInputDeserializer;
import org.apache.flink.cdc.common.memory.DataOutputSerializer;
import org.apache.flink.cdc.common.serialization.TypeSerializer;
import org.apache.flink.cdc.common.serialization.TypeSerializerSnapshot;
import org.apache.flink.cdc.common.serialization.TypeSerializerSnapshotSerializationUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the Flink 1.20.3 snapshot wire contract after native CDC migration. */
class NativeSerializerSnapshotTest {

    @Test
    void simpleSerializerSnapshotRetainsVersionedEnvelope() throws IOException {
        TypeSerializerSnapshot<Integer> original = IntSerializer.INSTANCE.snapshotConfiguration();
        DataOutputSerializer snapshotOutput = new DataOutputSerializer(128);
        TypeSerializerSnapshotSerializationUtil.writeSerializerSnapshot(snapshotOutput, original);
        DataInputDeserializer snapshotInput =
                new DataInputDeserializer(snapshotOutput.getCopyOfBuffer());

        // Flink 1.20.3's snapshot serialization proxy format version.
        assertEquals(2, snapshotInput.readInt());
        // Advance a fresh input through the complete versioned snapshot.
        snapshotInput = new DataInputDeserializer(snapshotOutput.getCopyOfBuffer());
        TypeSerializerSnapshot<Integer> restored =
                TypeSerializerSnapshotSerializationUtil.readSerializerSnapshot(
                        snapshotInput, NativeSerializerSnapshotTest.class.getClassLoader());
        TypeSerializer<Integer> serializer = restored.restoreSerializer();
        assertEquals(IntSerializer.class, serializer.getClass());

        DataOutputSerializer valueOutput = new DataOutputSerializer(16);
        serializer.serialize(123456, valueOutput);
        assertEquals(123456, serializer.deserialize(
                new DataInputDeserializer(valueOutput.getCopyOfBuffer())));
    }

    @Test
    void compositeSerializerSnapshotRestoresNestedSerializer() throws IOException {
        ListSerializer<Integer> original = new ListSerializer<>(IntSerializer.INSTANCE);
        DataOutputSerializer snapshotOutput = new DataOutputSerializer(256);
        TypeSerializerSnapshotSerializationUtil.writeSerializerSnapshot(
                snapshotOutput, original.snapshotConfiguration());
        TypeSerializerSnapshot<List<Integer>> restored =
                TypeSerializerSnapshotSerializationUtil.readSerializerSnapshot(
                        new DataInputDeserializer(snapshotOutput.getCopyOfBuffer()),
                        NativeSerializerSnapshotTest.class.getClassLoader());

        assertTrue(restored.restoreSerializer() instanceof ListSerializer);
        List<Integer> values = Arrays.asList(1, 3, 5, 8);
        DataOutputSerializer output = new DataOutputSerializer(128);
        restored.restoreSerializer().serialize(values, output);
        assertEquals(values, restored.restoreSerializer().deserialize(
                new DataInputDeserializer(output.getCopyOfBuffer())));
    }
}
