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
import io.yak.ops.cdc.common.types.ArrayType;
import io.yak.ops.cdc.common.types.DataType;
import io.yak.ops.cdc.common.types.DecimalType;
import io.yak.ops.cdc.common.types.LocalZonedTimestampType;
import io.yak.ops.cdc.common.types.MapType;
import io.yak.ops.cdc.common.types.RowType;
import io.yak.ops.cdc.common.types.TimeType;
import io.yak.ops.cdc.common.types.TimestampType;
import io.yak.ops.cdc.common.types.ZonedTimestampType;
import io.yak.ops.cdc.common.types.variant.Variant;

/**
 * Writer to write a composite data format, like row, array. 1. Invoke {@link #reset()}. 2. Write
 * each field by writeXX or setNullAt. (Same field can not be written repeatedly.) 3. Invoke {@link
 * #complete()}.
 */
@Internal
public interface BinaryWriter {

    /** Reset writer to prepare next write. */
    void reset();

    /** Set null to this field. */
    void setNullAt(int pos);

    void writeBoolean(int pos, boolean value);

    void writeByte(int pos, byte value);

    void writeShort(int pos, short value);

    void writeInt(int pos, int value);

    void writeLong(int pos, long value);

    void writeFloat(int pos, float value);

    void writeDouble(int pos, double value);

    void writeString(int pos, StringData value);

    void writeBinary(int pos, byte[] bytes);

    void writeDecimal(int pos, DecimalData value, int precision);

    void writeTimestamp(int pos, TimestampData value, int precision);

    void writeLocalZonedTimestamp(int pos, LocalZonedTimestampData value, int precision);

    void writeZonedTimestamp(int pos, ZonedTimestampData value, int precision);

    void writeArray(int pos, ArrayData value, DataType elementType);

    void writeMap(int pos, MapData value, DataType keyType, DataType valueType);

    void writeRecord(int pos, RecordData value, RowType rowType);

    void writeDate(int pos, DateData value);

    void writeTime(int pos, TimeData value, int precision);

    void writeVariant(int pos, Variant variant);

    /** Finally, complete write to set real size to binary. */
    void complete();

    static void write(BinaryWriter writer, int pos, Object o, DataType type) {
        switch (type.getTypeRoot()) {
            case BOOLEAN:
                writer.writeBoolean(pos, (boolean) o);
                break;
            case TINYINT:
                writer.writeByte(pos, (byte) o);
                break;
            case SMALLINT:
                writer.writeShort(pos, (short) o);
                break;
            case INTEGER:
                writer.writeInt(pos, (int) o);
                break;
            case DATE:
                if (o instanceof DateData) {
                    writer.writeDate(pos, (DateData) o);
                } else {
                    // This path is kept for backward compatibility, as legacy data might represent
                    // dates as integers (days since epoch).
                    writer.writeInt(pos, (int) o);
                }
                break;
            case TIME_WITHOUT_TIME_ZONE:
                if (o instanceof TimeData) {
                    writer.writeTime(pos, (TimeData) o, ((TimeType) type).getPrecision());
                } else {
                    // This path is kept for backward compatibility, as legacy data might represent
                    // time as integers (milliseconds of the day).
                    writer.writeInt(pos, (int) o);
                }
                break;
            case BIGINT:
                writer.writeLong(pos, (long) o);
                break;
            case TIMESTAMP_WITHOUT_TIME_ZONE:
                TimestampType timestampType = (TimestampType) type;
                writer.writeTimestamp(pos, (TimestampData) o, timestampType.getPrecision());
                break;
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                LocalZonedTimestampType lzTs = (LocalZonedTimestampType) type;
                writer.writeLocalZonedTimestamp(
                        pos, (LocalZonedTimestampData) o, lzTs.getPrecision());
                break;
            case TIMESTAMP_WITH_TIME_ZONE:
                ZonedTimestampType zTs = (ZonedTimestampType) type;
                writer.writeZonedTimestamp(pos, (ZonedTimestampData) o, zTs.getPrecision());
                break;
            case FLOAT:
                writer.writeFloat(pos, (float) o);
                break;
            case DOUBLE:
                writer.writeDouble(pos, (double) o);
                break;
            case CHAR:
            case VARCHAR:
                writer.writeString(pos, (StringData) o);
                break;
            case DECIMAL:
                DecimalType decimalType = (DecimalType) type;
                writer.writeDecimal(pos, (DecimalData) o, decimalType.getPrecision());
                break;
            case ARRAY:
                writer.writeArray(pos, (ArrayData) o, ((ArrayType) type).getElementType());
                break;
            case MAP:
                MapType mapType = (MapType) type;
                writer.writeMap(pos, (MapData) o, mapType.getKeyType(), mapType.getValueType());
                break;
            case ROW:
                writer.writeRecord(pos, (RecordData) o, (RowType) type);
                break;
            case BINARY:
            case VARBINARY:
                writer.writeBinary(pos, (byte[]) o);
                break;
            case VARIANT:
                writer.writeVariant(pos, (Variant) o);
                break;
            default:
                throw new UnsupportedOperationException("Not support type: " + type);
        }
    }

    /** Converts an internal array into the same binary layout used by Flink CDC 3.7. */
    static BinaryArrayData toBinaryArray(ArrayData array, DataType elementType) {
        int elementSize = BinaryArrayData.calculateFixLengthPartSize(elementType);
        BinaryArrayData binaryArray = new BinaryArrayData();
        BinaryArrayWriter arrayWriter =
                new BinaryArrayWriter(binaryArray, array.size(), elementSize);
        BinaryArrayWriter.NullSetter nullSetter = BinaryArrayWriter.createNullSetter(elementType);
        ArrayData.ElementGetter elementGetter = ArrayData.createElementGetter(elementType);

        arrayWriter.reset();
        for (int i = 0; i < array.size(); i++) {
            if (array.isNullAt(i)) {
                nullSetter.setNull(arrayWriter, i);
            } else {
                write(arrayWriter, i, elementGetter.getElementOrNull(array, i), elementType);
            }
        }
        arrayWriter.complete();
        return binaryArray;
    }
}
