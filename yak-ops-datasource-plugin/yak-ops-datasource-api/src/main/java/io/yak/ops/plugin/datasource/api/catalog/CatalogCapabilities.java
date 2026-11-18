package io.yak.ops.plugin.datasource.api.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Capabilities declared by one concrete datasource plugin. */
public class CatalogCapabilities {
    private final List<NamespaceLevel> namespaceLevels;
    private final List<CatalogObjectType> objectTypes;
    private final List<CatalogFeature> features;

    public CatalogCapabilities(List<NamespaceLevel> namespaceLevels,
                               List<CatalogObjectType> objectTypes,
                               List<CatalogFeature> features) {
        this.namespaceLevels = immutableCopy(namespaceLevels);
        this.objectTypes = immutableCopy(objectTypes);
        this.features = immutableCopy(features);
    }

    public static CatalogCapabilities empty() {
        return new CatalogCapabilities(Collections.<NamespaceLevel>emptyList(),
                Collections.<CatalogObjectType>emptyList(), Collections.<CatalogFeature>emptyList());
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public List<NamespaceLevel> getNamespaceLevels() { return namespaceLevels; }
    public List<CatalogObjectType> getObjectTypes() { return objectTypes; }
    public List<CatalogFeature> getFeatures() { return features; }
    public boolean supportsObject(CatalogObjectType type) { return type != null && objectTypes.contains(type); }
    public boolean supportsFeature(CatalogFeature feature) { return feature != null && features.contains(feature); }
}
