/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package org.apache.flink.cdc.common.memory;

import org.apache.flink.cdc.common.data.binary.BinarySegmentUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySegmentPortTest {

    @Test
    void heapSegmentsKeepPrimitiveReadWriteSemantics() {
        MemorySegment segment = MemorySegmentFactory.allocateUnpooledSegment(32);
        segment.putInt(0, 0x12345678);
        segment.putLong(8, 0x1020304050607080L);
        assertEquals(0x12345678, segment.getInt(0));
        assertEquals(0x1020304050607080L, segment.getLong(8));
    }

    @Test
    void wrappedHeapSegmentsShareOriginalStorage() {
        byte[] values = {1, 2, 3, 4};
        MemorySegment segment = MemorySegmentFactory.wrap(values);
        segment.put(2, (byte) 9);
        assertEquals(9, values[2]);
    }

    @Test
    void binaryDataCopiesAcrossSegmentBoundaries() {
        MemorySegment[] segments = {
            MemorySegmentFactory.wrap(new byte[] {1, 2, 3, 4}),
            MemorySegmentFactory.wrap(new byte[] {5, 6, 7, 8})
        };
        assertArrayEquals(
                new byte[] {3, 4, 5, 6},
                BinarySegmentUtils.copyToBytes(segments, 2, 4));
    }

    @Test
    void offHeapMemoryRetainsPrimitiveAccess() {
        MemorySegment segment = MemorySegmentFactory.allocateUnpooledOffHeapMemory(32);
        try {
            assertTrue(segment.isOffHeap());
            segment.putInt(0, 2026);
            assertEquals(2026, segment.getInt(0));
        } finally {
            segment.free();
        }
    }
}
