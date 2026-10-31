package io.yak.ops.connector.base.source;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.source.MetadataAccessor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable metadata limited to the tables authorized by the caller. */
public final class TableMetadataAccessor implements MetadataAccessor {
    private final Map<TableId, Schema> schemas;

    public TableMetadataAccessor(Map<TableId, Schema> schemas) {
        this.schemas = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(schemas, "schemas")));
    }

    @Override
    public List<String> listNamespaces() {
        return schemas.keySet().stream()
                .map(TableId::catalog)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<String> listSchemas(String namespace) {
        return schemas.keySet().stream()
                .filter(table -> namespace == null || namespace.equals(table.catalog()))
                .map(TableId::schema)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<TableId> listTables(String namespace, String schemaName) {
        return schemas.keySet().stream()
                .filter(table -> namespace == null || namespace.equals(table.catalog()))
                .filter(table -> schemaName == null || schemaName.equals(table.schema()))
                .toList();
    }

    @Override
    public Schema getTableSchema(TableId tableId) {
        Schema schema = schemas.get(tableId);
        if (schema == null) {
            throw new IllegalArgumentException("Table is outside the authorized source metadata scope");
        }
        return schema;
    }
}
