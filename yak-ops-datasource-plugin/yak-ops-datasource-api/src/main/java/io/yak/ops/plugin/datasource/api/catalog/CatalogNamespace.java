package io.yak.ops.plugin.datasource.api.catalog;

/** One namespace element such as a catalog, database, or schema. */
public class CatalogNamespace {
    private final String name;
    private final NamespaceLevel level;
    private final CatalogPath path;
    private final boolean hasChildren;

    public CatalogNamespace(String name, NamespaceLevel level, CatalogPath path, boolean hasChildren) {
        this.name = name;
        this.level = level;
        this.path = path;
        this.hasChildren = hasChildren;
    }

    public String getName() { return name; }
    public NamespaceLevel getLevel() { return level; }
    public CatalogPath getPath() { return path; }
    public boolean isHasChildren() { return hasChildren; }
}
