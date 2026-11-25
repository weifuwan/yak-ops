package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DorisColumnMetadata;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reads actual Doris table metadata through the datasource plugin catalog. */
@Component
public class DorisSchemaInspector {

    public boolean tableExists(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        CatalogObject object = catalog.getObject(
                connection,
                new CatalogPath(null, database, null, tableName, CatalogObjectType.TABLE));
        return object != null && object.getType() == CatalogObjectType.TABLE;
    }

    public Map<String, DorisColumnMetadata> readColumns(
            Connection connection, DataSourceCatalog catalog, String database, String tableName) {
        List<DataSourceCatalogColumn> columns = catalog.listColumns(
                connection, new CatalogPath(null, database, null, tableName, CatalogObjectType.TABLE));
        Map<String, DorisColumnMetadata> result = new LinkedHashMap<>();
        if (columns == null) {
            return result;
        }
        for (DataSourceCatalogColumn column : columns) {
            if (column == null || column.getName() == null || column.getName().trim().isEmpty()) {
                continue;
            }
            result.put(column.getName().toLowerCase(Locale.ROOT), new DorisColumnMetadata(
                    column.getName(),
                    column.getTypeName(),
                    column.getSize(),
                    column.getScale(),
                    column.getNullable(),
                    column.isPrimaryKey()));
        }
        return result;
    }

    public boolean matches(
            Connection connection,
            DataSourceCatalog catalog,
            DdlTableDefinition table,
            DorisDdlGenerator generator) {
        if (!tableExists(connection, catalog, table.getDatabase(), table.getTableName())) {
            return false;
        }
        Map<String, DorisColumnMetadata> actualColumns =
                readColumns(connection, catalog, table.getDatabase(), table.getTableName());
        if (table.getColumns().isEmpty() || table.getColumns().size() != actualColumns.size()) {
            return false;
        }
        for (DdlColumnDefinition expected : table.getColumns()) {
            DorisColumnMetadata actual = actualColumns.get(expected.getName().toLowerCase(Locale.ROOT));
            if (actual == null || !generator.matches(actual, expected)) {
                return false;
            }
        }
        return true;
    }
}
