package io.yak.ops.api.vo.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Paginated child nodes for lazy-loaded catalog tree branches. */
public class CatalogChildrenPageVO {
    private final List<CatalogTreeNodeVO> items;
    private final long total;
    private final int pageNo;
    private final int pageSize;
    private final boolean hasMore;

    public CatalogChildrenPageVO(List<CatalogTreeNodeVO> items, long total, int pageNo, int pageSize) {
        this.items = items == null ? Collections.<CatalogTreeNodeVO>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
        this.total = Math.max(0, total);
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.hasMore = (long) pageNo * pageSize < this.total;
    }

    public List<CatalogTreeNodeVO> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public boolean isHasMore() { return hasMore; }
}
