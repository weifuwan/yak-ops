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
