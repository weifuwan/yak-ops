import {
  Alert,
  Button,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
  PageHeader,
  Switch,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  clearTaskDraft,
  readTaskDraft,
  taskDraftKey,
  writeTaskDraft,
} from "@/service/data-sync/draft";
import { solutionSyncType } from "@/service/data-sync/parameters";
import { findStepCombination, solutionSummary } from "./solution-presentation";
import { useSolutions } from "./use-solutions";

import { EditorAnchorStepper, type EditorAnchorItem } from "@/app/data-sync/editor-anchor-stepper";
import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import { ScheduleForm } from "@/app/schedule/schedule-form";
import { EMPTY_SCHEDULE, sameScheduleRule } from "@/app/schedule/schedule-presentation";
import {
  createSchedule,
  getTaskSchedule,
  previewSchedule,
  scheduleErrorMessage,
  updateSchedule,
  type ScheduleRecord,
  type ScheduleTimeRule,
} from "@/service/schedule";
import { getDataSourceTypeLabel } from "@/app/datasource/constants";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import {
  getDataSource,
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
  type DataSyncTaskRecord,
  type DataSyncTaskSavePayload,
  type DataSyncTaskStatus,
  type DataSyncDraft,
  type DataSyncWriteMode,
} from "@/service/data-sync";

interface EditorForm {
  name: string;
  remark: string;
  debug: boolean;
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
  error: string;
  refresh: () => void;
}

const EMPTY_FORM: EditorForm = {
  name: "",
  remark: "",
  debug: false,
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
  const key = JSON.stringify([dataSourceId, database, schema]);
  const [result, setResult] = useState<{
    key: string;
    schemas: string[];
    tables: DataSourceCatalogTable[];
    loading: boolean;
    error: string;
  }>({ key: "", schemas: [], tables: [], loading: false, error: "" });
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);
  useEffect(() => {
    if (!dataSourceId) return;
    let active = true;
    setResult({ key, schemas: [], tables: [], loading: true, error: "" });
    void Promise.all([
      listDataSourceSchemas(dataSourceId, database || undefined),
      listDataSourceTables(dataSourceId, {
        database: database || undefined,
        schema: schema || undefined,
        limit: 500,
      }),
    ])
      .then(([schemas, tables]) => {
        if (active) setResult({ key, schemas, tables, loading: false, error: "" });
      })
      .catch(() => {
        if (active)
          setResult({ key, schemas: [], tables: [], loading: false, error: "表信息加载失败" });
      });
    return () => {
      active = false;
    };
  }, [key, dataSourceId, database, schema, revision]);
  const current = Boolean(dataSourceId) && result.key === key;
  return {
    schemas: current ? result.schemas : [],
    tables: current ? result.tables : [],
    loading: Boolean(dataSourceId) && (!current || result.loading),
    error: current ? result.error : "",
    refresh,
  };
}

interface DataSourceEndpointCardProps {
  title: string;
  dataSourceType: string;
  dataSources: DataSourceRecord[];
  dataSourceId: string;
  refreshing: boolean;
  onRefresh: () => void | Promise<void>;
  onCreateDataSource: () => void;
  onDataSourceChange: (value: string) => void;
}

