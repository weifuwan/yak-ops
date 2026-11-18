package io.yak.ops.plugin.datasource.api.catalog;

/** Portable location of a database object. */
public class CatalogPath {
    private String catalog;
    private String database;
    private String schema;
    private String objectName;
    private CatalogObjectType objectType;

    public CatalogPath() { }

    public CatalogPath(String catalog, String database, String schema, String objectName,
                       CatalogObjectType objectType) {
        this.catalog = catalog;
        this.database = database;
        this.schema = schema;
        this.objectName = objectName;
        this.objectType = objectType;
    }

    public String getCatalog() { return catalog; }
    public void setCatalog(String catalog) { this.catalog = catalog; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }
    public CatalogObjectType getObjectType() { return objectType; }
    public void setObjectType(CatalogObjectType objectType) { this.objectType = objectType; }
}
