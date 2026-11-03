/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.data.binary.BinaryArrayData;
import io.yak.ops.cdc.common.data.binary.BinaryMapData;
import io.yak.ops.cdc.common.data.binary.BinaryRecordData;
import io.yak.ops.cdc.common.data.binary.BinarySegmentUtils;
import io.yak.ops.cdc.common.data.binary.BinaryStringData;
import io.yak.ops.cdc.common.memory.MemorySegment;
import io.yak.ops.cdc.common.memory.MemorySegmentFactory;
import io.yak.ops.cdc.common.types.DataTypes;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Compatibility tests for Flink CDC 3.7 binary record layout without Flink dependencies. */
class BinaryRecordDataContractTest {

    @Test
    void heapMemorySegmentPreservesPrimitiveAccessAndCopy() {
        MemorySegment segment = MemorySegmentFactory.allocateUnpooledSegment(24);
        segment.putInt(0, 0x12345678);
        segment.putLong(8, 987654321L);
        assertEquals(0x12345678, segment.getInt(0));
        assertEquals(987654321L, segment.getLong(8));

        MemorySegment copy = MemorySegmentFactory.allocateUnpooledSegment(24);
        segment.copyTo(0, copy, 0, 24);
        segment.putLong(8, 5L);
        assertEquals(987654321L, copy.getLong(8));
        assertThrows(IndexOutOfBoundsException.class, () -> segment.getInt(23));
    }

    @Test
    void fixedAndVariableFieldsAndNullBitmapFollowUpstreamLayout() {
        int arity = 3;
        int bitmapSize = BinaryRecordData.calculateBitSetWidthInBytes(arity);
        int fixedSize = bitmapSize + arity * Long.BYTES;
        byte[] utf8 = "你好，Flink CDC".getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = MemorySegmentFactory.allocateUnpooledSegment(fixedSize + utf8.length);

        segment.putLong(bitmapSize, 1001L);
        segment.putLong(bitmapSize + Long.BYTES, ((long) fixedSize << 32) | utf8.length);
        segment.put(fixedSize, utf8);
        BinarySegmentUtils.bitSet(segment, 0, BinaryRecordData.HEADER_SIZE_IN_BITS + 2);

        BinaryRecordData record = new BinaryRecordData(arity);
        record.pointTo(segment, 0, fixedSize + utf8.length);
        assertEquals(arity, record.getArity());
        assertEquals(1001L, record.getLong(0));
        assertEquals("你好，Flink CDC", record.getString(1).toString());
        assertFalse(record.isNullAt(0));
        assertTrue(record.isNullAt(2));
        assertTrue(record.anyNull());

        BinaryRecordData copied = record.copy();
        assertNotSame(record.getSegments()[0], copied.getSegments()[0]);
        segment.putLong(bitmapSize, 9999L);
        assertEquals(1001L, copied.getLong(0));
        assertEquals("你好，Flink CDC", copied.getString(1).toString());
    }

    @Test
    void variableLengthFieldsMayCrossMemorySegments() {
        final int segmentSize = 64;
        final int valueOffset = 60;
        byte[] utf8 = "跨 Segment 的二进制 UTF-8 测试".getBytes(StandardCharsets.UTF_8);
        MemorySegment first = MemorySegmentFactory.allocateUnpooledSegment(segmentSize);
        MemorySegment second = MemorySegmentFactory.allocateUnpooledSegment(segmentSize);
        first.putLong(8, 42L);
        first.putLong(16, ((long) valueOffset << 32) | utf8.length);
        first.put(valueOffset, utf8, 0, segmentSize - valueOffset);
        second.put(0, utf8, segmentSize - valueOffset, utf8.length - (segmentSize - valueOffset));

        BinaryRecordData record = new BinaryRecordData(2);
        record.pointTo(new MemorySegment[] {first, second}, 0, valueOffset + utf8.length);
        assertEquals(42L, record.getLong(0));
        assertEquals("跨 Segment 的二进制 UTF-8 测试", record.getString(1).toString());
        BinaryRecordData copied = record.copy();
        assertEquals("跨 Segment 的二进制 UTF-8 测试", copied.getString(1).toString());
        assertEquals(valueOffset + utf8.length, copied.getSizeInBytes());
    }

    @Test
    void primitiveArraysAndBinaryMapsRetainTheirFormat() {
        BinaryArrayData keys = BinaryArrayData.fromPrimitiveArray(new int[] {1, 2, 3});
        BinaryArrayData values = BinaryArrayData.fromPrimitiveArray(new long[] {10L, 20L, 30L});
        assertEquals(3, keys.size());
        assertArrayEquals(new int[] {1, 2, 3}, keys.toIntArray());
        assertArrayEquals(new long[] {10L, 20L, 30L}, values.toLongArray());

        BinaryMapData map = BinaryMapData.valueOf(keys, values);
        assertEquals(3, map.size());
        assertEquals(Map.of(1, 10L, 2, 20L, 3, 30L),
                map.toJavaMap(DataTypes.INT(), DataTypes.BIGINT()));
        BinaryMapData copied = map.copy();
        assertEquals(map, copied);
        assertNotSame(map.getSegments()[0], copied.getSegments()[0]);
    }

    @Test
    void binaryStringsSupportLazyUtf8MaterializationAndDeepCopy() {
        BinaryStringData data = BinaryStringData.fromString("数据同步🙂");
        assertEquals("数据同步🙂", data.toString());
        byte[] utf8 = "数据同步🙂".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(utf8, data.toBytes());
        BinaryStringData fromBytes = BinaryStringData.fromBytes(utf8);
        assertEquals(data, fromBytes);
        BinaryStringData copied = fromBytes.copy();
        assertEquals(fromBytes, copied);
        assertNotSame(fromBytes.getSegments()[0], copied.getSegments()[0]);
    }

    @Test
    void crossSegmentPrimitiveReadAndWriteMatchNativeMemorySemantics() {
        MemorySegment first = MemorySegmentFactory.allocateUnpooledSegment(8);
        MemorySegment second = MemorySegmentFactory.allocateUnpooledSegment(8);
        MemorySegment[] segments = {first, second};
        BinarySegmentUtils.setLong(segments, 4, 0x123456789ABCDEFL);
        assertEquals(0x123456789ABCDEFL, BinarySegmentUtils.getLong(segments, 4));
        assertEquals(8, BinarySegmentUtils.copyToBytes(segments, 4, 8).length);
    }
}
