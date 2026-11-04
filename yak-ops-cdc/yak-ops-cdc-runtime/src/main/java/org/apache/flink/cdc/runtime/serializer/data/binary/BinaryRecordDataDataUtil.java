package org.apache.flink.cdc.runtime.serializer.data.binary;

import org.apache.flink.cdc.common.data.binary.BinaryRecordData;
import org.apache.flink.core.memory.MemorySegmentFactory;
import org.apache.flink.core.memory.MemoryUtils;

/**
 * Utilities for {@link BinaryRecordDataDataUtil}. Many of the methods in this class are used in
 * code generation.
 */
public class BinaryRecordDataDataUtil {

    public static final sun.misc.Unsafe UNSAFE = MemoryUtils.UNSAFE;
    public static final int BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    public static final BinaryRecordData EMPTY_ROW = new BinaryRecordData(0);

    static {
        int size = EMPTY_ROW.getFixedLengthPartSize();
        byte[] bytes = new byte[size];
        EMPTY_ROW.pointTo(MemorySegmentFactory.wrap(bytes), 0, size);
    }

    public static boolean byteArrayEquals(byte[] left, byte[] right, int length) {
        return byteArrayEquals(left, BYTE_ARRAY_BASE_OFFSET, right, BYTE_ARRAY_BASE_OFFSET, length);
    }

    public static boolean byteArrayEquals(
            Object left, long leftOffset, Object right, long rightOffset, int length) {
        int i = 0;

        while (i <= length - 8) {
            if (UNSAFE.getLong(left, leftOffset + i) != UNSAFE.getLong(right, rightOffset + i)) {
                return false;
            }
            i += 8;
        }

        while (i < length) {
            if (UNSAFE.getByte(left, leftOffset + i) != UNSAFE.getByte(right, rightOffset + i)) {
                return false;
            }
            i += 1;
        }
        return true;
    }
}
