import type { DataSourceCatalogTable } from "@/service/datasource";
import type { DataSyncTablePath } from "@/service/data-sync";

export const tableKey = (table: DataSyncTablePath) =>
  JSON.stringify([table.database || "", table.schema || "", table.table]);
export const tablePathLabel = (table: DataSyncTablePath) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");
export const catalogPath = (table: DataSourceCatalogTable): DataSyncTablePath => ({
  database: table.database,
  schema: table.schema,
  table: table.name,
});
export const selectedTableKey = (
  tables: DataSourceCatalogTable[],
  database: string,
  schema: string,
  name: string,
) => {
  const selected = tables.find(
    (table) =>
      table.name === name && (table.database || "") === database && (table.schema || "") === schema,
  );
  return selected ? tableKey(catalogPath(selected)) : null;
};
