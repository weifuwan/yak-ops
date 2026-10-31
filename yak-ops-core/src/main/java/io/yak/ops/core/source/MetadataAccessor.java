package io.yak.ops.core.source;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import java.util.List;

/** Read-only table discovery within the connection or authorization scope of a DataSource. */
public interface MetadataAccessor extends AutoCloseable {
    List<String> listNamespaces();

    List<String> listSchemas(String namespace);

    List<TableId> listTables(String namespace, String schemaName);

    Schema getTableSchema(TableId tableId);

    @Override
    default void close() throws Exception {}
}