function DataSourceEndpointCard({
  title,
  dataSourceType,
  dataSources,
  dataSourceId,
  refreshing,
  onRefresh,
  onCreateDataSource,
  onDataSourceChange,
}: DataSourceEndpointCardProps) {
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
          <div className="flex items-center gap-2 text-[13px] text-[#344054]">
            <DatabaseIcons dbType={dataSourceType} width="16px" height="16px" />
            <span>{getDataSourceTypeLabel(dataSourceType)}</span>
          </div>
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

interface TaskEditorInput {
  draft: DataSyncDraft;
  task?: DataSyncTaskRecord;
  connections?: DataSourceRecord[];
}

/** Resolve a saved solution from its definition, never from the route used to open it. */
export function DataSyncTaskEditorPage() {
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const key = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const [input, setInput] = useState<TaskEditorInput & { id: string }>();
  const [error, setError] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    setInput(undefined);
    setError(false);
    void getDataSyncTask(id, controller.signal)
      .then(async (task) => {
        const connections = await Promise.all([
          getDataSource(task.sourceDataSourceId, controller.signal),
          getDataSource(task.targetDataSourceId, controller.signal),
        ]);
        if (controller.signal.aborted) return;
        setInput({
          id,
          task,
          connections,
          draft: {
            name: task.name,
            solutionType: task.solutionType,
            steps: task.steps,
            sourceType: connections[0].dbType || "",
            targetType: connections[1].dbType || "",
          },
        });
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      });
    return () => controller.abort();
  }, [id, reload]);

  if (!id) {
    const state = location.state as { draftKey?: string; draft?: DataSyncDraft } | null;
    const draft = readTaskDraft(key) || (state?.draftKey === key ? state.draft : undefined);
    if (!draft) return <Navigate replace to="/data-sync?create=1" />;
    return <TaskEditor key="new" draft={draft} />;
  }
  if (error)
    return (
      <div className="p-6">
        <Alert>
          任务加载失败
          <Button size="small" onClick={() => setReload((value) => value + 1)}>
            重试
          </Button>
        </Alert>
      </div>
    );
  if (!input || input.id !== id)
    return <div className="p-8 text-sm text-[#667085]">正在加载同步任务...</div>;
  return <TaskEditor key={id} {...input} />;
}

