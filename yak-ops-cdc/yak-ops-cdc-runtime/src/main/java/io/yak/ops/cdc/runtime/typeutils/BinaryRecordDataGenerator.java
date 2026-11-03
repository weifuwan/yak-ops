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

import io.yak.ops.cdc.common.annotation.PublicEvolving;
import io.yak.ops.cdc.common.data.RecordData;
import io.yak.ops.cdc.common.data.binary.BinaryRecordData;
import io.yak.ops.cdc.common.types.DataType;
import io.yak.ops.cdc.common.types.RowType;
import io.yak.ops.cdc.runtime.serializer.data.writer.BinaryRecordDataWriter;
import io.yak.ops.cdc.runtime.serializer.data.writer.BinaryWriter;


import static io.yak.ops.cdc.common.utils.Preconditions.checkArgument;

/** Creates Flink CDC-compatible binary records from internal row values. */
@PublicEvolving
public class BinaryRecordDataGenerator {

    private final DataType[] dataTypes;

    private transient BinaryRecordData reuseRecordData;
    private transient BinaryRecordDataWriter reuseWriter;

    public BinaryRecordDataGenerator(RowType recordType) {
        this(recordType.getChildren().toArray(new DataType[0]));
    }

    public BinaryRecordDataGenerator(DataType[] dataTypes) {
        this.dataTypes = dataTypes.clone();
        this.reuseRecordData = new BinaryRecordData(dataTypes.length);
        this.reuseWriter = new BinaryRecordDataWriter(reuseRecordData);
    }

    /**
     * Creates a binary record from internal field values.
     *
     * <p>All non-null values must use the internal data structures described by {@link RecordData}.
     * The generator reuses its writer and is therefore not thread-safe. Returned records are
     * independent copies and remain valid after the next invocation.
     */
    public BinaryRecordData generate(Object[] rowFields) {
        checkArgument(
                dataTypes.length == rowFields.length,
                "The types and values must have the same length. But types is %s and values is %s",
                dataTypes.length,
                rowFields.length);

        reuseWriter.reset();
        for (int i = 0; i < dataTypes.length; i++) {
            if (rowFields[i] == null) {
                reuseWriter.setNullAt(i);
            } else {
                BinaryWriter.write(reuseWriter, i, rowFields[i], dataTypes[i]);
            }
        }
        reuseWriter.complete();
        return reuseRecordData.copy();
    }

    /** Creates an independent binary copy of an existing internal record. */
    public BinaryRecordData generate(RecordData recordData) {
        checkArgument(
                dataTypes.length == recordData.getArity(),
                "The types and values must have the same length. But types is %s and values is %s",
                dataTypes.length,
                recordData.getArity());
        if (recordData instanceof BinaryRecordData) {
            return ((BinaryRecordData) recordData).copy();
        }

        Object[] fields = new Object[dataTypes.length];
        for (int i = 0; i < dataTypes.length; i++) {
            if (!recordData.isNullAt(i)) {
                fields[i] =
                        RecordData.createFieldGetter(dataTypes[i], i).getFieldOrNull(recordData);
            }
        }
        return generate(fields);
    }
}
