package io.yak.ops.spi.datasource.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 插件查询预览结果。 */
public final class DataSourceQueryResult {

    private final List<DataSourceQueryColumn> columns;
    private final List<Map<String, Object>> data;
    private final long total;

    public DataSourceQueryResult(List<DataSourceQueryColumn> columns, List<Map<String, Object>> data, long total) {
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        List<Map<String, Object>> copiedRows = new ArrayList<>();
        for (Map<String, Object> row : data) {
            copiedRows.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        }
        this.data = Collections.unmodifiableList(copiedRows);
        this.total = total;
    }

    public List<DataSourceQueryColumn> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public long getTotal() {
        return total;
    }
}
