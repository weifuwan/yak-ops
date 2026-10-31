/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Yak Ops: RowData, structured table identity and value hashing
 * for binary keys in the local runtime.
 */
package io.yak.ops.core.sink;

import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.OperationType;
import io.yak.ops.core.function.HashFunction;
import io.yak.ops.core.function.HashFunctionProvider;
import io.yak.ops.core.schema.Schema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Hashes the table identity and primary-key values using the Flink CDC partition contract. */
public final class DefaultDataChangeEventHashFunctionProvider implements HashFunctionProvider<DataChangeEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public HashFunction<DataChangeEvent> getHashFunction(TableId tableId, Schema schema) {
        Objects.requireNonNull(schema, "schema");
        List<RowData.FieldGetter> keyGetters = new ArrayList<>();
        for (String primaryKey : schema.primaryKeys()) {
            int index = -1;
            for (int position = 0; position < schema.columns().size(); position++) {
                if (schema.columns().get(position).name().equals(primaryKey)) {
                    index = position;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("Primary-key column is absent from the schema: " + primaryKey);
            }
            keyGetters.add(RowData.createFieldGetter(schema.columns().get(index).type(), index));
        }
        List<RowData.FieldGetter> getters = List.copyOf(keyGetters);
        return event -> {
            Objects.requireNonNull(event, "event");
            TableId actual = event.tableId();
            if (tableId != null && !tableId.equals(actual)) {
                throw new IllegalArgumentException("Data event belongs to another table");
            }
            List<Object> values = new ArrayList<>();
            if (actual.catalog() != null) {
                values.add(actual.catalog());
            }
            if (actual.schema() != null) {
                values.add(actual.schema());
            }
            values.add(actual.table());
            RowData row = event.op() == OperationType.DELETE ? event.before() : event.after();
            for (RowData.FieldGetter getter : getters) {
                values.add(getter.getFieldOrNull(row));
            }
            return (Arrays.deepHashCode(values.toArray()) * 31) & Integer.MAX_VALUE;
        };
    }
}
