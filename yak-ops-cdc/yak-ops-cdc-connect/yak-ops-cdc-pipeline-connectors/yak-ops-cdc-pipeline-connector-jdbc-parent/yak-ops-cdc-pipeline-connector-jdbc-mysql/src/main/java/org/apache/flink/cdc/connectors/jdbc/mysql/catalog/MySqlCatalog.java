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

package org.apache.flink.cdc.connectors.jdbc.mysql.catalog;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.catalog.AbstractJdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;

import java.sql.Types;
import java.util.Locale;

/**
 * MySQL target catalog: JDBC uses the database name as TABLE_CAT, not TABLE_SCHEM.
 *
 * <p>Metadata is mapped through CDC's cross-database logical types; vendor-only types such as
 * MEDIUMINT and unsigned integers are widened just as in MySQL CDC source metadata.
 */
public class MySqlCatalog extends AbstractJdbcCatalog {
    public MySqlCatalog(JdbcDialect dialect, JdbcSinkConfig config) {
        super(dialect, config);
    }

    // Package-visible for connection-free metadata regression tests.
    MySqlCatalog(JdbcDialect dialect, JdbcConnectionFactory connectionFactory) {
        super(dialect, connectionFactory);
    }

    @Override
    protected String jdbcCatalog(TableId tableId) {
        return tableId.getSchemaName();
    }

    @Override
    protected String jdbcSchema(TableId tableId) {
        return null;
    }

    @Override
    protected DataType fromJdbcColumn(
            int jdbcType, String nativeType, int precision, int scale) {
        String sqlType = nativeType == null ? "" : nativeType.toUpperCase(Locale.ROOT);
        boolean unsigned = sqlType.contains("UNSIGNED");
        int length = Math.max(1, precision);
        int timePrecision = Math.max(0, Math.min(scale, 9));
        if (sqlType.contains("TEXT") || sqlType.equals("JSON")
                || sqlType.startsWith("ENUM") || sqlType.startsWith("SET")) {
            return DataTypes.STRING();
        }
        if (sqlType.startsWith("DATETIME")) {
            return DataTypes.TIMESTAMP(timePrecision);
        }
        if (sqlType.startsWith("TIMESTAMP")) {
            return DataTypes.TIMESTAMP_LTZ(timePrecision);
        }
        switch (jdbcType) {
            case Types.BOOLEAN:
                return DataTypes.BOOLEAN();
            case Types.BIT:
                return length == 1 ? DataTypes.BOOLEAN() : DataTypes.VARBINARY(length);
            case Types.TINYINT:
                return unsigned ? DataTypes.SMALLINT() : DataTypes.TINYINT();
            case Types.SMALLINT:
                return unsigned ? DataTypes.INT() : DataTypes.SMALLINT();
            case Types.INTEGER:
                return unsigned ? DataTypes.BIGINT() : DataTypes.INT();
            case Types.BIGINT:
                return unsigned ? DataTypes.DECIMAL(20, 0) : DataTypes.BIGINT();
            case Types.DECIMAL:
            case Types.NUMERIC:
                int decimalPrecision = Math.max(1, Math.min(precision, 38));
                return DataTypes.DECIMAL(
                        decimalPrecision, Math.max(0, Math.min(scale, decimalPrecision)));
            case Types.FLOAT:
            case Types.REAL:
                return DataTypes.FLOAT();
            case Types.DOUBLE:
                return DataTypes.DOUBLE();
            case Types.CHAR:
            case Types.NCHAR:
                return DataTypes.CHAR(length);
            case Types.VARCHAR:
            case Types.NVARCHAR:
                return DataTypes.VARCHAR(length);
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
                return DataTypes.STRING();
            case Types.BINARY:
                return DataTypes.BINARY(length);
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                return DataTypes.VARBINARY(length);
            case Types.DATE:
                return DataTypes.DATE();
            case Types.TIME:
                return DataTypes.TIME(timePrecision);
            case Types.TIMESTAMP:
                return DataTypes.TIMESTAMP(timePrecision);
            default:
                throw new IllegalArgumentException(
                        "Unsupported MySQL JDBC metadata type: "
                                + nativeType
                                + " ("
                                + jdbcType
                                + ")");
        }
    }
}
