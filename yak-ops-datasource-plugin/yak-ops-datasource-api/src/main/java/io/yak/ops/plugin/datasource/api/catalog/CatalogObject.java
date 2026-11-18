package io.yak.ops.plugin.datasource.api.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A real database object discovered by a datasource plugin. */
public class CatalogObject {
    private final String name;
    private final CatalogObjectType type;
    private final String comment;
    private final CatalogPath path;
    private final boolean hasChildren;
    private final List<String> primaryKeys;

    public CatalogObject(String name, CatalogObjectType type, String comment, CatalogPath path,
                         boolean hasChildren, List<String> primaryKeys) {
        this.name = name;
        this.type = type;
        this.comment = comment;
        this.path = path;
        this.hasChildren = hasChildren;
        this.primaryKeys = primaryKeys == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(primaryKeys));
    }

    public String getName() { return name; }
    public CatalogObjectType getType() { return type; }
    public String getComment() { return comment; }
    public CatalogPath getPath() { return path; }
    public boolean isHasChildren() { return hasChildren; }
    public List<String> getPrimaryKeys() { return primaryKeys; }
}
