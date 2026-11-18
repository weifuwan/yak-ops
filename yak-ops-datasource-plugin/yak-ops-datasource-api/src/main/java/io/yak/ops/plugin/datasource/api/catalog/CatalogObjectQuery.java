package io.yak.ops.plugin.datasource.api.catalog;

/** A bounded, namespace-scoped object listing request. */
public class CatalogObjectQuery {
    private final CatalogPath path;
    private final CatalogObjectType objectType;
    private final String keyword;
    private final int offset;
    private final int limit;

    public CatalogObjectQuery(CatalogPath path, CatalogObjectType objectType,
                              String keyword, int offset, int limit) {
        this.path = path;
        this.objectType = objectType;
        this.keyword = keyword;
        this.offset = Math.max(0, offset);
        this.limit = Math.max(1, Math.min(limit, 500));
    }

    public CatalogPath getPath() { return path; }
    public CatalogObjectType getObjectType() { return objectType; }
    public String getKeyword() { return keyword; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
}
