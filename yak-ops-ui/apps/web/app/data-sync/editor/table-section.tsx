import { Alert, Button, Field, FieldLabel, Input } from "@yak-ops/yak-ui";
import { useMemo, type ReactNode } from "react";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import type { DataSourceCatalogTable } from "@/service/datasource";
import type { CatalogOptions } from "./types";

const tableKey = (table: DataSourceCatalogTable) =>
  [table.database || "", table.schema || "", table.name].join("|");

const tableLabel = (table: DataSourceCatalogTable) =>
  [table.schema, table.name].filter(Boolean).join(".") || table.name;

export const selectedTableKey = (
  tables: DataSourceCatalogTable[],
  database: string,
  schema: string,
  tableName: string,
) =>
  tables.find(
    (table) =>
      table.name === tableName &&
      (table.database || "") === database &&
      (table.schema || "") === schema,
  )
    ? [database, schema, tableName].join("|")
    : null;

interface TableSectionProps {
  prepareSchema?: boolean;
  onTableNameChange?: (name: string) => void;
  children?: ReactNode;
  dataSourceId: string;
  boundSchema?: string;
  database: string;
  schema: string;
  table: string;
  catalog: CatalogOptions;
  onSchemaChange: (value: string) => void;
  onTableChange: (table: DataSourceCatalogTable) => void;
}

export function TableSection({
  prepareSchema = false,
  onTableNameChange,
  children,
  dataSourceId,
  boundSchema,
  database,
  schema,
  table,
  catalog,
  onSchemaChange,
  onTableChange,
}: TableSectionProps) {
  const tableValue = selectedTableKey(catalog.tables, database, schema, table);
  const schemaOptions = useMemo(
    () => catalog.schemas.map((item) => ({ value: item, label: item })),
    [catalog.schemas],
  );
  const tableOptions = useMemo(
    () =>
      catalog.tables.map((item) => ({
        value: tableKey(item),
        label: tableLabel(item),
        searchText: [item.database, item.schema, item.name, item.type, item.remarks]
          .filter(Boolean)
          .join(" "),
      })),
    [catalog.tables],
  );
  const requiresSchema = !boundSchema && catalog.schemas.length > 0;
  const tableDisabled = !dataSourceId || (requiresSchema && !schema);

  return (
    <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
      <div className="space-y-3">
        {requiresSchema ? (
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>Schema</FieldLabel>
            <DataSyncSearchableSelect
              value={schema || null}
              options={schemaOptions}
              placeholder="请选择 Schema"
              searchPlaceholder="搜索 Schema"
              emptyText="暂无 Schema"
              refreshing={catalog.loading}
              onRefresh={catalog.refresh}
              onValueChange={onSchemaChange}
            />
          </Field>
        ) : null}
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>表</FieldLabel>
          <div className="w-1/2 min-w-0 max-lg:w-auto max-lg:flex-1">
            {prepareSchema ? (
              <Input
                size="small"
                variant="outlined"
                value={table}
                maxLength={128}
                disabled={tableDisabled}
                placeholder="去向表名称"
                aria-label="去向表名称"
                onChange={(event) => onTableNameChange?.(event.target.value)}
              />
            ) : (
              <DataSyncSearchableSelect
                value={tableValue}
                options={tableOptions}
                disabled={tableDisabled}
                placeholder={!dataSourceId ? "请先选择数据源" : "请选择表"}
                searchPlaceholder="搜索表"
                emptyText="暂无表"
                refreshing={catalog.loading}
                onRefresh={catalog.refresh}
                onValueChange={(value) => {
                  const selected = catalog.tables.find((item) => tableKey(item) === value);
                  if (selected) onTableChange(selected);
                }}
              />
            )}
          </div>
        </Field>
        {catalog.error ? (
          <Alert>
            {catalog.error}
            <Button size="small" onClick={catalog.refresh}>
              重试
            </Button>
          </Alert>
        ) : null}
        {children}
      </div>
    </div>
  );
}
