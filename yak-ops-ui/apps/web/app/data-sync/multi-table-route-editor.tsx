import {
  Alert,
  Button,
  Checkbox,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
  Switch,
} from "@yak-ops/yak-ui";
import { Check, CircleAlert, CircleDashed, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { SchemaMappingEditor } from "@/app/data-sync/schema-mapping-editor";
import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import {
  listDataSourceColumns,
  type DataSourceCatalogColumn,
  type DataSourceCatalogTable,
} from "@/service/datasource";
import {
  previewDataSyncMapping,
  type DataSyncMappingPreview,
  type DataSyncMappingPreviewPayload,
  type DataSyncTableRoute,
} from "@/service/data-sync";

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

interface PreviewEntry {
  fingerprint: string;
  preview?: DataSyncMappingPreview;
  failed?: boolean;
}

interface PreviewRequest {
  key: string;
  fingerprint: string;
  payload: DataSyncMappingPreviewPayload;
}

function pathKey(database: string | undefined, schema: string | undefined, name: string) {
  return [database || "", schema || "", name].map((value) => value.toLocaleLowerCase()).join("|");
}

function sourceKey(route: DataSyncTableRoute) {
  return pathKey(route.sourceDatabase, route.sourceSchema, route.sourceTable);
}

function catalogKey(table: DataSourceCatalogTable, database: string, schema: string) {
  return pathKey(table.database || database, table.schema || schema, table.name);
}

function tableLabel(route: DataSyncTableRoute) {
  return [route.sourceSchema, route.sourceTable].filter(Boolean).join(".") || route.sourceTable;
}

function useColumns(
  dataSourceId: string,
  database: string | undefined,
  schema: string | undefined,
  table: string | undefined,
  enabled: boolean,
) {
  const [columns, setColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!dataSourceId || !table || !enabled) {
      setColumns([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    void listDataSourceColumns(dataSourceId, {
      database: database || undefined,
      schema: schema || undefined,
      table,
    })
      .then((result) => {
        if (active) setColumns(result || []);
      })
      .catch(() => {
        if (active) setColumns([]);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataSourceId, database, schema, table, enabled]);

  return { columns, loading };
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
  const [sourceKeyword, setSourceKeyword] = useState("");
  const [selectedRoute, setSelectedRoute] = useState("");
  const [previews, setPreviews] = useState<Record<string, PreviewEntry>>({});

  const routeByKey = useMemo(
    () => new Map(routes.map((route) => [sourceKey(route), route])),
    [routes],
  );
  const sourceTables = useMemo(() => {
    const keyword = sourceKeyword.trim().toLocaleLowerCase();
    return sourceCatalog.tables.filter(
      (table) =>
        !keyword ||
        [table.name, table.schema, table.database, table.remarks]
          .filter(Boolean)
          .join(" ")
          .toLocaleLowerCase()
          .includes(keyword),
    );
  }, [sourceCatalog.tables, sourceKeyword]);

  const targetOptions = useMemo(
    () =>
      targetCatalog.tables.map((table) => ({
        value: catalogKey(table, targetDatabase, targetSchema),
        label: [table.schema, table.name].filter(Boolean).join(".") || table.name,
        searchText: [table.database, table.schema, table.name].filter(Boolean).join(" "),
      })),
    [targetCatalog.tables, targetDatabase, targetSchema],
  );

  const previewRequests = useMemo<PreviewRequest[]>(
    () =>
      routes.flatMap((route) => {
        if (
          !sourceDataSourceId ||
          !targetDataSourceId ||
          !route.sourceTable ||
          !route.targetTable.trim()
        ) {
          return [];
        }
        const payload: DataSyncMappingPreviewPayload = {
          sourceDataSourceId,
          sourceDatabase: route.sourceDatabase || undefined,
          sourceSchema: route.sourceSchema || undefined,
          sourceTable: route.sourceTable,
          targetDataSourceId,
          targetDatabase: route.targetDatabase || undefined,
          targetSchema: route.targetSchema || undefined,
          targetTable: route.targetTable.trim(),
          autoCreateTable: Boolean(route.autoCreateTable),
          mapping: route.mapping,
        };
        return [{ key: sourceKey(route), payload, fingerprint: JSON.stringify(payload) }];
      }),
    [routes, sourceDataSourceId, targetDataSourceId],
  );

  const requestByKey = useMemo(
    () => new Map(previewRequests.map((request) => [request.key, request])),
    [previewRequests],
  );

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      // 每批最多 3 个 Catalog 校验请求；用户快速修改时忽略过期结果。
      let cursor = 0;
      const worker = async () => {
        while (active && cursor < previewRequests.length) {
          const request = previewRequests[cursor++];
          try {
            const preview = await previewDataSyncMapping(request.payload);
            if (active) {
              setPreviews((current) => ({
                ...current,
                [request.key]: { fingerprint: request.fingerprint, preview },
              }));
            }
          } catch {
            if (active) {
              setPreviews((current) => ({
                ...current,
                [request.key]: { fingerprint: request.fingerprint, failed: true },
              }));
            }
          }
        }
      };
      void Promise.all(Array.from({ length: Math.min(3, previewRequests.length) }, () => worker()));
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [previewRequests]);

  const duplicateTargets = useMemo(() => {
    const visited = new Set<string>();
    const duplicates = new Set<string>();
    for (const route of routes) {
      const key = pathKey(route.targetDatabase, route.targetSchema, route.targetTable.trim());
      if (visited.has(key)) duplicates.add(key);
      visited.add(key);
    }
    return duplicates;
  }, [routes]);

  const allReady =
    routes.length > 0 &&
    duplicateTargets.size === 0 &&
    routes.every((route) => {
      const key = sourceKey(route);
      const request = requestByKey.get(key);
      const entry = previews[key];
      return Boolean(
        request && entry?.fingerprint === request.fingerprint && entry.preview?.compatible,
      );
    });

  useEffect(() => {
    onReadyChange(allReady);
  }, [allReady, onReadyChange]);

  useEffect(() => {
    if (routes.length === 0) {
      setSelectedRoute("");
      return;
    }
    if (!routes.some((route) => sourceKey(route) === selectedRoute)) {
      setSelectedRoute(sourceKey(routes[0]));
    }
  }, [routes, selectedRoute]);

  const currentRoute = routes.find((route) => sourceKey(route) === selectedRoute) || routes[0];
  const currentKey = currentRoute ? sourceKey(currentRoute) : "";
  const currentRequest = requestByKey.get(currentKey);
  const currentEntry = previews[currentKey];
  const currentPreview =
    currentRequest?.fingerprint === currentEntry?.fingerprint ? currentEntry?.preview : undefined;

  const targetDerived =
    Boolean(currentRoute?.autoCreateTable) &&
    !targetCatalog.tables.some(
      (table) =>
        catalogKey(table, targetDatabase, targetSchema) ===
        pathKey(
          currentRoute?.targetDatabase,
          currentRoute?.targetSchema,
          currentRoute?.targetTable || "",
        ),
    );
  const sourceColumns = useColumns(
    sourceDataSourceId,
    currentRoute?.sourceDatabase,
    currentRoute?.sourceSchema,
    currentRoute?.sourceTable,
    Boolean(currentRoute),
  );
  const targetColumns = useColumns(
    targetDataSourceId,
    currentRoute?.targetDatabase,
    currentRoute?.targetSchema,
    currentRoute?.targetTable,
    Boolean(currentRoute && !targetDerived && !targetCatalog.loading),
  );

  const updateRoute = (key: string, patch: Partial<DataSyncTableRoute>, clearMapping = false) => {
    onChange(
      routes.map((route) =>
        sourceKey(route) === key
          ? {
              ...route,
              ...patch,
              mapping: clearMapping ? undefined : (patch.mapping ?? route.mapping),
            }
          : route,
      ),
    );
  };

  const toggleSource = (table: DataSourceCatalogTable, checked: boolean) => {
    const database = table.database || sourceDatabase;
    const schema = table.schema || sourceSchema;
    const key = pathKey(database, schema, table.name);
    if (!checked) {
      onChange(routes.filter((route) => sourceKey(route) !== key));
      return;
    }
    if (routeByKey.has(key) || routes.length >= 50) return;
    const route: DataSyncTableRoute = {
      sourceDatabase: database || undefined,
      sourceSchema: schema || undefined,
      sourceTable: table.name,
      targetDatabase: targetDatabase || undefined,
      targetSchema: targetSchema || undefined,
      targetTable: table.name,
      autoCreateTable: false,
    };
    onChange([...routes, route]);
    setSelectedRoute(key);
  };

  const currentDdl = currentPreview?.ddlStatements?.length
    ? currentPreview.ddlStatements
    : currentPreview?.createTableSql
      ? [currentPreview.createTableSql]
      : [];

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
                options={sourceCatalog.schemas.map((value) => ({ value, label: value }))}
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
              value={sourceKeyword}
              placeholder="搜索表"
              onChange={(event) => setSourceKeyword(event.target.value)}
            />
            <Button size="small" aria-label="刷新表" onClick={sourceCatalog.refresh}>
              <RefreshCw size={15} />
            </Button>
          </div>
          <div className="max-h-[260px] overflow-y-auto rounded-md border border-[#e6e8eb]">
            {sourceTables.map((table) => {
              const key = catalogKey(table, sourceDatabase, sourceSchema);
              const checked = routeByKey.has(key);
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
            {sourceCatalog.loading ? (
              <div className="px-3 py-5 text-center text-xs text-[#98a2b3]">正在读取表...</div>
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
                options={targetCatalog.schemas.map((value) => ({ value, label: value }))}
                placeholder="选择 Schema"
                onRefresh={targetCatalog.refresh}
                refreshing={targetCatalog.loading}
                onValueChange={onTargetSchemaChange}
              />
            </Field>
          ) : null}
          {routes.length > 0 ? (
            <div className="space-y-2">
              {routes.map((route) => {
                const key = sourceKey(route);
                const request = requestByKey.get(key);
                const entry = previews[key];
                const preview =
                  entry?.fingerprint === request?.fingerprint ? entry?.preview : undefined;
                const duplicate = duplicateTargets.has(
                  pathKey(route.targetDatabase, route.targetSchema, route.targetTable.trim()),
                );
                const option = pathKey(route.targetDatabase, route.targetSchema, route.targetTable);
                const selectedOption = targetOptions.some((item) => item.value === option)
                  ? option
                  : null;
                return (
                  <div
                    key={key}
                    className="grid grid-cols-[minmax(100px,1fr)_minmax(170px,1.4fr)_100px_68px] items-center gap-3 rounded-md border border-[#e6e8eb] px-3 py-2 max-lg:grid-cols-2"
                  >
                    <span className="truncate text-[13px] text-[#344054]">{tableLabel(route)}</span>
                    {route.autoCreateTable ? (
                      <Input
                        size="small"
                        variant="outlined"
                        value={route.targetTable}
                        placeholder="目标表"
                        onChange={(event) =>
                          updateRoute(key, { targetTable: event.target.value }, true)
                        }
                      />
                    ) : (
                      <DataSyncSearchableSelect
                        value={selectedOption}
                        options={targetOptions}
                        disabled={!targetDataSourceId}
                        placeholder="选择目标表"
                        onRefresh={targetCatalog.refresh}
                        refreshing={targetCatalog.loading}
                        onValueChange={(value) => {
                          const chosen = targetCatalog.tables.find(
                            (item) => catalogKey(item, targetDatabase, targetSchema) === value,
                          );
                          if (chosen) {
                            updateRoute(
                              key,
                              {
                                targetDatabase: chosen.database || targetDatabase,
                                targetSchema: chosen.schema || targetSchema,
                                targetTable: chosen.name,
                              },
                              true,
                            );
                          }
                        }}
                      />
                    )}
                    <label className="flex items-center gap-2 text-xs text-[#667085]">
                      <Switch
                        size="small"
                        checked={Boolean(route.autoCreateTable)}
                        onCheckedChange={(value) =>
                          updateRoute(
                            key,
                            {
                              autoCreateTable: Boolean(value),
                              targetTable: route.targetTable || route.sourceTable,
                            },
                            true,
                          )
                        }
                      />
                      自动建表
                    </label>
                    <div className="flex items-center justify-end gap-1 text-xs">
                      {duplicate ||
                      (entry?.fingerprint === request?.fingerprint && entry.failed) ||
                      (preview && !preview.compatible) ? (
                        <>
                          <CircleAlert size={14} className="text-[#d92d20]" />
                          <span className="text-[#b42318]">异常</span>
                        </>
                      ) : preview?.compatible ? (
                        <>
                          <Check size={14} className="text-[#039855]" />
                          <span className="text-[#039855]">兼容</span>
                        </>
                      ) : (
                        <CircleDashed size={14} className="text-[#98a2b3]" />
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="py-5 text-center text-xs text-[#98a2b3]">请先选择来源表</div>
          )}
          {duplicateTargets.size > 0 ? <Alert>目标表不能重复映射。</Alert> : null}
        </div>
      </CollapseSection>

      <CollapseSection id="mapping" title="去向字段映射">
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {routes.map((route) => {
              const key = sourceKey(route);
              const request = requestByKey.get(key);
              const entry = previews[key];
              const preview =
                entry?.fingerprint === request?.fingerprint ? entry?.preview : undefined;
              return (
                <button
                  key={key}
                  type="button"
                  className={`rounded-md border px-3 py-1.5 text-xs transition-colors ${
                    currentKey === key
                      ? "border-[#162044] bg-[#f4f6fb] font-semibold text-[#162044]"
                      : "border-[#e6e8eb] bg-white text-[#667085] hover:bg-[#f9fafb]"
                  }`}
                  onClick={() => setSelectedRoute(key)}
                >
                  {tableLabel(route)}
                  {preview ? (
                    <span
                      className={preview.compatible ? "ml-1 text-[#039855]" : "ml-1 text-[#d92d20]"}
                    >
                      {preview.compatible ? "✓" : "!"}
                    </span>
                  ) : null}
                </button>
              );
            })}
          </div>

          {currentRoute ? (
            <div className="space-y-3">
              <SchemaMappingEditor
                key={currentKey}
                value={currentRoute.mapping}
                onChange={(mapping) => updateRoute(currentKey, { mapping })}
                sourceColumns={sourceColumns.columns}
                targetColumns={targetColumns.columns}
                sourceLoading={sourceColumns.loading}
                targetLoading={targetColumns.loading}
                sourceReady={Boolean(sourceDataSourceId && currentRoute.sourceTable)}
                targetReady={Boolean(targetDataSourceId && currentRoute.targetTable.trim())}
                targetDerived={targetDerived}
                preview={currentPreview}
              />
              {currentRoute.mapping?.columns.length === 0 ? (
                <Alert>至少保留一个字段映射。</Alert>
              ) : null}
              {currentPreview?.unsupportedReasons?.length ? (
                <Alert>
                  {currentPreview.unsupportedReasons.map((reason, index) => (
                    <div key={index}>{reason}</div>
                  ))}
                </Alert>
              ) : null}
              {currentDdl.length > 0 ? (
                <details className="rounded-md border border-[#e6e8eb] bg-white">
                  <summary className="cursor-pointer px-3 py-2 text-xs font-medium text-[#344054]">
                    建表 SQL
                  </summary>
                  <pre className="max-h-72 overflow-auto bg-[#f5f5f5] px-3 py-3 text-xs leading-5 text-[#344054]">
                    {currentDdl.join("\n\n")}
                  </pre>
                </details>
              ) : null}
            </div>
          ) : (
            <div className="rounded-lg border border-[#e6e8eb] bg-white p-4 text-xs text-[#98a2b3]">
              请先选择来源表
            </div>
          )}
        </div>
      </CollapseSection>
    </>
  );
}

export default MultiTableRouteEditor;
