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

package org.apache.flink.cdc.connectors.jdbc.internal.converter;

import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypeRoot;
import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Default JDBC 4.2 parameter binding for typed CDC values. */
public class DefaultJdbcRowConverter implements JdbcRowConverter {

    @Override
    public JdbcStatementBuilder<JdbcRowData> createStatementBuilder(
            Schema tableSchema, List<String> statementColumns, List<String> rowColumns) {
        final List<DataType> statementTypes = new ArrayList<>(statementColumns.size());
        int[] indexes = new int[statementColumns.size()];
        for (int i = 0; i < statementColumns.size(); i++) {
            String columnName = statementColumns.get(i);
            indexes[i] = rowColumns.indexOf(columnName);
            checkArgument(
                    indexes[i] >= 0, "Missing JDBC column %s in row schema", statementColumns.get(i));
            if (tableSchema != null) {
                Column column =
                        tableSchema
                                .getColumn(columnName)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Missing JDBC type for column "
                                                                + columnName));
                statementTypes.add(column.getType());
            }
        }

        return (statement, rowData) -> {
            Object[] values = checkNotNull(rowData.getRows(), "JDBC row values must not be null");
            checkArgument(
                    values.length == rowColumns.size(),
                    "JDBC row contains %s values but schema has %s columns",
                    values.length,
                    rowColumns.size());
            for (int i = 0; i < indexes.length; i++) {
                setJdbcParameter(
                        statement,
                        i + 1,
                        values[indexes[i]],
                        tableSchema == null ? null : statementTypes.get(i));
            }
        };
    }

    protected void setJdbcParameter(
            PreparedStatement statement, int index, Object value, DataType dataType)
            throws SQLException {
        if (value == null && dataType != null) {
            statement.setNull(index, jdbcType(dataType.getTypeRoot()));
        } else if (value instanceof LocalDate) {
            statement.setDate(index, Date.valueOf((LocalDate) value));
        } else if (value instanceof Instant) {
            statement.setTimestamp(index, Timestamp.from((Instant) value));
        } else if (value instanceof ZonedDateTime) {
            statement.setTimestamp(index, Timestamp.from(((ZonedDateTime) value).toInstant()));
        } else if (value instanceof byte[]) {
            statement.setBytes(index, (byte[]) value);
        } else if (value instanceof BigDecimal) {
            statement.setBigDecimal(index, (BigDecimal) value);
        } else {
            // Includes LocalTime and LocalDateTime, supported by JDBC 4.2 drivers.
            statement.setObject(index, value);
        }
    }

    /** JDBC types for typed nulls; no driver-specific converters are required by the core. */
    protected int jdbcType(DataTypeRoot root) {
        switch (root) {
            case BOOLEAN:
                return Types.BOOLEAN;
            case CHAR:
                return Types.CHAR;
            case VARCHAR:
                return Types.VARCHAR;
            case BINARY:
                return Types.BINARY;
            case VARBINARY:
                return Types.VARBINARY;
            case DECIMAL:
                return Types.DECIMAL;
            case TINYINT:
                return Types.TINYINT;
            case SMALLINT:
                return Types.SMALLINT;
            case INTEGER:
                return Types.INTEGER;
            case BIGINT:
                return Types.BIGINT;
            case FLOAT:
                return Types.FLOAT;
            case DOUBLE:
                return Types.DOUBLE;
            case DATE:
                return Types.DATE;
            case TIME_WITHOUT_TIME_ZONE:
                return Types.TIME;
            case TIMESTAMP_WITHOUT_TIME_ZONE:
                return Types.TIMESTAMP;
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
            case TIMESTAMP_WITH_TIME_ZONE:
                return Types.TIMESTAMP_WITH_TIMEZONE;
            default:
                return Types.OTHER;
        }
    }
}
