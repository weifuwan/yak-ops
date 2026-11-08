package io.yak.ops.plugin.datasource.api.catalog;

import java.util.Collections;
import java.util.List;

public class DataSourceCatalogTable {

    private final String name;
    private final String type;
    private final String remarks;
    private final List<String> primaryKeys;

    public DataSourceCatalogTable(String name, String type, String remarks, List<String> primaryKeys) {
        this.name = name;
        this.type = type;
        this.remarks = remarks;
        this.primaryKeys = primaryKeys == null ? Collections.emptyList() : List.copyOf(primaryKeys);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getRemarks() {
        return remarks;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }
}
