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

package io.yak.ops.cdc.runtime.serializer.data.writer;

import io.yak.ops.cdc.common.annotation.Internal;
import io.yak.ops.cdc.common.data.ArrayData;
import io.yak.ops.cdc.common.data.DateData;
import io.yak.ops.cdc.common.data.DecimalData;
import io.yak.ops.cdc.common.data.LocalZonedTimestampData;
import io.yak.ops.cdc.common.data.MapData;
import io.yak.ops.cdc.common.data.RecordData;
import io.yak.ops.cdc.common.data.StringData;
import io.yak.ops.cdc.common.data.TimeData;
import io.yak.ops.cdc.common.data.TimestampData;
import io.yak.ops.cdc.common.data.ZonedTimestampData;
import io.yak.ops.cdc.common.data.binary.BinaryArrayData;
import io.yak.ops.cdc.common.data.binary.BinaryFormat;
import io.yak.ops.cdc.common.data.binary.BinaryMapData;
import io.yak.ops.cdc.common.data.binary.BinaryRecordData;
import io.yak.ops.cdc.common.data.binary.BinarySegmentUtils;
import io.yak.ops.cdc.common.data.binary.BinaryStringData;
import io.yak.ops.cdc.common.memory.MemorySegment;
import io.yak.ops.cdc.common.memory.MemorySegmentFactory;
import io.yak.ops.cdc.common.types.DataType;
import io.yak.ops.cdc.common.types.RowType;
import io.yak.ops.cdc.common.types.variant.BinaryVariant;
import io.yak.ops.cdc.common.types.variant.Variant;
import io.yak.ops.cdc.runtime.typeutils.BinaryRecordDataGenerator;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.yak.ops.cdc.common.data.binary.BinaryRecordData.TIMESTAMP_DELIMITER;

/**
 * Use the special format to write data to a {@link MemorySegment} (its capacity grows
 * automatically).
 *
 * <p>If write a format binary: 1. New a writer. 2. Write each field by writeXX or setNullAt.
 * (Variable length fields can not be written repeatedly.) 3. Invoke {@link #complete()}.
 *
 * <p>If want to reuse this writer, please invoke {@link #reset()} first.
 */
@Internal
abstract class AbstractBinaryWriter implements BinaryWriter {

    protected MemorySegment segment;

    protected int cursor;

    /** Set offset and size to fix len part. */
    protected abstract void setOffsetAndSize(int pos, int offset, long size);

    /** Get field offset. */
    protected abstract int getFieldOffset(int pos);

    /** After grow, need point to new memory. */
    protected abstract void afterGrow();

    protected abstract void setNullBit(int ordinal);

    /** See {@link BinarySegmentUtils#readStringData(MemorySegment[], int, int, long)}. */
    @Override
    public void writeString(int pos, StringData input) {
        BinaryStringData string = (BinaryStringData) input;
        if (string.getSegments() == null) {
            String javaObject = string.toString();
            writeBytes(pos, javaObject.getBytes(StandardCharsets.UTF_8));
        } else {
            int len = string.getSizeInBytes();
            if (len <= 7) {
                byte[] bytes = BinarySegmentUtils.allocateReuseBytes(len);
                BinarySegmentUtils.copyToBytes(
                        string.getSegments(), string.getOffset(), bytes, 0, len);
                writeBytesToFixLenPart(segment, getFieldOffset(pos), bytes, len);
            } else {
                writeSegmentsToVarLenPart(pos, string.getSegments(), string.getOffset(), len);
            }
        }
    }

    private void writeBytes(int pos, byte[] bytes) {
        int len = bytes.length;
        if (len <= BinaryFormat.MAX_FIX_PART_DATA_SIZE) {
            writeBytesToFixLenPart(segment, getFieldOffset(pos), bytes, len);
        } else {
            writeBytesToVarLenPart(pos, bytes, len);
        }
    }

    @Override
    public void writeArray(int pos, ArrayData input, DataType elementType) {
        BinaryArrayData binary = BinaryWriter.toBinaryArray(input, elementType);
        writeSegmentsToVarLenPart(
                pos, binary.getSegments(), binary.getOffset(), binary.getSizeInBytes());
    }

