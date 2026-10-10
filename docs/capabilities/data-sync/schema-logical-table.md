# Data Sync Physical Schema Contract

Status: Current contract after retiring product LogicalTable, Mapping and automatic DDL.

## Ownership

Datasource owns Workspace-scoped connection settings, credentials, driver isolation, SSH and HTTP metadata representation. The JDBC Connector exclusively owns Catalog, vendor Dialect, native JDBC metadata and logical type conversion. Core `TableId` and `TableSchema` describe physical identity, column order, nullability, capacity and ordered primary keys.

```text
DataSourceService.queryTableSchema(...)
  → JdbcCatalogFactory.create(jdbcUrl, injected ConnectionProvider)
  → JdbcCatalog.getTable(TableId)
  → JdbcTableMetadata + JdbcDialect.createRowConverter
  → Core TableSchema
```

Product Data Sync does not persist a separate `LogicalTable` or `LogicalColumn`. No `JdbcSchemaMapper` or datasource-owned `GenericJdbcCatalog` is permitted.

## Supported Task Behavior

- The source and target physical tables must already exist.
- Columns bind by identifier name; Connector owns actual Source/Target RowData position alignment.
- Target field type and capacity must be compatible with each source column, based only on the Connector's normalized Core `Column` values.
- A target-only non-nullable column is rejected if a usable default is not established.
- REALTIME / OFFLINE UPSERT require complete matching primary key identity.
- Product column Mapping, automatic target-table creation, DDL preview and Schema Mapping preview are removed.

## Historical Metadata

The published V3 migration and historical execution snapshots may contain `mapping_config` and `auto_create_table`. These remain historical data, not executable feature flags. Do not modify the published migration or silently run a legacy task with retired policies.

Prior v1.2 logical-modeling designs and release evidence remain available under `docs/release/`. This page describes what the current code supports, not those earlier plans.
