import { Alert, Button } from "@yak-ops/yak-ui";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { taskDraftKey } from "@/service/data-sync/draft";
import { offlineIncremental } from "@/service/data-sync/parameters";
import type {
  DataSyncDraft,
  DataSyncTableMapping,
  DataSyncTaskSavePayload,
} from "@/service/data-sync";
import type { ScheduleTimeRule } from "@/service/schedule";
import { findStepCombination } from "../solution/presentation";
import { useSolutions } from "../solution/use-solutions";
import { EditorAnchorStepper, type EditorAnchorItem } from "./anchor-stepper";
import { DataSourceSection } from "./data-source-section";
import { SingleTableBatchBody } from "./single-table-body";
import { DatabaseReplicationBody } from "./database-replication-body";
import { transformTableSelector } from "@/service/data-sync/transforms";
import { createEditorForm } from "./form-state";
import { EMPTY_INCREMENTAL } from "./incremental-fields";
import { replicationConfigurationError } from "./replication-validation";
import { selectSourceSchema, selectSourceTable } from "./source-selection";
import { tableKey } from "./table-catalog";
import { buildEditorPayload, editorValidationIssue, type EditorIssue } from "./definition";
import { useCatalogOptions } from "./use-catalog-options";
import { useEditorDataSources } from "./use-editor-data-sources";
import { useEditorDraft } from "./use-editor-draft";
import { useEditorHandoff } from "./use-editor-handoff";
import { useEditorSchedule } from "./use-editor-schedule";
import { useEditorSubmission } from "./use-editor-submission";
import { useReplicationMetadata } from "./use-replication-metadata";
import { type TaskEditorInput, type EditorForm, type EditorSource, type EditorSink } from "./types";

import { PropertiesDrawer } from "./properties-drawer";
import { AdvancedDrawer, type AdvancedTab, type AdvancedParameterTab } from "./advanced-drawer";
import { VersionsDrawer } from "./versions-drawer";
import { parameterFingerprint } from "@/service/data-sync/versions";
import { type DataSourceRecord } from "@/service/datasource";
import { EMPTY_SCHEDULE, sameScheduleRule } from "@/app/schedule/schedule-presentation";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import type { EditorConfiguration } from "./configuration";
import { useDefinitionRestoration, type EditorConfirmation } from "./use-definition-restoration";
import { EditorHeader, EditorFooter } from "./editor-chrome";
import { EditorConfirmationDialog, EditorRecoveryNotice } from "./editor-feedback";

type OpenDrawer =
  | { kind: "properties" | "versions" }
  | { kind: "advanced"; tab?: AdvancedTab; parameterTab?: AdvancedParameterTab };
const parameterPart = (value: DataSyncTaskSavePayload) =>
  Object.fromEntries(
    Object.entries(value).filter(([key]) => !["name", "remark", "debug"].includes(key)),
  );

