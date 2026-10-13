import {
  Alert,
  Button,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
  PageHeader,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";

import { EditorAnchorStepper, type EditorAnchorItem } from "@/app/data-sync/editor-anchor-stepper";
import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import { getDataSourceTypeLabel } from "@/app/datasource/constants";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import {
  listDataSources,
  listDataSourceSchemas,
  listDataSourceTables,
  type DataSourceCatalogTable,
  type DataSourceRecord,
} from "@/service/datasource";
import {
  createDataSyncTask,
  getDataSyncTask,
  publishDataSyncTask,
  updateDataSyncTask,
  type DataSyncTaskSavePayload,
  type DataSyncTaskStatus,
  type DataSyncType,
  type DataSyncWriteMode,
} from "@/service/data-sync";

interface EditorForm {
  name: string;
  remark: string;
  writeMode: DataSyncWriteMode;
  sourceDataSourceId: string;
  sourceDatabase: string;
  sourceSchema: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase: string;
  targetSchema: string;
  targetTable: string;
}

interface CatalogOptions {
  schemas: string[];
  tables: DataSourceCatalogTable[];
  loading: boolean;
  refresh: () => void;
}

const EMPTY_FORM: EditorForm = {
  name: "",
  remark: "",
  writeMode: "APPEND",
  sourceDataSourceId: "",
  sourceDatabase: "",
  sourceSchema: "",
  sourceTable: "",
  targetDataSourceId: "",
  targetDatabase: "",
  targetSchema: "",
  targetTable: "",
};

const tableKey = (table: DataSourceCatalogTable) =>
  [table.database || "", table.schema || "", table.name].join("|");

const tableLabel = (table: DataSourceCatalogTable) =>
  [table.schema, table.name].filter(Boolean).join(".") || table.name;

const selectedTableKey = (
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

function useCatalogOptions(dataSourceId: string, database: string, schema: string): CatalogOptions {
  const [schemas, setSchemas] = useState<string[]>([]);
  const [tables, setTables] = useState<DataSourceCatalogTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const refresh = useCallback(() => setRefreshVersion((value) => value + 1), []);

  useEffect(() => {
    if (!dataSourceId) {
      setSchemas([]);
      setTables([]);
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    void Promise.all([
      listDataSourceSchemas(dataSourceId, database || undefined),
      listDataSourceTables(dataSourceId, {
        database: database || undefined,
        schema: schema || undefined,
        limit: 500,
      }),
    ])
      .then(([schemaOptions, tableOptions]) => {
        if (!active) return;
        setSchemas(schemaOptions || []);
        setTables(tableOptions || []);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataSourceId, database, schema, refreshVersion]);

  return { schemas, tables, loading, refresh };
}

interface DataSourceEndpointCardProps {
  title: string;
  dataSources: DataSourceRecord[];
  dataSourceId: string;
  refreshing: boolean;
  onRefresh: () => void | Promise<void>;
  onCreateDataSource: () => void;
  onDataSourceChange: (value: string) => void;
}

function DataSourceEndpointCard({
  title,
  dataSources,
  dataSourceId,
  refreshing,
  onRefresh,
  onCreateDataSource,
  onDataSourceChange,
}: DataSourceEndpointCardProps) {
  const selectedDataSource = dataSources.find((item) => item.id === dataSourceId);
  const dataSourceOptions = useMemo(
    () =>
      dataSources.flatMap((item) =>
        item.id
          ? [
              {
                value: item.id,
                label: item.name || item.id,
                searchText: [item.dbType, item.database, item.schema].filter(Boolean).join(" "),
              },
            ]
          : [],
      ),
    [dataSources],
  );
  return (
    <div className="rounded-lg border border-[#e6e8eb] bg-white p-4">
      <div className="text-sm font-semibold text-[#344054]">{title}</div>
      <div className="mt-4 space-y-3">
        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>类型</FieldLabel>
          {selectedDataSource ? (
            <div className="flex items-center gap-2 text-[13px] text-[#344054]">
              <DatabaseIcons dbType={selectedDataSource.dbType} width="16px" height="16px" />
              <span>{getDataSourceTypeLabel(selectedDataSource.dbType)}</span>
            </div>
          ) : (
            <div className="text-[13px] text-[#98a2b3]">-</div>
          )}
        </Field>

        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据源</FieldLabel>
          <DataSyncSearchableSelect
            value={dataSourceId || null}
            options={dataSourceOptions}
            placeholder="请选择数据源"
            searchPlaceholder="搜索数据源"
            emptyText="暂无数据源"
            refreshing={refreshing}
            onRefresh={onRefresh}
            footer={
              <Button
                size="small"
                variant="ghost"
                className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                onClick={onCreateDataSource}
              >
                <Plus size={14} />
                新增数据源
              </Button>
            }
            onValueChange={onDataSourceChange}
          />
        </Field>
      </div>
    </div>
  );
}

interface TableSectionProps {
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

function TableSection({
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
    <div className="rounded-lg border border-[#e6e8eb] bg-white p-4">
      <div className="space-y-3">
        {requiresSchema ? (
          <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
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
        <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>表</FieldLabel>
          <div className="w-1/2 min-w-0 max-lg:w-auto max-lg:flex-1">
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
          </div>
        </Field>
        {children}
      </div>
    </div>
  );
}

interface DataSyncTaskEditorPageProps {
  syncType: DataSyncType;
}

export function DataSyncTaskEditorPage({ syncType }: DataSyncTaskEditorPageProps) {
  const realtime = syncType === "REALTIME";
  const localScroll = !realtime;
  const basePath = realtime ? "/realtime-sync" : "/offline-sync";
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();
  const draft = (
    location.state as {
      draft?: {
        name?: string;
        sourceDataSourceId?: string;
        targetDataSourceId?: string;
      };
    } | null
  )?.draft;

  const [form, setForm] = useState<EditorForm>(() => ({
    ...EMPTY_FORM,
    name: draft?.name || "",
    sourceDataSourceId: draft?.sourceDataSourceId || "",
    targetDataSourceId: draft?.targetDataSourceId || "",
  }));
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [dataSourcesLoading, setDataSourcesLoading] = useState(false);
  const [taskStatus, setTaskStatus] = useState<DataSyncTaskStatus>("UNPUBLISHED");
  const [loading, setLoading] = useState(editing);
  const [saving, setSaving] = useState(false);
  const [parametersDirty, setParametersDirty] = useState(false);
  const editorScrollRef = useRef<HTMLElement | null>(null);
  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "basic", label: "基本信息" },
      { id: "datasource", label: "数据源" },
      { id: "source", label: "数据来源" },
      { id: "target", label: "数据去向" },
    ],
    [],
  );

  const sourceCatalog = useCatalogOptions(
    form.sourceDataSourceId,
    form.sourceDatabase,
    form.sourceSchema,
  );
  const targetCatalog = useCatalogOptions(
    form.targetDataSourceId,
    form.targetDatabase,
    form.targetSchema,
  );

  const sourceDataSources = useMemo(() => {
    if (!realtime) return dataSources;
    return dataSources.filter((item) => item.dbType === "MYSQL");
  }, [dataSources, realtime]);
  const targetDataSources = useMemo(() => {
    if (!realtime) return dataSources;
    return dataSources.filter((item) =>
      ["MYSQL", "POSTGRE_SQL", "ORACLE"].includes(item.dbType || ""),
    );
  }, [dataSources, realtime]);
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.sourceDataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.targetDataSourceId);
  const loadDataSources = useCallback(async () => {
    setDataSourcesLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 });
      setDataSources(result?.bizData || []);
    } finally {
      setDataSourcesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
  }, [loadDataSources]);

  useEffect(() => {
    if (dataSources.length === 0) return;
    setForm((current) => {
      const source = dataSources.find((item) => item.id === current.sourceDataSourceId);
      const target = dataSources.find((item) => item.id === current.targetDataSourceId);
      return {
        ...current,
        sourceDatabase: source?.database || current.sourceDatabase,
        sourceSchema: source?.schema || current.sourceSchema,
        targetDatabase: target?.database || current.targetDatabase,
        targetSchema: target?.schema || current.targetSchema,
      };
    });
  }, [dataSources]);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    void getDataSyncTask(id)
      .then((task) => {
        if (!active) return;
        if (task.syncType !== syncType) {
          toast.error("任务类型与当前页面不匹配");
          navigate(basePath, { replace: true });
          return;
        }
        setTaskStatus(task.status === "PUBLISHED" ? "PUBLISHED" : "UNPUBLISHED");
        setParametersDirty(false);
        setForm({
          name: task.name,
          remark: task.remark || "",
          writeMode:
            task.writeMode === "OVERWRITE" || task.writeMode === "UPSERT"
              ? task.writeMode
              : "APPEND",
          sourceDataSourceId: task.sourceDataSourceId,
          sourceDatabase: task.sourceDatabase || "",
          sourceSchema: task.sourceSchema || "",
          sourceTable: task.sourceTable,
          targetDataSourceId: task.targetDataSourceId,
          targetDatabase: task.targetDatabase || "",
          targetSchema: task.targetSchema || "",
          targetTable: task.targetTable,
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [basePath, id, navigate, realtime, syncType]);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => {
    if (key !== "name" && key !== "remark") setParametersDirty(true);
    setForm((current) => ({ ...current, [key]: value }));
  };

  const payload = (): DataSyncTaskSavePayload => {
    const common = {
      name: form.name.trim(),
      sourceDataSourceId: form.sourceDataSourceId,
      sourceDatabase: form.sourceDatabase || undefined,
      sourceSchema: form.sourceSchema || undefined,
      sourceTable: form.sourceTable,
      targetDataSourceId: form.targetDataSourceId,
      targetDatabase: form.targetDatabase || undefined,
      targetSchema: form.targetSchema || undefined,
      targetTable: form.targetTable,
      remark: form.remark.trim() || undefined,
    };
    if (realtime) {
      return {
        ...common,
        syncType: "REALTIME",
      };
    }
    return {
      ...common,
      writeMode: form.writeMode,
      syncType: "OFFLINE",
    };
  };

  const published = editing && taskStatus === "PUBLISHED";
  const canSave =
    !published &&
    form.name.trim() &&
    Boolean(
      form.sourceDataSourceId &&
      form.sourceTable &&
      form.targetDataSourceId &&
      selectedTableKey(
        targetCatalog.tables,
        form.targetDatabase,
        form.targetSchema,
        form.targetTable,
      ),
    ) &&
    !sourceCatalog.loading &&
    !targetCatalog.loading;

  const save = async (publishAfterSave = false) => {
    if (!canSave || saving) return;
    setSaving(true);
    try {
      const request = payload();
      const saved =
        editing && id
          ? await updateDataSyncTask(
              id,
              parametersDirty ? request : { name: request.name, remark: request.remark },
            )
          : await createDataSyncTask(request);

      if (publishAfterSave) {
        try {
          await publishDataSyncTask(saved.id);
        } catch {
          toast.warning("任务已保存，但上线失败，当前保持已下线");
          navigate(`${basePath}/${saved.id}`, { replace: true });
          return;
        }
        toast.success(realtime ? "实时同步任务已保存并上线" : "同步任务已保存并上线");
        navigate(basePath, { replace: true });
        return;
      }
      toast.success(editing ? "同步任务已保存，当前仍为已下线" : "同步任务已创建，当前为已下线");
      navigate(`${basePath}/${saved.id}`, { replace: true });
    } finally {
      setSaving(false);
    }
  };

  const pageTitle = editing
    ? form.name || (realtime ? "编辑实时同步任务" : "编辑离线同步任务")
    : realtime
      ? "新建实时同步任务"
      : "新建离线同步任务";
  const pageDescription = realtime
    ? "MySQL CDC 单表实时同步 · 首次全量后持续消费 Binlog"
    : undefined;

  if (loading) {
    return <div className="p-8 text-sm text-[#667085]">正在加载同步任务...</div>;
  }

  if (published) {
    return (
      <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
        <PageHeader
          title={pageTitle}
          description={pageDescription}
          bordered
          className="bg-white px-6 max-md:px-4"
          extra={
            <Button size="small" onClick={() => navigate(basePath)}>
              返回任务列表
            </Button>
          }
        />
        <div className="px-6 pt-5 max-md:px-4">
          <Alert>任务已上线，当前不可编辑。请先在任务列表下线，再修改任务定义。</Alert>
        </div>
      </div>
    );
  }

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-[#f6f6f6] text-[#242731]"
          : "min-h-full bg-[#f6f6f6] text-[#242731]"
      }
    >
      <PageHeader
        title={pageTitle}
        description={pageDescription}
        bordered
        className={localScroll ? "shrink-0 bg-white px-6 max-md:px-4" : "bg-white px-6 max-md:px-4"}
        extra={
          <>
            <Button size="small" disabled={saving} onClick={() => navigate(basePath)}>
              取消
            </Button>
            <Button size="small" loading={saving} disabled={!canSave} onClick={() => void save()}>
              保存
            </Button>
            <Button
              size="small"
              variant="primary"
              loading={saving}
              disabled={!canSave}
              onClick={() => void save(true)}
            >
              保存并上线
            </Button>
          </>
        }
      />

      <div
        className={
          localScroll
            ? "flex min-h-0 flex-1 gap-5 px-6 max-md:px-4"
            : "flex gap-5 px-6 pb-8 pt-5 max-md:px-4"
        }
      >
        <main
          ref={editorScrollRef}
          className={
            localScroll
              ? "min-h-0 min-w-0 flex-1 space-y-4 overflow-y-auto pb-8 pt-5"
              : "min-w-0 flex-1 space-y-4"
          }
        >
          {editing ? (
            <Alert>
              {realtime
                ? "当前任务已下线，可修改任务定义。执行配置变化会生成新版本，新版本上线后首次启动会重新全量同步；仅修改名称或备注不会增加版本。"
                : "当前任务已下线，可修改任务定义。保存后仍需上线，任务才可以运行。"}
            </Alert>
          ) : (
            <Alert>新建任务保存后默认处于已下线状态，需要上线后才可以运行或启动。</Alert>
          )}

          {realtime ? (
            <div className="rounded-lg border border-[#b2ccff] bg-[#f5f8ff] px-4 py-3 text-xs leading-5 text-[#344054]">
              首次启动会先同步来源表当前全量数据，随后持续消费 MySQL
              Binlog；停止后再次启动同一任务版本会从已保存的 CDC 状态继续。
            </div>
          ) : null}

          <CollapseSection id="basic" title="基本信息">
            <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel required>任务名称</FieldLabel>
                <Input
                  size="small"
                  variant="outlined"
                  maxLength={128}
                  value={form.name}
                  placeholder="请输入任务名称"
                  onChange={(event) => patch("name", event.target.value)}
                />
              </Field>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
                <FieldLabel className="pt-1.5">备注</FieldLabel>
                <Textarea
                  size="small"
                  rows={2}
                  maxLength={500}
                  value={form.remark}
                  className="min-h-[56px] resize-none"
                  placeholder="可选"
                  onValueChange={(value) => patch("remark", value)}
                />
              </Field>
            </div>
          </CollapseSection>

          <CollapseSection id="datasource" title="数据源">
            <div className="grid grid-cols-2 gap-3 max-lg:grid-cols-1">
              <DataSourceEndpointCard
                title="来源"
                dataSources={sourceDataSources}
                dataSourceId={form.sourceDataSourceId}
                refreshing={dataSourcesLoading}
                onRefresh={loadDataSources}
                onCreateDataSource={() => navigate("/data-source?create=1")}
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setParametersDirty(true);
                  setForm((current) => ({
                    ...current,
                    sourceDataSourceId: value,
                    sourceDatabase: selected?.database || "",
                    sourceSchema: selected?.schema || "",
                    sourceTable: "",
                  }));
                }}
              />
              <DataSourceEndpointCard
                title="去向"
                dataSources={targetDataSources}
                dataSourceId={form.targetDataSourceId}
                refreshing={dataSourcesLoading}
                onRefresh={loadDataSources}
                onCreateDataSource={() => navigate("/data-source?create=1")}
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setParametersDirty(true);
                  setForm((current) => ({
                    ...current,
                    targetDataSourceId: value,
                    targetDatabase: selected?.database || "",
                    targetSchema: selected?.schema || "",
                    targetTable: "",
                  }));
                }}
              />
            </div>
          </CollapseSection>

          <CollapseSection id="source" title="数据来源">
            <TableSection
              dataSourceId={form.sourceDataSourceId}
              boundSchema={selectedSourceDataSource?.schema}
              database={form.sourceDatabase}
              schema={form.sourceSchema}
              table={form.sourceTable}
              catalog={sourceCatalog}
              onSchemaChange={(value) => {
                setParametersDirty(true);
                setForm((current) => ({
                  ...current,
                  sourceSchema: value,
                  sourceTable: "",
                }));
              }}
              onTableChange={(table) => {
                setParametersDirty(true);
                setForm((current) => ({
                  ...current,
                  sourceDatabase: current.sourceDatabase || table.database || "",
                  sourceSchema: current.sourceSchema || table.schema || "",
                  sourceTable: table.name,
                }));
              }}
            >
              {realtime ? (
                <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
              ) : null}
            </TableSection>
          </CollapseSection>

          <CollapseSection id="target" title="数据去向">
            <TableSection
              dataSourceId={form.targetDataSourceId}
              boundSchema={selectedTargetDataSource?.schema}
              database={form.targetDatabase}
              schema={form.targetSchema}
              table={form.targetTable}
              catalog={targetCatalog}
              onSchemaChange={(value) => {
                setParametersDirty(true);
                setForm((current) => ({ ...current, targetSchema: value, targetTable: "" }));
              }}
              onTableChange={(table) => {
                setParametersDirty(true);
                setForm((current) => ({
                  ...current,
                  targetDatabase: current.targetDatabase || table.database || "",
                  targetSchema: current.targetSchema || table.schema || "",
                  targetTable: table.name,
                }));
              }}
            />
          </CollapseSection>
        </main>

        <aside
          className={
            localScroll
              ? "hidden h-fit w-44 shrink-0 self-start pt-5 lg:block"
              : "sticky top-4 hidden h-fit w-44 shrink-0 self-start lg:block"
          }
        >
          <EditorAnchorStepper
            items={anchorItems}
            scrollRootRef={localScroll ? editorScrollRef : undefined}
          />
        </aside>
      </div>
    </div>
  );
}

export default DataSyncTaskEditorPage;
