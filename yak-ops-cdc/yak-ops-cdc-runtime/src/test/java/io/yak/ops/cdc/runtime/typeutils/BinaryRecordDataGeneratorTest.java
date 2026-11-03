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

package io.yak.ops.cdc.runtime.typeutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.data.DecimalData;
import io.yak.ops.cdc.common.data.GenericArrayData;
import io.yak.ops.cdc.common.data.GenericMapData;
import io.yak.ops.cdc.common.data.GenericRecordData;
import io.yak.ops.cdc.common.data.LocalZonedTimestampData;
import io.yak.ops.cdc.common.data.TimestampData;
import io.yak.ops.cdc.common.data.ZonedTimestampData;
import io.yak.ops.cdc.common.data.binary.BinaryArrayData;
import io.yak.ops.cdc.common.data.binary.BinaryRecordData;
import io.yak.ops.cdc.common.data.binary.BinaryStringData;
import io.yak.ops.cdc.common.types.DataTypes;
import io.yak.ops.cdc.common.types.RowType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests for DataType-driven binary record construction. */
class BinaryRecordDataGeneratorTest {

    @Test
    void generatesPrimitiveAndNestedRecordsFromRowType() {
        RowType childType =
                DataTypes.ROW(
                        DataTypes.FIELD("child_id", DataTypes.INT()),
                        DataTypes.FIELD("child_name", DataTypes.STRING()));
        RowType rowType =
                DataTypes.ROW(
                        DataTypes.FIELD("id", DataTypes.BIGINT()),
                        DataTypes.FIELD("name", DataTypes.STRING()),
                        DataTypes.FIELD("amount", DataTypes.DECIMAL(20, 4)),
                        DataTypes.FIELD("tags", DataTypes.ARRAY(DataTypes.STRING())),
                        DataTypes.FIELD(
                                "attributes", DataTypes.MAP(DataTypes.STRING(), DataTypes.INT())),
                        DataTypes.FIELD("child", childType),
                        DataTypes.FIELD("created_at", DataTypes.TIMESTAMP(9)),
                        DataTypes.FIELD("updated_at", DataTypes.TIMESTAMP_LTZ(9)),
                        DataTypes.FIELD("observed_at", DataTypes.TIMESTAMP_TZ(9)));

        Map<Object, Object> attributes = new LinkedHashMap<>();
        attributes.put(BinaryStringData.fromString("region"), 1);
        attributes.put(BinaryStringData.fromString("tier"), 2);

        Object[] values =
                new Object[] {
                    1001L,
                    BinaryStringData.fromString("Yak CDC"),
                    DecimalData.fromBigDecimal(new BigDecimal("1234567890123456.7890"), 20, 4),
                    new GenericArrayData(
                            new Object[] {
                                BinaryStringData.fromString("full"), null,
                                BinaryStringData.fromString("incremental")
                            }),
                    new GenericMapData(attributes),
                    GenericRecordData.of(7, BinaryStringData.fromString("nested")),
                    TimestampData.fromLocalDateTime(
                            LocalDateTime.of(2026, 11, 3, 11, 30, 0, 123456789)),
                    LocalZonedTimestampData.fromInstant(Instant.parse("2026-11-03T03:30:00.123456789Z")),
                    ZonedTimestampData.of(1793676600123L, 456789, "Asia/Singapore")
                };

        BinaryRecordDataGenerator generator = new BinaryRecordDataGenerator(rowType);
        BinaryRecordData record = generator.generate(values);

        assertEquals(9, record.getArity());
        assertEquals(1001L, record.getLong(0));
        assertEquals("Yak CDC", record.getString(1).toString());
        assertEquals(
                new BigDecimal("1234567890123456.7890"),
                record.getDecimal(2, 20, 4).toBigDecimal());

        BinaryArrayData tags = (BinaryArrayData) record.getArray(3);
        assertEquals(3, tags.size());
        assertEquals("full", tags.getString(0).toString());
        assertTrue(tags.isNullAt(1));
        assertEquals("incremental", tags.getString(2).toString());

        BinaryArrayData keys = (BinaryArrayData) record.getMap(4).keyArray();
        BinaryArrayData mapValues = (BinaryArrayData) record.getMap(4).valueArray();
        assertEquals("region", keys.getString(0).toString());
        assertEquals(1, mapValues.getInt(0));
        assertEquals("tier", keys.getString(1).toString());
        assertEquals(2, mapValues.getInt(1));

        assertEquals(7, record.getRow(5, 2).getInt(0));
        assertEquals("nested", record.getRow(5, 2).getString(1).toString());
        assertEquals(
                TimestampData.fromLocalDateTime(
                        LocalDateTime.of(2026, 11, 3, 11, 30, 0, 123456789)),
                record.getTimestamp(6, 9));
        assertEquals(
                LocalZonedTimestampData.fromInstant(
                        Instant.parse("2026-11-03T03:30:00.123456789Z")),
                record.getLocalZonedTimestampData(7, 9));
        assertEquals("Asia/Singapore", record.getZonedTimestamp(8, 9).getZoneId());
    }

    @Test
    void generatorCanConvertExistingGenericRecordData() {
        RowType rowType =
                DataTypes.ROW(
                        DataTypes.FIELD("id", DataTypes.INT()),
                        DataTypes.FIELD("name", DataTypes.STRING()));
        BinaryRecordData record =
                new BinaryRecordDataGenerator(rowType)
                        .generate(
                                GenericRecordData.of(
                                        42, BinaryStringData.fromString("from generic record")));

        assertEquals(42, record.getInt(0));
        assertEquals("from generic record", record.getString(1).toString());
    }

    @Test
    void generatedRecordsDoNotShareReusableWriterMemory() {
        RowType rowType = DataTypes.ROW(DataTypes.FIELD("value", DataTypes.STRING()));
        BinaryRecordDataGenerator generator = new BinaryRecordDataGenerator(rowType);

        BinaryRecordData first =
                generator.generate(new Object[] {BinaryStringData.fromString("first")});
        BinaryRecordData second =
                generator.generate(new Object[] {BinaryStringData.fromString("second")});

        assertEquals("first", first.getString(0).toString());
        assertEquals("second", second.getString(0).toString());
        assertNotSame(first.getSegments()[0], second.getSegments()[0]);
    }

    @Test
    void binaryArraysSupportLocalAndZonedTimestampValues() {
        RowType rowType =
                DataTypes.ROW(
                        DataTypes.FIELD(
                                "local_timestamps",
                                DataTypes.ARRAY(DataTypes.TIMESTAMP_LTZ(9))),
                        DataTypes.FIELD(
                                "zoned_timestamps",
                                DataTypes.ARRAY(DataTypes.TIMESTAMP_TZ(9))));
        LocalZonedTimestampData local =
                LocalZonedTimestampData.fromInstant(
                        Instant.parse("2026-11-03T03:30:00.123456789Z"));
        ZonedTimestampData zoned =
                ZonedTimestampData.of(1793676600123L, 456789, "Asia/Singapore");

        BinaryRecordData record =
                new BinaryRecordDataGenerator(rowType)
                        .generate(
                                new Object[] {
                                    new GenericArrayData(new Object[] {local}),
                                    new GenericArrayData(new Object[] {zoned})
                                });

        assertEquals(local, record.getArray(0).getLocalZonedTimestamp(0, 9));
        assertEquals(zoned, record.getArray(1).getZonedTimestamp(0, 9));
    }
}