    @Override
    public void writeMap(int pos, MapData input, DataType keyType, DataType valueType) {
        BinaryArrayData keys = BinaryWriter.toBinaryArray(input.keyArray(), keyType);
        BinaryArrayData values = BinaryWriter.toBinaryArray(input.valueArray(), valueType);
        BinaryMapData binary = BinaryMapData.valueOf(keys, values);
        writeSegmentsToVarLenPart(
                pos, binary.getSegments(), binary.getOffset(), binary.getSizeInBytes());
    }

    @Override
    public void writeRecord(int pos, RecordData input, RowType rowType) {
        BinaryRecordData recordData =
                input instanceof BinaryRecordData
                        ? (BinaryRecordData) input
                        : new BinaryRecordDataGenerator(rowType).generate(input);
        writeSegmentsToVarLenPart(
                pos, recordData.getSegments(), recordData.getOffset(), recordData.getSizeInBytes());
    }

    @Override
    public void writeBinary(int pos, byte[] bytes) {
        int len = bytes.length;
        if (len <= BinaryFormat.MAX_FIX_PART_DATA_SIZE) {
            writeBytesToFixLenPart(segment, getFieldOffset(pos), bytes, len);
        } else {
            writeBytesToVarLenPart(pos, bytes, len);
        }
    }

    @Override
    public void writeDecimal(int pos, DecimalData value, int precision) {
        if (DecimalData.isCompact(precision)) {
            assert value != null;
            writeLong(pos, value.toUnscaledLong());
        } else {
            // grow the global buffer before writing data.
            ensureCapacity(16);

            // zero-out the bytes
            segment.putLong(cursor, 0L);
            segment.putLong(cursor + 8, 0L);

            // Make sure Decimal object has the same scale as DecimalType.
            // Note that we may pass in null Decimal object to set null for it.
            if (value == null) {
                setNullBit(pos);
                // keep the offset for future update
                setOffsetAndSize(pos, cursor, 0);
            } else {
                final byte[] bytes = value.toUnscaledBytes();
                assert bytes.length <= 16;

                // Write the bytes to the variable length portion.
                segment.put(cursor, bytes, 0, bytes.length);
                setOffsetAndSize(pos, cursor, bytes.length);
            }

            // move the cursor forward.
            cursor += 16;
        }
    }

    @Override
    public void writeTimestamp(int pos, TimestampData value, int precision) {
        // store the nanoOfMillisecond in fixed-length part as offset and nanoOfMillisecond
        ensureCapacity(8);

        if (value == null) {
            setNullBit(pos);
            // zero-out the bytes
            segment.putLong(cursor, 0L);
            setOffsetAndSize(pos, cursor, 0);
        } else {
            segment.putLong(cursor, value.getMillisecond());
            setOffsetAndSize(pos, cursor, value.getNanoOfMillisecond());
        }

        cursor += 8;
    }

    @Override
    public void writeLocalZonedTimestamp(int pos, LocalZonedTimestampData value, int precision) {
        // store the nanoOfMillisecond in fixed-length part as offset and nanoOfMillisecond
        ensureCapacity(8);

        if (value == null) {
            setNullBit(pos);
            // zero-out the bytes
            segment.putLong(cursor, 0L);
            setOffsetAndSize(pos, cursor, 0);
        } else {
            segment.putLong(cursor, value.getEpochMillisecond());
            setOffsetAndSize(pos, cursor, value.getEpochNanoOfMillisecond());
        }

        cursor += 8;
    }

    @Override
    public void writeZonedTimestamp(int pos, ZonedTimestampData value, int precision) {
        String timestampString =
                String.join(
                        TIMESTAMP_DELIMITER,
                        Arrays.asList(
                                String.valueOf(value.getMillisecond()),
                                String.valueOf(value.getNanoOfMillisecond()),
                                value.getZoneId()));
        writeString(pos, new BinaryStringData(timestampString));
    }

    @Override
    public void writeDate(int pos, DateData value) {
        writeInt(pos, value.toEpochDay());
    }

    @Override
    public void writeTime(int pos, TimeData value, int precision) {
        writeInt(pos, value.toMillisOfDay());
    }

