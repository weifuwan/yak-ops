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

package org.apache.flink.cdc.connectors.jdbc.mysql.dialect;

import org.apache.flink.cdc.common.event.AddColumnEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcColumn;
import org.apache.flink.cdc.connectors.jdbc.mysql.type.MySqlTypeTransformer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for MySQL DDL column types and nullability. */
class MySqlStmtCreatorFactoryTest {

    private static final MySqlStmtCreatorFactory FACTORY = MySqlStmtCreatorFactory.INSTANCE;
    private static final TableId TABLE_ID =
            TableId.tableId("yak_e2e_target", "orders_flink_cdc");

    @Test
    void createTablePreservesOrdersSchema() {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT().notNull())
                        .physicalColumn("order_no", DataTypes.VARCHAR(64).notNull())
                        .physicalColumn("customer_name", DataTypes.VARCHAR(180).notNull())
                        .physicalColumn("amount", DataTypes.DECIMAL(14, 2).notNull())
                        .physicalColumn("quantity", DataTypes.INT().notNull())
                        .physicalColumn("order_status", DataTypes.VARCHAR(24).notNull())
                        .physicalColumn("order_date", DataTypes.DATE())
                        .physicalColumn("created_at", DataTypes.TIMESTAMP(3).notNull())
                        .physicalColumn("updated_at", DataTypes.TIMESTAMP(3))
                        .physicalColumn("tracking_code", DataTypes.VARCHAR(60))
                        .physicalColumn("risk_score", DataTypes.DECIMAL(10, 2))
                        .primaryKey("id")
                        .build();

