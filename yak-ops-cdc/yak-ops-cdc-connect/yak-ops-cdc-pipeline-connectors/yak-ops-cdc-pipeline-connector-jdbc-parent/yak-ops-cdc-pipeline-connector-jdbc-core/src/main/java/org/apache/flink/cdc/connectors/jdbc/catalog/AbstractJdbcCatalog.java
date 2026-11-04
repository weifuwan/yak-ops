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

package org.apache.flink.cdc.connectors.jdbc.catalog;

import org.apache.flink.cdc.common.event.AddColumnEvent;
import org.apache.flink.cdc.common.event.AlterColumnTypeEvent;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.DropColumnEvent;
import org.apache.flink.cdc.common.event.DropTableEvent;
import org.apache.flink.cdc.common.event.RenameColumnEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.event.TruncateTableEvent;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.utils.StringUtils;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.apache.flink.cdc.common.utils.Preconditions.checkArgument;

/**
 * Executes schema DDL through a catalog-owned connection.
 *
 * <p>Dialect only renders SQL. The catalog never manages the data writer's connection or batch.
 */
public abstract class AbstractJdbcCatalog implements JdbcCatalog {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractJdbcCatalog.class);

    protected final JdbcDialect dialect;
    protected final JdbcConnectionFactory connectionFactory;

    protected AbstractJdbcCatalog(JdbcDialect dialect, JdbcSinkConfig config) {
        this.dialect = dialect;
        this.connectionFactory = new JdbcConnectionFactory(config);
    }

    @Override
    public boolean tableExists(TableId tableId) {
        checkTableId(tableId);
        try (Connection connection = connectionFactory.connect();
                ResultSet tables =
                        connection
                                .getMetaData()
                                .getTables(
                                        tableId.getSchemaName(),
                                        null,
                                        tableId.getTableName(),
                                        new String[] {"TABLE"})) {
            return tables.next();
        } catch (SQLException exception) {
            throw new JdbcCatalogException("Failed to inspect JDBC table " + tableId, exception);
        }
    }

    @Override
    public void createTable(CreateTableEvent event, boolean ignoreIfExists) {
        checkTableId(event.tableId());
        executeUpdate(
                event.tableId(),
                dialect.buildCreateTableSql(
                        event.tableId(), event.getSchema(), ignoreIfExists));
    }

    @Override
    public void addColumn(AddColumnEvent event) {
        checkTableId(event.tableId());
        checkArgument(!event.getAddedColumns().isEmpty(), "Added columns should not be empty.");
        executeUpdate(
                event.tableId(),
                dialect.buildAlterAddColumnsSql(event.tableId(), event.getAddedColumns()));
    }

    @Override
    public void dropColumn(DropColumnEvent event) {
        checkTableId(event.tableId());
        for (String column : event.getDroppedColumnNames()) {
            executeUpdate(event.tableId(), dialect.buildDropColumnSql(event.tableId(), column));
        }
    }

    @Override
    public void renameColumn(RenameColumnEvent event) {
        checkTableId(event.tableId());
        for (Map.Entry<String, String> renamed : event.getNameMapping().entrySet()) {
            executeUpdate(
                    event.tableId(),
                    dialect.buildRenameColumnSql(
                            event.tableId(), renamed.getKey(), renamed.getValue()));
        }
    }

    @Override
    public void alterColumnType(AlterColumnTypeEvent event) {
        checkTableId(event.tableId());
        for (Map.Entry<String, DataType> changed : event.getTypeMapping().entrySet()) {
            executeUpdate(
                    event.tableId(),
                    dialect.buildAlterColumnTypeSql(
                            event.tableId(), changed.getKey(), changed.getValue()));
        }
    }

    @Override
    public void truncateTable(TruncateTableEvent event) {
        checkTableId(event.tableId());
        executeUpdate(event.tableId(), dialect.buildTruncateTableSql(event.tableId()));
    }

    @Override
    public void dropTable(DropTableEvent event, boolean ignoreIfNotExists) {
        checkTableId(event.tableId());
        executeUpdate(
                event.tableId(),
                dialect.buildDropTableSql(event.tableId(), ignoreIfNotExists));
    }

    private static void checkTableId(TableId tableId) {
        checkArgument(
                !StringUtils.isNullOrWhitespaceOnly(tableId.getSchemaName()),
                "database name cannot be null or empty.");
        checkArgument(
                !StringUtils.isNullOrWhitespaceOnly(tableId.getTableName()),
                "table name cannot be null or empty.");
    }

    protected final void executeUpdate(TableId tableId, String sql) {
        try (Connection connection = connectionFactory.connect();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            LOG.info("Applied JDBC schema change to table {}. Raw SQL: {}", tableId, sql);
        } catch (SQLException exception) {
            throw new JdbcCatalogException(
                    "Failed to update JDBC table " + tableId + ". Raw SQL: " + sql, exception);
        }
    }
}