    @Override
    public void writeVariant(int pos, Variant variant) {
        byte[] metadata = ((BinaryVariant) variant).getMetadata();
        byte[] value = ((BinaryVariant) variant).getValue();
        int metadataLen = metadata.length;

        int length = metadata.length + value.length + 4;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.putInt(metadataLen).put(metadata).put(value);

        writeBytesToVarLenPart(pos, buffer.array(), length);
    }

    private void zeroBytes(int offset, int size) {
        for (int i = offset; i < offset + size; i++) {
            segment.put(i, (byte) 0);
        }
    }

    protected void zeroOutPaddingBytes(int numBytes) {
        if ((numBytes & 0x07) > 0) {
            segment.putLong(cursor + ((numBytes >> 3) << 3), 0L);
        }
    }

    protected void ensureCapacity(int neededSize) {
        final int length = cursor + neededSize;
        if (segment.size() < length) {
            grow(length);
        }
    }

    private void writeSegmentsToVarLenPart(
            int pos, MemorySegment[] segments, int offset, int size) {
        final int roundedSize = roundNumberOfBytesToNearestWord(size);

        // grow the global buffer before writing data.
        ensureCapacity(roundedSize);

        zeroOutPaddingBytes(size);

        if (segments.length == 1) {
            segments[0].copyTo(offset, segment, cursor, size);
        } else {
            writeMultiSegmentsToVarLenPart(segments, offset, size);
        }

        setOffsetAndSize(pos, cursor, size);

        // move the cursor forward.
        cursor += roundedSize;
    }

    private void writeMultiSegmentsToVarLenPart(MemorySegment[] segments, int offset, int size) {
        // Write the bytes to the variable length portion.
        int needCopy = size;
        int fromOffset = offset;
        int toOffset = cursor;
        for (MemorySegment sourceSegment : segments) {
            int remain = sourceSegment.size() - fromOffset;
            if (remain > 0) {
                int copySize = remain > needCopy ? needCopy : remain;
                sourceSegment.copyTo(fromOffset, segment, toOffset, copySize);
                needCopy -= copySize;
                toOffset += copySize;
                fromOffset = 0;
            } else {
                fromOffset -= sourceSegment.size();
            }
        }
    }

    private void writeBytesToVarLenPart(int pos, byte[] bytes, int len) {
        final int roundedSize = roundNumberOfBytesToNearestWord(len);

        // grow the global buffer before writing data.
        ensureCapacity(roundedSize);

        zeroOutPaddingBytes(len);

        // Write the bytes to the variable length portion.
        segment.put(cursor, bytes, 0, len);

        setOffsetAndSize(pos, cursor, len);

        // move the cursor forward.
        cursor += roundedSize;
    }

    /** Increases the capacity to ensure that it can hold at least the minimum capacity argument. */
    private void grow(int minCapacity) {
        int oldCapacity = segment.size();
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        segment = MemorySegmentFactory.wrap(Arrays.copyOf(segment.getArray(), newCapacity));
        afterGrow();
    }

    protected static int roundNumberOfBytesToNearestWord(int numBytes) {
        int remainder = numBytes & 0x07;
        if (remainder == 0) {
            return numBytes;
        } else {
            return numBytes + (8 - remainder);
        }
    }

    private static void writeBytesToFixLenPart(
            MemorySegment segment, int fieldOffset, byte[] bytes, int len) {
        long firstByte = len | 0x80; // first bit is 1, other bits is len
        long sevenBytes = 0L; // real data
        if (BinaryRecordData.LITTLE_ENDIAN) {
            for (int i = 0; i < len; i++) {
                sevenBytes |= ((0x00000000000000FFL & bytes[i]) << (i * 8L));
            }
        } else {
            for (int i = 0; i < len; i++) {
                sevenBytes |= ((0x00000000000000FFL & bytes[i]) << ((6 - i) * 8L));
            }
        }

        final long offsetAndSize = (firstByte << 56) | sevenBytes;

        segment.putLong(fieldOffset, offsetAndSize);
    }

    @Internal
    public MemorySegment getSegments() {
        return segment;
    }

}
