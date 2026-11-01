package io.yak.ops.connector.jdbc.database.catalog;

import java.util.List;

/** An immutable page of metadata results and its continuation offset. */
public final class JdbcTablePage {
    private final List<JdbcTableInfo> items;
    private final Integer nextOffset;

    public JdbcTablePage(List<JdbcTableInfo> items, Integer nextOffset) {
        this.items = List.copyOf(items);
        this.nextOffset = nextOffset;
    }

    public List<JdbcTableInfo> items() {
        return items;
    }

    public Integer nextOffset() {
        return nextOffset;
    }
}
