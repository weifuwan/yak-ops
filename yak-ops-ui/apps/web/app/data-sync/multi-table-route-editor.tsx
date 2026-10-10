import {
  Alert,
  Button,
  Checkbox,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
} from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import type { DataSourceCatalogTable } from "@/service/datasource";
import type { DataSyncTableRoute } from "@/service/data-sync";

interface CatalogOptions {
  schemas: string[];
  tables: DataSourceCatalogTable[];
  loading: boolean;
  refresh: () => void;
}

interface MultiTableRouteEditorProps {
  routes: DataSyncTableRoute[];
  onChange: (routes: DataSyncTableRoute[]) => void;
  onReadyChange: (ready: boolean) => void;
  sourceDataSourceId: string;
  targetDataSourceId: string;
  sourceDatabase: string;
  sourceSchema: string;
  targetDatabase: string;
  targetSchema: string;
  sourceBoundSchema?: string;
  targetBoundSchema?: string;
  sourceCatalog: CatalogOptions;
  targetCatalog: CatalogOptions;
  onSourceSchemaChange: (schema: string) => void;
  onTargetSchemaChange: (schema: string) => void;
}

function pathKey(database: string | undefined, schema: string | undefined, table: string) {
  return [database || "", schema || "", table].map((value) => value.toLocaleLowerCase()).join("|");
}

function routeSourceKey(route: DataSyncTableRoute) {
  return pathKey(route.sourceDatabase, route.sourceSchema, route.sourceTable);
}

function catalogKey(table: DataSourceCatalogTable, database: string, schema: string) {
  return pathKey(table.database || database, table.schema || schema, table.name);
}