export function TaskEditor({
  draft: initialDraft,
  task: initialTask,
  connections = [],
}: TaskEditorInput) {
  const [draft, setDraft] = useState<DataSyncDraft>(initialDraft);
  const replication = draft.productType === "DATABASE_REPLICATION";
  const [mappings, setMappings] = useState<DataSyncTableMapping[]>(
    initialTask?.tableMappings || draft.form?.tableMappings || [],
  );
  const [pendingChange, setPendingChange] = useState<EditorConfirmation>();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const pendingSchedule = useEditorHandoff(id);
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const draftKey = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const {
    choices,
    loading: solutionsLoading,
    error: solutionsError,
    refresh: reloadSolutions,
  } = useSolutions(draft.sourceType, draft.targetType);
  const solution = choices.find(
    (value) =>
      value.productType === draft.productType &&
      value.sourceConnector === draft.sourceConnector &&
      value.sinkConnector === draft.sinkConnector,
  );
  const combination = findStepCombination(solution, draft.steps);
  const supportsScheduling = Boolean(combination?.supportsScheduling);
  const prepareSchema = draft.steps.includes("SCHEMA");
  const useIncremental = offlineIncremental(draft.productType, draft.steps);
  const initialLoad = draft.steps.includes("FULL");
  const continuous = replication && draft.steps.includes("INCREMENTAL");
  const [form, setForm] = useState<EditorForm>(() => createEditorForm(draft, initialTask));
  const savedTask = useRef(initialTask);
  const [saved, setSaved] = useState(initialTask);
  const [baseline, setBaseline] = useState(() =>
    buildEditorPayload(form, draft, replication, mappings),
  );
  const [drawer, setDrawer] = useState<OpenDrawer | undefined>(() =>
    pendingSchedule?.scheduleSaveError
      ? { kind: "advanced", tab: "parameters", parameterTab: "runtime" }
      : undefined,
  );
  const [drawerDirty, setDrawerDirty] = useState(false);
  const [pageError, setPageError] = useState("");
  const [completedTaskId, setCompletedTaskId] = useState<string>();
  const [restoredConnections, setRestoredConnections] = useState<DataSourceRecord[]>([]);
  const { dataSources, dataSourcesLoading, dataSourcesError, loadDataSources } =
    useEditorDataSources([...connections, ...restoredConnections]);
  const {
    schedule,
    scheduleForm,
    scheduleLoading,
    scheduleError,
    scheduleNeedsReload,
    scheduleDraft,
    setSchedule,
    setScheduleForm,
    setScheduleError,
    setScheduleNeedsReload,
    reloadSchedule,
    changeSchedule,
  } = useEditorSchedule(id, pendingSchedule);
  const editorScrollRef = useRef<HTMLElement | null>(null);
  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "datasource", label: "数据源" },
      { id: "source", label: "数据来源" },
      { id: "target", label: replication ? "目标表映射" : "数据去向" },
      ...(!replication ? [{ id: "field-mapping", label: "去向字段映射" }] : []),
    ],
    [replication],
  );
  const sourceCatalog = useCatalogOptions(
    form.source.dataSourceId,
    form.source.database,
    form.source.schema,
    !replication,
  );
  const targetCatalog = useCatalogOptions(
    form.sink.dataSourceId,
    form.sink.database,
    form.sink.schema,
  );
  const replicationMetadata = useReplicationMetadata(
    form.source.dataSourceId,
    mappings,
    replication,
  );
  const replicationError = replication
    ? replicationConfigurationError(
        mappings,
        replicationMetadata,
        initialLoad,
        continuous,
        form.sink.schemaChangesEnabled,
        draft.targetType,
      )
    : "";
  const sourceDataSources = dataSources.filter((item) => item.dbType === draft.sourceType);
  const targetDataSources = dataSources.filter((item) => item.dbType === draft.targetType);
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.source.dataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.sink.dataSourceId);
  useEditorDraft(id, draftKey, draft, form, replication, mappings, savedTask);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) =>
    setForm((current) => ({ ...current, [key]: value }));
  const patchSource = <K extends keyof EditorSource>(key: K, value: EditorSource[K]) =>
    setForm((current) => ({ ...current, source: { ...current.source, [key]: value } }));
  const patchSink = <K extends keyof EditorSink>(key: K, value: EditorSink[K]) =>
    setForm((current) => ({ ...current, sink: { ...current.sink, [key]: value } }));
  const updateMappings = (value: DataSyncTableMapping[]) => {
    const keys = new Set(value.map((mapping) => tableKey(mapping.source)));
    const removed = new Set(
      mappings
        .filter((mapping) => !keys.has(tableKey(mapping.source)))
        .map((mapping) => transformTableSelector(mapping.source)),
    );
    setMappings(value);
    if (removed.size)
      setForm((current) => ({
        ...current,
        transforms: current.transforms.filter((rule) => !removed.has(rule["source-table"])),
      }));
  };
  const requestSourceChange = (update: (current: EditorForm) => EditorForm) => {
    const apply = () => {
      setForm((current) =>
        update({
          ...current,
          transforms: [],
          source: {
            ...current.source,
            where: "",
            splitKey: "",
            incremental: useIncremental ? EMPTY_INCREMENTAL : undefined,
          },
        }),
      );
    };
    if (
      form.transforms.length ||
      form.source.incremental?.column ||
      form.source.where ||
      form.source.splitKey
    )
      setPendingChange({
        title: "更换来源表",
        message: "更换来源表将清除字段映射、数据过滤、切分键和增量字段设置。",
        apply,
      });
    else apply();
  };
  const requestTargetChange = (apply: () => void) => {
    if (form.transforms.length)
      setPendingChange({
        title: "更换去向表",
        message: "字段映射和转换表达式会保留，请根据新目标表重新核对输出字段。",
        apply,
      });
    else apply();
  };
  const requestConnectionChange = (side: "source" | "target", value: string) => {
    if (value === (side === "source" ? form.source.dataSourceId : form.sink.dataSourceId)) return;
    const apply = () => {
      const selected = dataSources.find((item) => item.id === value);
      setMappings([]);
      setForm((current) => ({
        ...current,
        transforms: [],
        selection: undefined,
        mappingTemplate: undefined,
        selectionResolved: true,
        typeMappings: undefined,
        startup: side === "source" ? undefined : current.startup,
        ...(side === "source"
          ? {
              source: {
                ...current.source,
                dataSourceId: value,
                database: selected?.database || "",
                schema: selected?.schema || "",
                table: "",
                where: "",
                splitKey: "",
                incremental: useIncremental ? EMPTY_INCREMENTAL : undefined,
              },
            }
          : {
              sink: {
                ...current.sink,
                dataSourceId: value,
                database: selected?.database || "",
                schema: selected?.schema || "",
                table: "",
                targetPrimaryKeys: [],
                preSql: [],
                postSql: [],
              },
            }),
      }));
    };
    if (mappings.length || form.transforms.length || form.source.table || form.sink.table)
      setPendingChange({
        title: "更换数据源",
        message: "更换数据源将清除已选表映射、字段映射和转换表达式。",
        apply,
      });
    else apply();
  };
  const singleColumns =
    mappings.length === 1
      ? replicationMetadata.columns.get(tableKey(mappings[0].source))
      : undefined;
  const singleKeyless = Boolean(
    singleColumns?.length && !singleColumns.some((column) => column.primaryKey === true),
  );
  const writeModes = (combination?.writeModes ?? []).filter(
    (mode) =>
      mode !== "RETRACT" ||
      (mappings.length === 1 && (singleKeyless || mappings[0].writeMode === "RETRACT")),
  );
  const validationIssue = editorValidationIssue({
    combination,
    capabilities: solution?.capabilities,
    solutionsLoading,
    solutionsError,
    useIncremental,
    initialLoad,
    replication,
    mappings,
    selectedSourceDataSource,
    selectedTargetDataSource,
    draft,
    replicationError,
    supportsScheduling,
    scheduleLoading,
    scheduleNeedsReload,
    schedule,
    scheduleForm,
    form,
    prepareSchema,
    targetCatalog,
    sourceCatalog,
  });
  const payload = buildEditorPayload(form, draft, replication, mappings);
  const taskDirty = !saved || parameterFingerprint(payload) !== parameterFingerprint(baseline);
  const parametersDirty =
    !saved ||
    parameterFingerprint(parameterPart(payload)) !== parameterFingerprint(parameterPart(baseline));
  const scheduleDirty = !sameScheduleRule(scheduleForm, schedule || EMPTY_SCHEDULE);
  const closeDrawer = () => {
    setDrawer(undefined);
    setDrawerDirty(false);
  };
  const applyConfiguration = (value: EditorConfiguration, rule?: ScheduleTimeRule) => {
    setDraft(value.draft);
    setForm(value.form);
    setMappings(value.mappings);
    if (rule) changeSchedule(rule);
    closeDrawer();
    setPageError("");
  };
  const locateIssue = (issue: EditorIssue) => {
    setPageError(issue.message);
    if (issue.location === "properties") setDrawer({ kind: "properties" });
    else if (issue.location === "advanced")
      setDrawer({ kind: "advanced", tab: issue.tab, parameterTab: issue.parameterTab });
    else
      requestAnimationFrame(() => {
        const section = document.getElementById(issue.location);
        section?.scrollIntoView({ block: "center", behavior: "auto" });
        section?.querySelector<HTMLElement>("input,button,textarea")?.focus();
      });
  };
  const submission = useEditorSubmission({
    draftKey,
    recovery: pendingSchedule?.writeRecovery,
    scope: { userId: currentUser!.id, workspaceId: currentWorkspace!.id },
    taskDirty,
    parametersDirty,
    supportsScheduling,
    schedule,
    scheduleForm,
    savedTask,
    scheduleDraft,
    payload: () => payload,
    onPersisted: (task, request) => {
      setSaved(task);
      if (!id) setCompletedTaskId(task.id);
      if (request) setBaseline(request);
    },
    onComplete: (task) => {
      if (!id) setCompletedTaskId(task.id);
    },
    onScheduleFailure: () =>
      setDrawer({ kind: "advanced", tab: "parameters", parameterTab: "runtime" }),
    setSchedule,
    setScheduleForm,
    setScheduleError,
    setScheduleNeedsReload,
  });
  const { saving } = submission;
  const locked = saving || Boolean(submission.pendingWrite || submission.retryPublication);
  const transferringSavedTask = Boolean(completedTaskId && !id && !saving && !taskDirty);
  useUnsavedChanges((taskDirty || scheduleDirty || drawerDirty) && !transferringSavedTask);
  useEffect(() => {
    if (!transferringSavedTask) return;
    navigate(`/data-sync/${completedTaskId}/edit`, {
      replace: true,
      state: {
        ...(scheduleDirty ? { scheduleDraft: scheduleForm } : {}),
        ...(scheduleError ? { scheduleSaveError: scheduleError } : {}),
        writeRecovery: {
          error: submission.error,
          pendingWrite: submission.pendingWrite,
          retryPublication: submission.retryPublication,
        },
      },
    });
  }, [
    transferringSavedTask,
    completedTaskId,
    scheduleDirty,
    scheduleForm,
    scheduleError,
    submission.error,
    submission.pendingWrite,
    submission.retryPublication,
    navigate,
  ]);
  const submit = (publish = false) => {
    if (validationIssue && !submission.retryPublication) {
      locateIssue(validationIssue);
      return;
    }
    setPageError("");
    void submission.save(publish);
  };
  const {
    restore,
    reloadRemote,
    cancel: cancelRestoration,
  } = useDefinitionRestoration({
    task: saved,
    form,
    closeDrawer,
    confirm: setPendingChange,
    apply: (value, connections) => {
      setRestoredConnections(connections);
      applyConfiguration(value);
    },
    replaceRemote: (remote, value, connections) => {
      savedTask.current = remote;
      setSaved(remote);
      setRestoredConnections(connections);
      setBaseline(
        buildEditorPayload(
          value.form,
          value.draft,
          value.draft.productType === "DATABASE_REPLICATION",
          value.mappings,
        ),
      );
      applyConfiguration(value);
      submission.clearRecovery();
    },
  });
  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <EditorHeader
        name={form.name}
        locked={locked}
        saving={saving}
        onBack={() => navigate("/data-sync")}
        onProperties={() => setDrawer({ kind: "properties" })}
        onAdvanced={() => setDrawer({ kind: "advanced" })}
        onVersions={() => setDrawer({ kind: "versions" })}
      />
      <div className="flex min-h-0 flex-1 gap-4 px-4">
        <main
          ref={editorScrollRef}
          aria-busy={saving}
          className="min-h-0 min-w-0 flex-1 space-y-3 overflow-y-auto pb-6 pt-4"
        >
          {pageError ? <Alert>{pageError}</Alert> : null}
          {submission.error ? (
            <EditorRecoveryNotice
              error={submission.error}
              saving={saving}
              canReconcile={Boolean(
                submission.pendingWrite && submission.pendingWrite.kind !== "create",
              )}
              canRetry={Boolean(submission.retryPublication)}
              hasTask={Boolean(saved)}
              onReconcile={() => void submission.reconcile()}
              onRetry={() => void submission.save(true)}
              onReload={() => void reloadRemote()}
              onBack={() => navigate("/data-sync")}
            />
          ) : null}
          <fieldset disabled={locked} className="min-w-0 space-y-3">
            {solutionsError ? (
              <Alert>
                {solutionsError}
                <Button size="small" onClick={reloadSolutions}>
                  重试
                </Button>
              </Alert>
            ) : null}
            {dataSourcesError ? (
              <Alert>
                {dataSourcesError}
                <Button size="small" onClick={loadDataSources}>
                  重试
                </Button>
              </Alert>
            ) : null}
            {!solutionsLoading && !solutionsError && !combination ? (
              <Alert>当前链路不再支持所选同步方案</Alert>
            ) : null}
            <DataSourceSection
              id={id}
              sourceType={draft.sourceType}
              targetType={draft.targetType}
              sourceDataSources={sourceDataSources}
              targetDataSources={targetDataSources}
              sourceDataSourceId={form.source.dataSourceId}
              targetDataSourceId={form.sink.dataSourceId}
              loading={dataSourcesLoading}
              onRefresh={loadDataSources}
              onSourceChange={(value) => requestConnectionChange("source", value)}
              onTargetChange={(value) => requestConnectionChange("target", value)}
            />
            {replication ? (
              <DatabaseReplicationBody
                selection={{
                  form,
                  mappings,
                  targetTables: targetCatalog.tables,
                  capabilities: solution?.capabilities,
                  prepareSchema,
                  initialLoad,
                  continuous,
                  writeModes,
                  onMappingsChange: updateMappings,
                  onRulesChange: (selection, mappingTemplate, selectionResolved) =>
                    setForm((current) => ({
                      ...current,
                      selection,
                      mappingTemplate,
                      selectionResolved,
                    })),
                  confirm: setPendingChange,
                }}
                mapping={{
                  mappings,
                  baseline: saved?.tableMappings || [],
                  targetCatalog,
                  capabilities: solution?.capabilities,
                  writeModes,
                  initialLoad,
                  prepareSchema,
                  schemaChanges: form.sink.schemaChangesEnabled,
                  regex: Boolean(form.selection),
                  transforms: form.transforms,
                  typeMappings: form.typeMappings,
                  onChange: updateMappings,
                  onTransformsChange: (value) => patch("transforms", value),
                  onTypeMappingsChange: (value) => patch("typeMappings", value),
                  confirm: setPendingChange,
                }}
              />
            ) : (
              <SingleTableBatchBody
                form={form}
                capabilities={solution?.capabilities}
                writeModes={combination?.writeModes || []}
                useIncremental={useIncremental}
                initialLoad={initialLoad}
                patchSource={patchSource}
                patchSink={patchSink}
                onTransformsChange={(value) => patch("transforms", value)}
                onTypeMappingsChange={(value) => patch("typeMappings", value)}
                source={{
                  side: "source",
                  dataSourceId: form.source.dataSourceId,
                  boundSchema: selectedSourceDataSource?.schema,
                  database: form.source.database,
                  schema: form.source.schema,
                  table: form.source.table,
                  catalog: sourceCatalog,
                  onSchemaChange: (schema) =>
                    requestSourceChange((current) => selectSourceSchema(current, schema)),
                  onTableChange: (table) =>
                    requestSourceChange((current) => selectSourceTable(current, table)),
                }}
                target={{
                  side: "target",
                  prepareSchema,
                  dataSourceId: form.sink.dataSourceId,
                  boundSchema: selectedTargetDataSource?.schema,
                  database: form.sink.database,
                  schema: form.sink.schema,
                  table: form.sink.table,
                  catalog: targetCatalog,
                  onTableNameChange: (value) => patchSink("table", value),
                  onSchemaChange: (schema) =>
                    requestTargetChange(() =>
                      setForm((current) => ({
                        ...current,
                        sink: { ...current.sink, schema, table: "" },
                      })),
                    ),
                  onTableChange: (table) =>
                    requestTargetChange(() =>
                      setForm((current) => ({
                        ...current,
                        sink: {
                          ...current.sink,
                          database: table.database || current.sink.database,
                          schema: table.schema || current.sink.schema,
                          table: table.name,
                        },
                      })),
                    ),
                }}
              />
            )}
            {replicationError && mappings.length ? <Alert>{replicationError}</Alert> : null}
          </fieldset>
        </main>
        <aside className="hidden h-fit w-32 shrink-0 self-start pt-4 lg:block">
          <EditorAnchorStepper items={anchorItems} scrollRootRef={editorScrollRef} />
        </aside>
      </div>
      <EditorFooter
        task={saved}
        dirty={taskDirty || scheduleDirty}
        locked={locked}
        saving={saving}
        onSave={() => submit()}
        onPublish={() => submit(true)}
      />
      {drawer?.kind === "properties" ? (
        <PropertiesDrawer
          id={saved?.id}
          draft={draft}
          value={form}
          onApply={(value) => {
            setForm((current) => ({ ...current, ...value }));
            closeDrawer();
          }}
          onClose={closeDrawer}
          onDirtyChange={setDrawerDirty}
        />
      ) : null}
      {drawer?.kind === "advanced" ? (
        <AdvancedDrawer
          value={{ draft, form, mappings }}
          solution={solution}
          strategyDisabled={Boolean(scheduleNeedsReload || scheduleLoading)}
          initialTab={drawer.tab}
          initialParameterTab={drawer.parameterTab}
          schedule={
            supportsScheduling || schedule
              ? {
                  schedule,
                  scheduleForm,
                  scheduleError,
                  scheduleNeedsReload,
                  scheduleLoading,
                  saving,
                  onReload: () => void reloadSchedule(),
                  onChange: changeSchedule,
                }
              : undefined
          }
          onApply={applyConfiguration}
          onClose={closeDrawer}
          onDirtyChange={setDrawerDirty}
        />
      ) : null}
      {drawer?.kind === "versions" ? (
        <VersionsDrawer
          task={saved}
          allowRestore={!locked}
          onRestore={(value) => void restore(value)}
          onClose={() => {
            cancelRestoration();
            closeDrawer();
          }}
        />
      ) : null}
      {pendingChange ? (
        <EditorConfirmationDialog
          value={pendingChange}
          onClose={() => setPendingChange(undefined)}
        />
      ) : null}
    </div>
  );
}
