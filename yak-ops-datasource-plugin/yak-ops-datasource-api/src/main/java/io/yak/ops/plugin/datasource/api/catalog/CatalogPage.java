package io.yak.ops.plugin.datasource.api.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Simple immutable page returned by plugin metadata queries. */
public class CatalogPage<T> {
    private final List<T> items;
    private final long total;

    public CatalogPage(List<T> items, long total) {
        this.items = items == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
        this.total = Math.max(0, total);
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
}