        String ddl = FACTORY.buildCreateTableSql(TABLE_ID, schema, true);

        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS"), ddl);
        assertTrue(ddl.contains("`id` BIGINT NOT NULL"), ddl);
        assertTrue(ddl.contains("`order_no` VARCHAR(64) NOT NULL"), ddl);
        assertTrue(ddl.contains("`customer_name` VARCHAR(180) NOT NULL"), ddl);
        assertTrue(ddl.contains("`amount` DECIMAL(14,2) NOT NULL"), ddl);
        assertTrue(ddl.contains("`quantity` INT NOT NULL"), ddl);
        assertTrue(ddl.contains("`order_status` VARCHAR(24) NOT NULL"), ddl);
        assertTrue(ddl.contains("`order_date` DATE"), ddl);
        assertTrue(ddl.contains("`created_at` DATETIME(3) NOT NULL"), ddl);
        assertTrue(ddl.contains("`updated_at` DATETIME(3)"), ddl);
        assertTrue(ddl.contains("`tracking_code` VARCHAR(60)"), ddl);
        assertTrue(ddl.contains("`risk_score` DECIMAL(10,2)"), ddl);
        assertTrue(ddl.contains("PRIMARY KEY (`id`)"), ddl);
        assertFalse(ddl.contains("NOT NULL NOT NULL"), ddl);
        assertFalse(ddl.contains("`created_at` TIMESTAMP"), ddl);
        assertFalse(ddl.contains("`updated_at` TIMESTAMP"), ddl);
        assertFalse(ddl.contains("`order_date` DATE NOT NULL"), ddl);
        assertFalse(ddl.contains("`updated_at` DATETIME(3) NOT NULL"), ddl);
        assertFalse(ddl.contains("`tracking_code` VARCHAR(60) NOT NULL"), ddl);
        assertFalse(ddl.contains("`risk_score` DECIMAL(10,2) NOT NULL"), ddl);
    }

    @Test
    void mapsTimestampAndLocalZonedTimestampToDifferentMysqlTypes() {
        assertEquals(
                "`created_at` DATETIME(3) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn(
                                "created_at", DataTypes.TIMESTAMP(3).notNull())));
        assertEquals(
                "`updated_at` DATETIME(3)",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("updated_at", DataTypes.TIMESTAMP(3))));
        assertEquals(
                "`created_at` DATETIME NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn(
                                "created_at", DataTypes.TIMESTAMP(0).notNull())));
        assertEquals(
                "`server_time` TIMESTAMP(3) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn(
                                "server_time", DataTypes.TIMESTAMP_LTZ(3).notNull())));
        assertEquals(
                "`server_time` TIMESTAMP",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("server_time", DataTypes.TIMESTAMP_LTZ(0))));
    }

    @Test
    void keepsNullabilityForVarcharCharDecimalAndNumericColumns() {
        assertEquals(
                "`order_no` VARCHAR(64) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("order_no", DataTypes.VARCHAR(64).notNull())));
        assertEquals(
                "`order_no` VARCHAR(64)",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("order_no", DataTypes.VARCHAR(64))));
        assertEquals(
                "`code` CHAR(8) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("code", DataTypes.CHAR(8).notNull())));
        assertEquals(
                "`amount` DECIMAL(14,2) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("amount", DataTypes.DECIMAL(14, 2).notNull())));
        assertEquals(
                "`quantity` INT NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("quantity", DataTypes.INT().notNull())));
        assertEquals(
                "`weight` FLOAT NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("weight", DataTypes.FLOAT().notNull())));
        assertEquals(
                "`rate` DOUBLE NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("rate", DataTypes.DOUBLE().notNull())));
    }

    @Test
    void retainsDateTimeAndBinaryNullability() {
        assertEquals(
                "`order_date` DATE NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("order_date", DataTypes.DATE().notNull())));
        assertEquals(
                "`delivery_time` TIME(3) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("delivery_time", DataTypes.TIME(3).notNull())));
        assertEquals(
                "`delivery_time` TIME",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn("delivery_time", DataTypes.TIME(0))));
        assertEquals(
                "`binary_key` BINARY(8) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn(
                                "binary_key", DataTypes.BINARY(8).notNull())));
        assertEquals(
                "`binary_key` VARBINARY(8) NOT NULL",
                FACTORY.buildColumnStmt(
                        Column.physicalColumn(
                                "binary_key", DataTypes.VARBINARY(8).notNull())));
    }

    @Test
    void addColumnUsesSameDatetimeAndNullabilityRules() {
        Column createdAt =
                Column.physicalColumn("created_at", DataTypes.TIMESTAMP(3).notNull());
        Column trackingCode =
                Column.physicalColumn("tracking_code", DataTypes.VARCHAR(60));

        String ddl =
                FACTORY.buildAlterAddColumnsSql(
                        TABLE_ID,
                        List.of(
                                AddColumnEvent.after(createdAt, "id"),
                                AddColumnEvent.last(trackingCode)));

        assertTrue(ddl.contains("ADD COLUMN `created_at` DATETIME(3) NOT NULL AFTER `id`"), ddl);
        assertTrue(ddl.contains("ADD COLUMN `tracking_code` VARCHAR(60)"), ddl);
        assertFalse(ddl.contains("`tracking_code` VARCHAR(60) NOT NULL"), ddl);
        assertFalse(ddl.contains("NOT NULL NOT NULL"), ddl);
    }

    @Test
    void modifyColumnUsesSameDatetimeAndNullabilityRules() {
        String requiredDateTime =
                FACTORY.buildAlterColumnTypeSql(
                        TABLE_ID, "created_at", DataTypes.TIMESTAMP(3).notNull());
        String nullableDateTime =
                FACTORY.buildAlterColumnTypeSql(
                        TABLE_ID, "updated_at", DataTypes.TIMESTAMP(3));
        String requiredVarchar =
                FACTORY.buildAlterColumnTypeSql(
                        TABLE_ID, "order_no", DataTypes.VARCHAR(64).notNull());

        assertTrue(requiredDateTime.contains("MODIFY COLUMN `created_at` DATETIME(3) NOT NULL"),
                requiredDateTime);
        assertTrue(nullableDateTime.contains("MODIFY COLUMN `updated_at` DATETIME(3)"),
                nullableDateTime);
        assertFalse(nullableDateTime.contains("NOT NULL"), nullableDateTime);
        assertTrue(requiredVarchar.contains("MODIFY COLUMN `order_no` VARCHAR(64) NOT NULL"),
                requiredVarchar);
    }

    @Test
    void transformerSeparatesMysqlTypeNameFromNullability() {
        assertType(DataTypes.VARCHAR(64).notNull(), "VARCHAR(64)", false);
        assertType(DataTypes.VARCHAR(64), "VARCHAR(64)", true);
        assertType(DataTypes.DECIMAL(14, 2).notNull(), "DECIMAL(14,2)", false);
        assertType(DataTypes.INT().notNull(), "INT", false);
        assertType(DataTypes.CHAR(8).notNull(), "CHAR(8)", false);
        assertType(DataTypes.FLOAT().notNull(), "FLOAT", false);
        assertType(DataTypes.DOUBLE().notNull(), "DOUBLE", false);
        assertType(DataTypes.BINARY(8).notNull(), "BINARY(8)", false);
        assertType(DataTypes.DATE().notNull(), "DATE", false);
        assertType(DataTypes.TIME(3).notNull(), "TIME(3)", false);
        assertType(DataTypes.TIMESTAMP(3).notNull(), "DATETIME(3)", false);
        assertType(DataTypes.TIMESTAMP_LTZ(3).notNull(), "TIMESTAMP(3)", false);
    }

    private static void assertType(DataType logicalType, String expectedType, boolean nullable) {
        JdbcColumn.Builder builder = new JdbcColumn.Builder();
        logicalType.accept(new MySqlTypeTransformer(builder));
        JdbcColumn jdbcColumn = builder.build();
        assertEquals(expectedType, jdbcColumn.getColumnType());
        assertEquals(nullable, jdbcColumn.isNullable());
        assertFalse(jdbcColumn.getColumnType().contains("NOT NULL"));
    }
}
