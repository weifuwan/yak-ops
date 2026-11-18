package io.yak.ops.api.vo.catalog;

import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized tree node returned by the catalog API. */
public class CatalogTreeNodeVO {
    private final String id;
    private final String name;
    private final CatalogNodeType nodeType;
    private final String objectType;
    private final boolean hasChildren;
    private final CatalogPath path;
    private final Map<String, Object> metadata;

    public CatalogTreeNodeVO(String id, String name, CatalogNodeType nodeType, String objectType,
                             boolean hasChildren, CatalogPath path, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.nodeType = nodeType;
        this.objectType = objectType;
        this.hasChildren = hasChildren;
        this.path = path;
        this.metadata = metadata == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public CatalogNodeType getNodeType() { return nodeType; }
    public String getObjectType() { return objectType; }
    public boolean isHasChildren() { return hasChildren; }
    public CatalogPath getPath() { return path; }
    public Map<String, Object> getMetadata() { return metadata; }
}
