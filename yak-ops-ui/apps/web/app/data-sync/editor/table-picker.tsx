import { Alert, Button, Input } from "@yak-ops/yak-ui";
import type { DataSourceCatalogTable } from "@/service/datasource";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentField } from "./content-layout";
import { catalogPath, selectedTableKey, tableKey, tablePathLabel } from "./table-catalog";
import type { CatalogOptions } from "./types";

export interface TablePickerProps {
  side: "source" | "target";
  dataSourceId: string;
  boundSchema?: string;
  database: string;
  schema: string;
  table: string;
  catalog: CatalogOptions;
  prepareSchema?: boolean;
  onSchemaChange: (schema: string) => void;
  onTableChange: (table: DataSourceCatalogTable) => void;
  onTableNameChange?: (name: string) => void;
}

export function TablePicker({
  side,
  dataSourceId,
  boundSchema,
  database,
  schema,
  table,
  catalog,
  prepareSchema,
  onSchemaChange,
  onTableChange,
  onTableNameChange,
}: TablePickerProps) {
  const requiresSchema = !boundSchema && catalog.schemas.length > 0;
  const disabled = !dataSourceId || (requiresSchema && !schema);
  const title = side === "source" ? "来源" : "去向";
  return (
    <>
      {requiresSchema ? (
        <ContentField label="Schema">
          <DataSyncSearchableSelect
            ariaLabel={`${title} Schema`}
            value={schema || null}
            options={catalog.schemas.map((value) => ({ value, label: value }))}
            placeholder="请选择 Schema"
            onValueChange={onSchemaChange}
          />
        </ContentField>
      ) : null}
      <ContentField label="表" required>
        {prepareSchema ? (
          <Input
            size="small"
            variant="outlined"
            value={table}
            maxLength={128}
            disabled={disabled}
            aria-label="去向表名称"
            placeholder="去向表名称"
            onChange={(event) => onTableNameChange?.(event.target.value)}
          />
        ) : (
          <DataSyncSearchableSelect
            ariaLabel={`${title}表`}
            value={selectedTableKey(catalog.tables, database, schema, table)}
            options={catalog.tables.map((item) => ({
              value: tableKey(catalogPath(item)),
              label: tablePathLabel(catalogPath(item)),
              searchText: item.remarks,
            }))}
            disabled={disabled}
            placeholder={!dataSourceId ? "请先选择数据源" : "请选择表"}
            searchPlaceholder="搜索表"
            emptyText="暂无表"
            refreshing={catalog.loading}
            onRefresh={catalog.refresh}
            onValueChange={(value) => {
              const selected = catalog.tables.find((item) => tableKey(catalogPath(item)) === value);
              if (selected) onTableChange(selected);
            }}
          />
        )}
      </ContentField>
      {catalog.error ? (
        <Alert>
          {catalog.error}
          <Button size="small" onClick={catalog.refresh}>
            重试
          </Button>
        </Alert>
      ) : null}
    </>
  );
}
