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
import org.apache.flink.cdc.common.schema.Schema;

import java.io.Serializable;

/**
 * Applies database metadata changes, separate from the data writer.
 *
 * <p>All connections used by a catalog operation are returned after that operation.
 */
public interface JdbcCatalog extends Serializable {
    boolean tableExists(TableId tableId);

    /** Reads the current physical target-table schema through JDBC metadata. */
    Schema getTableSchema(TableId tableId);

    void createTable(CreateTableEvent event, boolean ignoreIfExists);

    void addColumn(AddColumnEvent event);

    void dropColumn(DropColumnEvent event);

    void renameColumn(RenameColumnEvent event);

    void alterColumnType(AlterColumnTypeEvent event);

    void truncateTable(TruncateTableEvent event);

    void dropTable(DropTableEvent event, boolean ignoreIfNotExists);
}
