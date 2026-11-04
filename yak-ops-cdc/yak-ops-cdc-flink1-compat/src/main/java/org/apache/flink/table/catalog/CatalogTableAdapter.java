package org.apache.flink.table.catalog;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.api.Schema;

import java.util.List;
import java.util.Map;

/**
 * Compatibility adapter for {@link CatalogTable} in Flink 1.20.
 *
 * <p>This adapter provides a factory method to create CatalogTable instances compatible with Flink
 * 1.x.
 */
@Internal
public class CatalogTableAdapter {

    /**
     * Creates a CatalogTable using the Flink 1.x API.
     *
     * @param schema the table schema
     * @param comment the table comment
     * @param partitionKeys the partition keys
     * @param options the table options
     * @return a new CatalogTable instance
     */
    public static CatalogTable of(
            Schema schema,
            String comment,
            List<String> partitionKeys,
            Map<String, String> options) {
        return CatalogTable.of(schema, comment, partitionKeys, options);
    }
}
