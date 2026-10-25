package io.yak.ops.plugin.datasource.api.lookup;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Non-secret physical metadata and revision for one authorized table.
 *
 * <p>Column features describe defaults, generated values, identities, or unknown generation
 * metadata. They contain feature identifiers only, never default expressions or row values.
 */
public record DataSourceTable(
        String datasourceType,
        TableId table,
        TableSchema schema,
        long revision,
        String tableComment,
        Map<String, String> columnComments,
        Set<String> columnFeatures,
        Map<String, ColumnProperties> columnProperties,
        List<List<String>> uniqueKeys) {

    public DataSourceTable {
        Objects.requireNonNull(datasourceType, "datasourceType");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(schema, "schema");
        if (revision < 1) throw new IllegalArgumentException("Datasource revision is required");
        columnComments = Map.copyOf(columnComments);
        columnFeatures = Set.copyOf(columnFeatures);
        columnProperties = Map.copyOf(columnProperties);
        uniqueKeys = uniqueKeys.stream().map(List::copyOf).toList();
    }

    /** Retains current callers that do not request unique-index metadata. */
    public DataSourceTable(
            String datasourceType,
            TableId table,
            TableSchema schema,
            long revision,
            String tableComment,
            Map<String, String> columnComments,
            Set<String> columnFeatures,
            Map<String, ColumnProperties> columnProperties) {
        this(
                datasourceType,
                table,
                schema,
                revision,
                tableComment,
                columnComments,
                columnFeatures,
                columnProperties,
                List.of());
    }

    /** Preserves the original metadata constructor for existing callers. */
    public DataSourceTable(
            String datasourceType,
            TableId table,
            TableSchema schema,
            long revision,
            String tableComment,
            Map<String, String> columnComments,
            Set<String> columnFeatures) {
        this(datasourceType, table, schema, revision, tableComment, columnComments, columnFeatures, Map.of());
    }

    /** Creates metadata for a physical table containing plain columns without comments. */
    public DataSourceTable(String datasourceType, TableId table, TableSchema schema, long revision) {
        this(datasourceType, table, schema, revision, null, Map.of(), Set.of(), Map.of());
    }

    @Override
    public String toString() {
        return "DataSourceTable{type=" + datasourceType + ", table=" + table + ", revision=" + revision + '}';
    }
}
