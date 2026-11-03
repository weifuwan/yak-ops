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

package org.apache.flink.cdc.common.types.utils;

import org.apache.flink.cdc.common.data.ArrayData;
import org.apache.flink.cdc.common.data.DateData;
import org.apache.flink.cdc.common.data.DecimalData;
import org.apache.flink.cdc.common.data.MapData;
import org.apache.flink.cdc.common.data.RecordData;
import org.apache.flink.cdc.common.data.StringData;
import org.apache.flink.cdc.common.data.TimeData;
import org.apache.flink.cdc.common.data.TimestampData;
import org.apache.flink.cdc.common.data.ZonedTimestampData;
import org.apache.flink.cdc.common.types.DataTypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link DataTypeUtils}. */
class DataTypeUtilsTest {

    @Test
    void testToInternalConversionClass() {
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.CHAR(10)))
                .isEqualTo(StringData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.VARCHAR(10)))
                .isEqualTo(StringData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.BOOLEAN()))
                .isEqualTo(Boolean.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.BINARY(10)))
                .isEqualTo(byte[].class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.VARBINARY(10)))
                .isEqualTo(byte[].class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.DECIMAL(10, 2)))
                .isEqualTo(DecimalData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.TINYINT()))
                .isEqualTo(Byte.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.SMALLINT()))
                .isEqualTo(Short.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.INT()))
                .isEqualTo(Integer.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.DATE()))
                .isEqualTo(DateData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.TIME(6)))
                .isEqualTo(TimeData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.BIGINT()))
                .isEqualTo(Long.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.FLOAT()))
                .isEqualTo(Float.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.DOUBLE()))
                .isEqualTo(Double.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.TIMESTAMP(6)))
                .isEqualTo(TimestampData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.TIMESTAMP_LTZ(6)))
                .isEqualTo(TimestampData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.TIMESTAMP_TZ(6)))
                .isEqualTo(ZonedTimestampData.class);
        assertThat(DataTypeUtils.toInternalConversionClass(DataTypes.ARRAY(DataTypes.INT())))
                .isEqualTo(ArrayData.class);
        assertThat(
                        DataTypeUtils.toInternalConversionClass(
                                DataTypes.MAP(DataTypes.STRING(), DataTypes.INT())))
                .isEqualTo(MapData.class);
        assertThat(
                        DataTypeUtils.toInternalConversionClass(
                                DataTypes.ROW(DataTypes.FIELD("id", DataTypes.BIGINT()))))
                .isEqualTo(RecordData.class);
    }
}
