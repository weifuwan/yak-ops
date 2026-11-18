package io.yak.ops.plugin.datasource.api.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Index metadata for a table-like object. */
public class CatalogIndex {
    private final String name;
    private final List<String> columns;
    private final boolean unique;
    private final boolean primary;

    public CatalogIndex(String name, List<String> columns, boolean unique, boolean primary) {
        this.name = name;
        this.columns = columns == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(columns));
        this.unique = unique;
        this.primary = primary;
    }

    public String getName() { return name; }
    public List<String> getColumns() { return columns; }
    public boolean isUnique() { return unique; }
    public boolean isPrimary() { return primary; }
}