export function MultiTableRouteEditor({
  routes,
  onChange,
  onReadyChange,
  sourceDataSourceId,
  targetDataSourceId,
  sourceDatabase,
  sourceSchema,
  targetDatabase,
  targetSchema,
  sourceBoundSchema,
  targetBoundSchema,
  sourceCatalog,
  targetCatalog,
  onSourceSchemaChange,
  onTargetSchemaChange,
}: MultiTableRouteEditorProps) {
  const [keyword, setKeyword] = useState("");
  const sourceKeys = useMemo(() => new Set(routes.map(routeSourceKey)), [routes]);
  const sourceTables = useMemo(() => {
    const needle = keyword.trim().toLocaleLowerCase();
    return sourceCatalog.tables.filter(
      (table) =>
        !needle ||
        [table.name, table.schema, table.database, table.remarks]
          .filter(Boolean)
          .join(" ")
          .toLocaleLowerCase()
          .includes(needle),
    );
  }, [keyword, sourceCatalog.tables]);

  const targetOptions = useMemo(
    () =>
      targetCatalog.tables.map((table) => ({
        value: catalogKey(table, targetDatabase, targetSchema),
        label: [table.schema, table.name].filter(Boolean).join(".") || table.name,
        searchText: [table.database, table.schema, table.name].filter(Boolean).join(" "),
      })),
    [targetCatalog.tables, targetDatabase, targetSchema],
  );

  const targetKeys = useMemo(
    () => new Set(targetOptions.map((item) => item.value)),
    [targetOptions],
  );
  const duplicateTargets = useMemo(() => {
    const seen = new Set<string>();
    const duplicates = new Set<string>();
    for (const route of routes) {
      const key = pathKey(route.targetDatabase, route.targetSchema, route.targetTable.trim());
      if (seen.has(key)) duplicates.add(key);
      seen.add(key);
    }
    return duplicates;
  }, [routes]);

  const allReady =
    Boolean(sourceDataSourceId && targetDataSourceId) &&
    routes.length > 0 &&
    duplicateTargets.size === 0 &&
    routes.every(
      (route) =>
        route.sourceTable.trim() &&
        route.targetTable.trim() &&
        targetKeys.has(pathKey(route.targetDatabase, route.targetSchema, route.targetTable)),
    );

  useEffect(() => {
    onReadyChange(Boolean(allReady));
  }, [allReady, onReadyChange]);

  const toggleSource = (table: DataSourceCatalogTable, checked: boolean) => {
    const database = table.database || sourceDatabase;
    const schema = table.schema || sourceSchema;
    const key = pathKey(database, schema, table.name);
    if (!checked) {
      onChange(routes.filter((route) => routeSourceKey(route) !== key));
      return;
    }
    if (sourceKeys.has(key) || routes.length >= 50) return;
    onChange([
      ...routes,
      {
        sourceDatabase: database || undefined,
        sourceSchema: schema || undefined,
        sourceTable: table.name,
        targetDatabase: targetDatabase || undefined,
        targetSchema: targetSchema || undefined,
        targetTable: table.name,
      },
    ]);
  };

  const updateRoute = (key: string, patch: Partial<DataSyncTableRoute>) =>
    onChange(
      routes.map((route) => (routeSourceKey(route) === key ? { ...route, ...patch } : route)),
    );

  return (
    <>
      <CollapseSection
        id="source"
        title="数据来源"
        extra={<span className="text-xs text-[#667085]">{routes.length} 张表</span>}
      >
        <div className="rounded-lg border border-[#e6e8eb] bg-white p-4">
          {!sourceBoundSchema && sourceCatalog.schemas.length > 0 ? (
            <Field className="mb-3 grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
              <FieldLabel>Schema</FieldLabel>
              <DataSyncSearchableSelect
                value={sourceSchema || null}
                options={sourceCatalog.schemas.map((name) => ({ value: name, label: name }))}
                placeholder="选择 Schema"
                onRefresh={sourceCatalog.refresh}
                refreshing={sourceCatalog.loading}
                onValueChange={onSourceSchemaChange}
              />
            </Field>
          ) : null}
          <div className="mb-3 flex items-center gap-2">
            <Input
              size="small"
              value={keyword}
              placeholder="搜索表"
              onChange={(event) => setKeyword(event.target.value)}
            />
            <Button size="small" aria-label="刷新表" onClick={sourceCatalog.refresh}>
              <RefreshCw size={15} />
            </Button>
          </div>
          <div className="max-h-[260px] overflow-y-auto rounded-md border border-[#e6e8eb]">
            {sourceTables.map((table) => {
              const key = catalogKey(table, sourceDatabase, sourceSchema);
              const checked = sourceKeys.has(key);
              return (
                <label
                  key={key}
                  className="flex cursor-pointer items-center gap-3 border-b border-[#f0f1f3] px-3 py-2 last:border-b-0 hover:bg-[#f8f9fb]"
                >
                  <Checkbox
                    checked={checked}
                    disabled={!checked && routes.length >= 50}
                    onCheckedChange={(value) => toggleSource(table, Boolean(value))}
                  />
                  <span className="min-w-0 truncate text-[13px] text-[#344054]">
                    {[table.schema, table.name].filter(Boolean).join(".") || table.name}
                  </span>
                </label>
              );
            })}
            {!sourceCatalog.loading && sourceTables.length === 0 ? (
              <div className="px-3 py-6 text-center text-xs text-[#98a2b3]">暂无数据表</div>
            ) : null}
          </div>
        </div>
      </CollapseSection>

      <CollapseSection id="target" title="数据去向">
        <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
          {!targetBoundSchema && targetCatalog.schemas.length > 0 ? (
            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
              <FieldLabel>目标 Schema</FieldLabel>
              <DataSyncSearchableSelect
                value={targetSchema || null}
                options={targetCatalog.schemas.map((name) => ({ value: name, label: name }))}
                placeholder="目标 Schema"
                onRefresh={targetCatalog.refresh}
                refreshing={targetCatalog.loading}
                onValueChange={onTargetSchemaChange}
              />
            </Field>
          ) : null}
          {routes.length > 0 ? (
            <div className="space-y-2">
              {routes.map((route) => {
                const key = routeSourceKey(route);
                const targetKey = pathKey(
                  route.targetDatabase,
                  route.targetSchema,
                  route.targetTable,
                );
                return (
                  <div
                    key={key}
                    className="grid grid-cols-[minmax(100px,1fr)_minmax(170px,1.4fr)] items-center gap-3 rounded-md border border-[#e6e8eb] px-3 py-2 max-lg:grid-cols-2"
                  >
                    <span className="truncate text-[13px] text-[#344054]">
                      {[route.sourceSchema, route.sourceTable].filter(Boolean).join(".")}
                    </span>
                    <DataSyncSearchableSelect
                      value={targetKeys.has(targetKey) ? targetKey : null}
                      options={targetOptions}
                      disabled={!targetDataSourceId}
                      placeholder="选择已存在的目标表"
                      onRefresh={targetCatalog.refresh}
                      refreshing={targetCatalog.loading}
                      onValueChange={(value) => {
                        const table = targetCatalog.tables.find(
                          (item) => catalogKey(item, targetDatabase, targetSchema) === value,
                        );
                        if (table) {
                          updateRoute(key, {
                            targetDatabase: table.database || targetDatabase,
                            targetSchema: table.schema || targetSchema,
                            targetTable: table.name,
                          });
                        }
                      }}
                    />
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="py-5 text-center text-xs text-[#98a2b3]">请先选择来源表</div>
          )}
          {duplicateTargets.size > 0 ? <Alert>目标表不能重复。</Alert> : null}
        </div>
      </CollapseSection>
    </>
  );
}

export default MultiTableRouteEditor;
