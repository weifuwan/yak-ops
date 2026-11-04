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
import org.apache.flink.cdc.common.types.DecimalType;
import org.apache.flink.cdc.connectors.jdbc.catalog.AbstractJdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalogException;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;

/**
 * MySQL target catalog using the connection's databaseTerm setting.
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
    protected String jdbcCatalog(DatabaseMetaData metadata, TableId tableId) throws SQLException {
        return metadata.supportsSchemasInTableDefinitions() ? null : tableId.getSchemaName();
    }

    @Override
    protected String jdbcSchema(DatabaseMetaData metadata, TableId tableId) throws SQLException {
        return metadata.supportsSchemasInTableDefinitions() ? tableId.getSchemaName() : null;
    }

    @Override
    protected DataType fromJdbcColumn(
            int jdbcType, String nativeType, int precision, int scale) {
        String sqlType = nativeType == null ? "" : nativeType.toUpperCase(Locale.ROOT);
        boolean unsigned = sqlType.contains("UNSIGNED");
        int length = Math.max(1, precision);
        if (sqlType.contains("TEXT") || sqlType.equals("JSON")
                || sqlType.startsWith("ENUM") || sqlType.startsWith("SET")) {
            return DataTypes.STRING();
        }
        if (sqlType.startsWith("DATETIME")) {
            return DataTypes.TIMESTAMP(temporalPrecision(precision, scale, 19));
        }
        if (sqlType.startsWith("TIMESTAMP")) {
            return DataTypes.TIMESTAMP_LTZ(temporalPrecision(precision, scale, 19));
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
                if (precision < DecimalType.MIN_PRECISION
                        || precision > DecimalType.MAX_PRECISION) {
                    throw new JdbcCatalogException(
                            String.format(
                                    "MySQL DECIMAL(%d,%d) cannot be represented without loss by "
                                            + "Flink CDC DECIMAL: supported precision is %d to %d. "
                                            + "Refusing to truncate the JDBC column precision.",
                                    precision,
                                    scale,
                                    DecimalType.MIN_PRECISION,
                                    DecimalType.MAX_PRECISION));
                }
                if (scale < DecimalType.MIN_SCALE || scale > precision) {
                    throw new JdbcCatalogException(
                            String.format(
                                    "Invalid MySQL DECIMAL(%d,%d) metadata: scale must be "
                                            + "between %d and precision %d.",
                                    precision,
                                    scale,
                                    DecimalType.MIN_SCALE,
                                    precision));
                }
                return DataTypes.DECIMAL(precision, scale);
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
                return DataTypes.TIME(temporalPrecision(precision, scale, 8));
            case Types.TIMESTAMP:
                return DataTypes.TIMESTAMP(temporalPrecision(precision, scale, 19));
            default:
                throw new IllegalArgumentException(
                        "Unsupported MySQL JDBC metadata type: "
                                + nativeType
                                + " ("
                                + jdbcType
                                + ")");
        }
    }

    private static int temporalPrecision(int columnSize, int scale, int wholeSecondsWidth) {
        // Connector/J may leave DECIMAL_DIGITS null. COLUMN_SIZE includes the decimal point
        // and fractional digits after the 8-character time or 19-character date-time.
        return scale > 0 ? scale : Math.max(0, Math.min(6, columnSize - wholeSecondsWidth - 1));
    }
}
