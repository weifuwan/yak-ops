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

package io.yak.ops.cdc.common.types.utils;

import io.yak.ops.cdc.common.data.ArrayData;
import io.yak.ops.cdc.common.data.DateData;
import io.yak.ops.cdc.common.data.DecimalData;
import io.yak.ops.cdc.common.data.MapData;
import io.yak.ops.cdc.common.data.RecordData;
import io.yak.ops.cdc.common.data.StringData;
import io.yak.ops.cdc.common.data.TimeData;
import io.yak.ops.cdc.common.data.TimestampData;
import io.yak.ops.cdc.common.data.ZonedTimestampData;
import io.yak.ops.cdc.common.types.DataType;

/** Internal type-to-class mapping from Flink CDC 3.7 without Flink Table dependencies. */
public class DataTypeUtils {

    public static Class<?> toInternalConversionClass(DataType type) {
        // ordered by type root definition
        switch (type.getTypeRoot()) {
            case CHAR:
            case VARCHAR:
                return StringData.class;
            case BOOLEAN:
                return Boolean.class;
            case BINARY:
            case VARBINARY:
                return byte[].class;
            case DECIMAL:
                return DecimalData.class;
            case TINYINT:
                return Byte.class;
            case SMALLINT:
                return Short.class;
            case INTEGER:
                return Integer.class;
            case DATE:
                return DateData.class;
            case TIME_WITHOUT_TIME_ZONE:
                return TimeData.class;
            case BIGINT:
                return Long.class;
            case FLOAT:
                return Float.class;
            case DOUBLE:
                return Double.class;
            case TIMESTAMP_WITHOUT_TIME_ZONE:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return TimestampData.class;
            case TIMESTAMP_WITH_TIME_ZONE:
                return ZonedTimestampData.class;
            case ARRAY:
                return ArrayData.class;
            case MAP:
                return MapData.class;
            case ROW:
                return RecordData.class;
            default:
                throw new IllegalArgumentException("Illegal type: " + type);
        }
    }

}
