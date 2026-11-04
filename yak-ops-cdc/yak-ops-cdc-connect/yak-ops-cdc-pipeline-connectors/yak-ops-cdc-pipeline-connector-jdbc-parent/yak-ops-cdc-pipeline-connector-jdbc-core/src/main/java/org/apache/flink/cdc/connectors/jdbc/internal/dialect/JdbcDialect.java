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

package org.apache.flink.cdc.connectors.jdbc.internal.dialect;

import org.apache.flink.cdc.common.event.AddColumnEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.JdbcRowConverter;

import java.io.Serializable;
import java.util.List;

/**
 * Stateless SQL dialect for a JDBC destination.
 *
 * <p>Implementations create SQL but never open a connection or execute a statement.
 */
public interface JdbcDialect extends Serializable {
    String dialectName();

    /** Creates the destination-specific JDBC row converter. */
    JdbcRowConverter createRowConverter();

    String quoteIdentifier(String identifier);

    String getUpsertStatement(TableId tableId, Schema schema);

    String getDeleteStatement(TableId tableId, Schema schema);

    String buildCreateTableSql(TableId tableId, Schema schema, boolean ignoreIfExists);

    String buildAlterAddColumnsSql(
            TableId tableId, List<AddColumnEvent.ColumnWithPosition> columns);

    String buildRenameColumnSql(TableId tableId, String oldName, String newName);

    String buildDropColumnSql(TableId tableId, String columnName);

    String buildAlterColumnTypeSql(TableId tableId, String name, DataType newType);

    String buildTruncateTableSql(TableId tableId);

    String buildDropTableSql(TableId tableId, boolean ignoreIfNotExists);
}
