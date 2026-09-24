package io.yak.ops.business.datasource.domain.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable preview/query result inside the Catalog subdomain. */
public record CatalogQueryResult(List<QueryColumn> columns, List<Map<String, Object>> rows, long total) {

    public CatalogQueryResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        if (rows == null || rows.isEmpty()) {
            rows = List.of();
        } else {
            List<Map<String, Object>> copied = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                copied.add(row == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            }
            rows = Collections.unmodifiableList(copied);
        }
        if (total < 0L) throw new IllegalArgumentException("Catalog 查询总数不能为负数");
    }

    public record QueryColumn(String title, String dataIndex, String key, boolean ellipsis) {}
}