function TaskEditor({ draft, task: initialTask, connections = [] }: TaskEditorInput) {
  const localScroll = true;
  const basePath = "/data-sync";
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const draftKey = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const {
    choices,
    loading: solutionsLoading,
    error: solutionsError,
    refresh: reloadSolutions,
  } = useSolutions(draft.sourceType, draft.targetType);
  const solution = choices.find((value) => value.solutionType === draft.solutionType);
  const combination = findStepCombination(solution, draft.steps);
  const supportsScheduling = Boolean(combination?.supportsScheduling);
  const usesChangelog =
    draft.steps.includes("INCREMENTAL") && solutionSyncType(draft.solutionType) === "REALTIME";

  const [form, setForm] = useState<EditorForm>(() => {
    const value = initialTask || draft.form;
    return {
      ...EMPTY_FORM,
      name: draft.name,
      remark: value?.remark || "",
      debug: value?.debug ?? false,
      writeMode: value?.writeMode === "UPSERT" || usesChangelog ? "UPSERT" : "APPEND",
      sourceDataSourceId: value?.sourceDataSourceId || "",
      sourceDatabase: value?.sourceDatabase || "",
      sourceSchema: value?.sourceSchema || "",
      sourceTable: value?.sourceTable || "",
      targetDataSourceId: value?.targetDataSourceId || "",
      targetDatabase: value?.targetDatabase || "",
      targetSchema: value?.targetSchema || "",
      targetTable: value?.targetTable || "",
    };
  });
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>(connections);
  const [dataSourcesLoading, setDataSourcesLoading] = useState(false);
  const [taskStatus] = useState<DataSyncTaskStatus>(
    initialTask?.status === "PUBLISHED" ? "PUBLISHED" : "UNPUBLISHED",
  );
  const [saving, setSaving] = useState(false);
  const [parametersDirty, setParametersDirty] = useState(false);
  const [taskDirty, setTaskDirty] = useState(!editing);
  const savedTask = useRef(initialTask);
  const saveSession = useRef<AbortController>();
  const pendingSchedule = location.state as {
    scheduleDraft?: ScheduleTimeRule;
    scheduleSaveError?: string;
  } | null;
  const [schedule, setSchedule] = useState<ScheduleRecord | null>(null);
  const [scheduleForm, setScheduleForm] = useState<ScheduleTimeRule>(
    pendingSchedule?.scheduleDraft || { ...EMPTY_SCHEDULE },
  );
  const [scheduleLoading, setScheduleLoading] = useState(
    Boolean(initialTask && initialTask.syncType === "OFFLINE"),
  );
  const [scheduleError, setScheduleError] = useState(pendingSchedule?.scheduleSaveError || "");
  const [scheduleNeedsReload, setScheduleNeedsReload] = useState(
    Boolean(pendingSchedule?.scheduleSaveError),
  );
  const [scheduleReload, setScheduleReload] = useState(0);
  const scheduleDraft = useRef(pendingSchedule?.scheduleDraft);

  useEffect(() => {
    const controller = new AbortController();
    saveSession.current = controller;
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!supportsScheduling || !id) return;
    const controller = new AbortController();
    setScheduleLoading(true);
    void getTaskSchedule(id, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setSchedule(value);
        if (!scheduleDraft.current) setScheduleForm(value || { ...EMPTY_SCHEDULE });
        if (scheduleReload > 0) {
          setScheduleError("");
          setScheduleNeedsReload(false);
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setScheduleError(scheduleErrorMessage(failure));
          setScheduleNeedsReload(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setScheduleLoading(false);
      });
    return () => controller.abort();
  }, [id, supportsScheduling, scheduleReload]);
  const editorScrollRef = useRef<HTMLElement | null>(null);
  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "basic", label: "基本信息" },
      { id: "datasource", label: "数据源" },
      { id: "source", label: "数据来源" },
      { id: "target", label: "目标表映射" },
      ...(supportsScheduling ? [{ id: "schedule", label: "调度配置" }] : []),
    ],
    [supportsScheduling],
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

  const sourceDataSources = useMemo(
    () => dataSources.filter((item) => item.dbType === draft.sourceType),
    [dataSources, draft.sourceType],
  );
  const targetDataSources = useMemo(
    () => dataSources.filter((item) => item.dbType === draft.targetType),
    [dataSources, draft.targetType],
  );
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.sourceDataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.targetDataSourceId);
  const datasourceRequest = useRef<AbortController>();
  const loadDataSources = useCallback(async () => {
    datasourceRequest.current?.abort();
    const controller = new AbortController();
    datasourceRequest.current = controller;
    setDataSourcesLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 }, controller.signal);
      if (controller.signal.aborted) return;
      setDataSources((current) => {
        const selected = current.filter(
          (value) =>
            value.id === savedTask.current?.sourceDataSourceId ||
            value.id === savedTask.current?.targetDataSourceId,
        );
        return [
          ...new Map([...selected, ...result.bizData].map((value) => [value.id, value])).values(),
        ];
      });
    } catch {
      // HTTP failures are reported by the shared transport; existing selections are retained.
    } finally {
      if (!controller.signal.aborted) setDataSourcesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
    return () => datasourceRequest.current?.abort();
  }, [loadDataSources]);

  useEffect(() => {
    if (id || savedTask.current) return;
    writeTaskDraft(draftKey, {
      sourceType: draft.sourceType,
      targetType: draft.targetType,
      solutionType: draft.solutionType,
      steps: draft.steps,
      name: form.name,
      form: { ...form, solutionType: draft.solutionType, steps: draft.steps },
    });
  }, [id, draftKey, draft.sourceType, draft.targetType, draft.solutionType, draft.steps, form]);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => {
    setTaskDirty(true);
    if (key !== "name" && key !== "remark" && key !== "debug") setParametersDirty(true);
    setForm((current) => ({ ...current, [key]: value }));
  };

  const payload = (): DataSyncTaskSavePayload => {
    const common = {
      name: form.name.trim(),
      debug: form.debug,
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
    return {
      ...common,
      solutionType: draft.solutionType,
      steps: draft.steps,
      writeMode: form.writeMode,
    };
  };

  const published = editing && taskStatus === "PUBLISHED";
  const canSave =
    !published &&
    Boolean(combination) &&
    !solutionsLoading &&
    !solutionsError &&
    combination!.writeModes.includes(form.writeMode) &&
    selectedSourceDataSource?.dbType === draft.sourceType &&
    selectedTargetDataSource?.dbType === draft.targetType &&
    (!supportsScheduling ||
      (!scheduleLoading &&
        !scheduleNeedsReload &&
        (!schedule?.enabled || sameScheduleRule(scheduleForm, schedule)))) &&
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
    !targetCatalog.loading &&
    !sourceCatalog.error &&
    !targetCatalog.error;

  const save = async (publishAfterSave = false) => {
    const signal = saveSession.current?.signal;
    if (!canSave || saving || !signal || signal.aborted) return;
    setSaving(true);
    try {
      const ruleChanged =
        supportsScheduling && !sameScheduleRule(scheduleForm, schedule || EMPTY_SCHEDULE);
      if (ruleChanged && scheduleForm.cronExpression.trim()) {
        try {
          await previewSchedule(scheduleForm, signal);
        } catch (failure) {
          if (!signal.aborted) setScheduleError(scheduleErrorMessage(failure));
          return;
        }
      }
      if (signal.aborted) return;
      const request = payload();
      const metadataRequest = { name: request.name, remark: request.remark, debug: request.debug };
      let saved = savedTask.current;
      // A completed Task write is retained across a later Schedule failure.
      if (!saved || taskDirty) {
        const savedId = id || saved?.id;
        saved = savedId
          ? await updateDataSyncTask(savedId, parametersDirty ? request : metadataRequest, signal)
          : await createDataSyncTask(request, signal);
        if (signal.aborted) return;
        savedTask.current = saved;
        clearTaskDraft(draftKey);
        setTaskDirty(false);
        setParametersDirty(false);
      }
      if (ruleChanged && scheduleForm.cronExpression.trim()) {
        try {
          const value = schedule
            ? await updateSchedule(schedule.id, schedule.revision, scheduleForm, signal)
            : await createSchedule(saved.id, scheduleForm, signal);
          if (signal.aborted) return;
          setSchedule(value);
          setScheduleForm(value);
          scheduleDraft.current = undefined;
          setScheduleError("");
        } catch (failure) {
          if (signal.aborted) return;
          const message = "任务已保存，调度未确认保存。" + scheduleErrorMessage(failure);
          setScheduleError(message);
          setScheduleNeedsReload(true);
          scheduleDraft.current = scheduleForm;
          if (!id)
            navigate(`${basePath}/${saved.id}/edit`, {
              replace: true,
              state: { scheduleDraft: scheduleForm, scheduleSaveError: message },
            });
          return;
        }
      }
      if (signal.aborted) return;
      if (publishAfterSave) {
        try {
          await publishDataSyncTask(saved.id, signal);
        } catch {
          if (!signal.aborted) {
            toast.warning("任务已保存，但上线失败，当前保持已下线");
            navigate(`${basePath}/${saved.id}/edit`, { replace: true });
          }
          return;
        }
        if (signal.aborted) return;
        toast.success("同步任务已保存并上线");
        navigate(basePath, { replace: true });
        return;
      }
      toast.success(editing ? "同步任务已保存，当前仍为已下线" : "同步任务已创建，当前为已下线");
      navigate(`${basePath}/${saved.id}/edit`, { replace: true, state: null });
    } catch {
      // Task request errors are reported by the existing transport; drafts remain editable.
    } finally {
      if (!signal.aborted) setSaving(false);
    }
  };

  const pageTitle = editing ? form.name || "编辑同步任务" : "新建同步任务";

  if (published) {
    return (
      <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
        <PageHeader
          title={pageTitle}
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
          {solutionsError ? (
            <Alert>
              {solutionsError}
              <Button size="small" onClick={reloadSolutions}>
                重试
              </Button>
            </Alert>
          ) : null}
          {!solutionsLoading && !solutionsError && !combination ? (
            <Alert>当前链路不再支持所选同步方案</Alert>
          ) : null}
          {usesChangelog ? (
            <Alert>重新提交会创建新的执行实例并重新全量初始化，不会续用上次实例的检查点。</Alert>
          ) : null}

          <CollapseSection id="basic" title="基本信息">
            <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
              <div className="grid grid-cols-[112px_minmax(0,1fr)] items-center gap-3 text-sm">
                <span>同步方案</span>
                <span>{solutionSummary(draft.solutionType, draft.steps)}</span>
              </div>
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
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>Debug</FieldLabel>
                <Switch
                  size="small"
                  checked={form.debug}
                  onCheckedChange={(checked) => patch("debug", checked)}
                />
              </Field>
            </div>
          </CollapseSection>

          <CollapseSection id="datasource" title="数据源">
            <div className="grid grid-cols-2 gap-3 max-lg:grid-cols-1">
              <DataSourceEndpointCard
                title="来源"
                dataSourceType={draft.sourceType}
                dataSources={sourceDataSources}
                dataSourceId={form.sourceDataSourceId}
                refreshing={dataSourcesLoading}
                onRefresh={loadDataSources}
                onCreateDataSource={() =>
                  navigate(
                    "/data-source?create=1&returnTo=" +
                      encodeURIComponent(id ? `/data-sync/${id}/edit` : "/data-sync/new"),
                  )
                }
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setTaskDirty(true);
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
                dataSourceType={draft.targetType}
                dataSources={targetDataSources}
                dataSourceId={form.targetDataSourceId}
                refreshing={dataSourcesLoading}
                onRefresh={loadDataSources}
                onCreateDataSource={() =>
                  navigate(
                    "/data-source?create=1&returnTo=" +
                      encodeURIComponent(id ? `/data-sync/${id}/edit` : "/data-sync/new"),
                  )
                }
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setTaskDirty(true);
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
                setTaskDirty(true);
                setParametersDirty(true);
                setForm((current) => ({
                  ...current,
                  sourceSchema: value,
                  sourceTable: "",
                }));
              }}
              onTableChange={(table) => {
                setTaskDirty(true);
                setParametersDirty(true);
                setForm((current) => ({
                  ...current,
                  sourceDatabase: current.sourceDatabase || table.database || "",
                  sourceSchema: current.sourceSchema || table.schema || "",
                  sourceTable: table.name,
                }));
              }}
            >
              {usesChangelog ? (
                <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
              ) : null}
            </TableSection>
          </CollapseSection>

          <CollapseSection id="target" title="目标表映射">
            <TableSection
              dataSourceId={form.targetDataSourceId}
              boundSchema={selectedTargetDataSource?.schema}
              database={form.targetDatabase}
              schema={form.targetSchema}
              table={form.targetTable}
              catalog={targetCatalog}
              onSchemaChange={(value) => {
                setTaskDirty(true);
                setParametersDirty(true);
                setForm((current) => ({ ...current, targetSchema: value, targetTable: "" }));
              }}
              onTableChange={(table) => {
                setTaskDirty(true);
                setParametersDirty(true);
                setForm((current) => ({
                  ...current,
                  targetDatabase: current.targetDatabase || table.database || "",
                  targetSchema: current.targetSchema || table.schema || "",
                  targetTable: table.name,
                }));
              }}
            >
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel required>写入方式</FieldLabel>
                <DataSyncSearchableSelect
                  placeholder="请选择写入方式"
                  value={form.writeMode}
                  disabled={!combination || combination.writeModes.length === 1}
                  options={(combination?.writeModes || []).map((mode) => ({
                    value: mode,
                    label: mode === "UPSERT" ? "按主键更新" : "追加写入",
                  }))}
                  onValueChange={(value) => patch("writeMode", value as DataSyncWriteMode)}
                />
              </Field>
            </TableSection>
          </CollapseSection>
          {supportsScheduling ? (
            <CollapseSection id="schedule" title="调度配置">
              <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
                {scheduleError ? (
                  <Alert>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span>{scheduleError}</span>
                      {scheduleNeedsReload ? (
                        <Button
                          size="small"
                          disabled={saving || scheduleLoading}
                          onClick={() => {
                            scheduleDraft.current = scheduleForm;
                            setScheduleReload((value) => value + 1);
                          }}
                        >
                          重新加载状态
                        </Button>
                      ) : null}
                    </div>
                  </Alert>
                ) : null}
                {scheduleLoading ? (
                  <div className="text-xs text-[#667085]">加载调度…</div>
                ) : (
                  <ScheduleForm
                    value={scheduleForm}
                    onChange={(value) => {
                      setScheduleForm(value);
                      scheduleDraft.current = value;
                      setScheduleError("");
                    }}
                    disabled={saving || scheduleNeedsReload || Boolean(schedule?.enabled)}
                    allowClear={!schedule}
                  />
                )}
                {schedule?.enabled ? (
                  <div className="text-xs text-[#667085]">请先在任务列表停止调度再修改配置</div>
                ) : null}
              </div>
            </CollapseSection>
          ) : null}
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
