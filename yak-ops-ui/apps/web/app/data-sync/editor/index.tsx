import { Alert, Button, Modal, PageHeader } from "@yak-ops/yak-ui";
import { useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { taskDraftKey } from "@/service/data-sync/draft";
import { offlineIncremental } from "@/service/data-sync/parameters";
import type {
  DataSyncDraft,
  DataSyncStep,
  DataSyncTableMapping,
  DataSyncTaskStatus,
} from "@/service/data-sync";
import type { ScheduleTimeRule } from "@/service/schedule";
import { findStepCombination } from "../solution/presentation";
import { useSolutions } from "../solution/use-solutions";
import { EditorAnchorStepper, type EditorAnchorItem } from "./anchor-stepper";
import { BasicSection } from "./basic-section";
import { DataSourceSection } from "./data-source-section";
import { FieldMappingSection } from "./field-mapping-section";
import { ExistingTransformRules } from "./existing-transform-rules";
import { createEditorForm } from "./form-state";
import { EMPTY_INCREMENTAL } from "./incremental-fields";
import { replicationConfigurationError } from "./replication-options";
import { RunConfigurationSection } from "./run-configuration-section";
import { SourceSection } from "./source-section";
import { selectSourceSchema, selectSourceTable } from "./source-selection";
import { StrategySection } from "./strategy-section";
import { tableKey } from "./table-mapping-editor";
import { selectedTableKey } from "./table-section";
import { TargetSection } from "./target-section";
import { buildEditorPayload, canSaveEditor } from "./definition";
import { useCatalogOptions } from "./use-catalog-options";
import { useEditorDataSources } from "./use-editor-data-sources";
import { useEditorDraft } from "./use-editor-draft";
import { useEditorSchedule } from "./use-editor-schedule";
import { useEditorSubmission } from "./use-editor-submission";
import { useReplicationMetadata } from "./use-replication-metadata";
import { type TaskEditorInput, type EditorForm, type EditorSource, type EditorSink } from "./types";

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
  const [pendingChange, setPendingChange] = useState<{
    title: string;
    message: string;
    apply: () => void;
  }>();
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
  const [taskStatus] = useState<DataSyncTaskStatus>(
    initialTask?.status === "PUBLISHED" ? "PUBLISHED" : "UNPUBLISHED",
  );
  const [parametersDirty, setParametersDirty] = useState(false);
  const [taskDirty, setTaskDirty] = useState(!editing);
  const savedTask = useRef(initialTask);
  const { dataSources, dataSourcesLoading, dataSourcesError, loadDataSources } =
    useEditorDataSources(connections);
  const pendingSchedule = location.state as {
    scheduleDraft?: ScheduleTimeRule;
    scheduleSaveError?: string;
  } | null;
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
  } = useEditorSchedule(id, supportsScheduling, initialTask, pendingSchedule);
  const hasAdvancedTransforms = replication && form.transforms.length > 0;
  const editorScrollRef = useRef<HTMLElement | null>(null);
  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "basic", label: "基本信息" },
      { id: "datasource", label: "数据源" },
      { id: "strategy", label: "同步策略" },
      { id: "source", label: "数据来源" },
      { id: "target", label: "数据去向" },
      ...(!replication ? [{ id: "field-mapping", label: "字段加工" }] : []),
      ...(hasAdvancedTransforms ? [{ id: "advanced-transforms", label: "高级转换规则" }] : []),
      { id: "env", label: "运行配置" },
    ],
    [replication, hasAdvancedTransforms],
  );
  const sourceCatalog = useCatalogOptions(
    form.source.dataSourceId,
    form.source.database,
    form.source.schema,
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

  const markParametersDirty = () => {
    setTaskDirty(true);
    setParametersDirty(true);
  };
  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => {
    setTaskDirty(true);
    if (key !== "name" && key !== "remark" && key !== "debug") setParametersDirty(true);
    setForm((current) => ({ ...current, [key]: value }));
  };
  const patchSource = <K extends keyof EditorSource>(key: K, value: EditorSource[K]) => {
    markParametersDirty();
    setForm((current) => ({ ...current, source: { ...current.source, [key]: value } }));
  };
  const patchSink = <K extends keyof EditorSink>(key: K, value: EditorSink[K]) => {
    markParametersDirty();
    setForm((current) => ({ ...current, sink: { ...current.sink, [key]: value } }));
  };
  const updateMappings = (value: DataSyncTableMapping[]) => {
    setMappings(value);
    markParametersDirty();
  };
  const requestSourceChange = (update: (current: EditorForm) => EditorForm) => {
    const apply = () => {
      markParametersDirty();
      setForm((current) =>
        update({
          ...current,
          transforms: [],
          source: {
            ...current.source,
            incremental: useIncremental ? EMPTY_INCREMENTAL : undefined,
          },
        }),
      );
    };
    if (form.transforms.length || form.source.incremental?.column)
      setPendingChange({
        title: "更换来源表",
        message: "更换来源表将清除字段映射、转换表达式和增量字段设置。",
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
      markParametersDirty();
      setForm((current) => ({
        ...current,
        transforms: [],
        startup: side === "source" ? undefined : current.startup,
        ...(side === "source"
          ? {
              source: {
                ...current.source,
                dataSourceId: value,
                database: selected?.database || "",
                schema: selected?.schema || "",
                table: "",
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
  const changeSteps = (steps: DataSyncStep[]) => {
    const currentSteps: DataSyncStep[] = draft.steps.filter((step) => step !== "SCHEMA");
    if (currentSteps.length === steps.length && steps.every((step) => currentSteps.includes(step)))
      return;
    const nextSteps: DataSyncStep[] =
      prepareSchema && steps.includes("FULL") ? ["SCHEMA", ...steps] : steps;
    const next = findStepCombination(solution, nextSteps);
    if (!next) return;
    markParametersDirty();
    setDraft((current) => ({ ...current, steps: nextSteps }));
    const nextContinuous = replication && steps.includes("INCREMENTAL");
    setForm((current) => ({
      ...current,
      startup: undefined,
      source: {
        ...current.source,
        incremental: offlineIncremental(draft.productType, steps)
          ? current.source.incremental || EMPTY_INCREMENTAL
          : undefined,
      },
      sink: {
        ...current.sink,
        writeMode: next.writeModes.includes(current.sink.writeMode)
          ? current.sink.writeMode
          : next.writeModes[0],
        schemaChangesEnabled: nextContinuous && current.sink.schemaChangesEnabled,
        allowDropColumns: nextContinuous && current.sink.allowDropColumns,
      },
    }));
    setMappings((current) =>
      current.map((mapping) => ({
        ...mapping,
        source: steps.includes("FULL")
          ? mapping.source
          : { ...mapping.source, chunkKeyColumn: undefined },
        writeMode: next.writeModes.includes(mapping.writeMode)
          ? mapping.writeMode
          : next.writeModes[0],
      })),
    );
  };
  const changePrepareSchema = (enabled: boolean) => {
    if (enabled === prepareSchema) return;
    const steps: DataSyncStep[] = draft.steps.filter((step) => step !== "SCHEMA");
    if (enabled) steps.unshift("SCHEMA");
    if (!findStepCombination(solution, steps)) return;
    markParametersDirty();
    setDraft((current) => ({ ...current, steps }));
    if (enabled)
      setMappings((current) =>
        current.map((mapping) => ({
          ...mapping,
          target: {
            ...mapping.target,
            database: mapping.target.database || form.sink.database || undefined,
            schema: mapping.target.schema || form.sink.schema || undefined,
            table: mapping.target.table || mapping.source.table,
          },
        })),
      );
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
  const published = editing && taskStatus === "PUBLISHED";
  const canSave = canSaveEditor({
    published,
    combination,
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
  const { save, saving } = useEditorSubmission({
    id,
    editing,
    draftKey,
    canSave,
    taskDirty,
    parametersDirty,
    supportsScheduling,
    schedule,
    scheduleForm,
    savedTask,
    scheduleDraft,
    payload: () => buildEditorPayload(form, draft, replication, mappings),
    setTaskDirty,
    setParametersDirty,
    setSchedule,
    setScheduleForm,
    setScheduleError,
    setScheduleNeedsReload,
    navigate,
  });
  const pageTitle = editing ? form.name || "编辑同步任务" : "新建同步任务";
  if (published)
    return (
      <div className="min-h-full bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader
          title={pageTitle}
          bordered
          className="bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
          extra={
            <Button size="small" onClick={() => navigate("/data-sync")}>
              返回任务列表
            </Button>
          }
        />
        <div className="px-6 pt-5 max-md:px-4">
          <Alert>任务已上线，当前不可编辑。请先在任务列表下线，再修改任务定义。</Alert>
        </div>
      </div>
    );
  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title={pageTitle}
        bordered
        className="shrink-0 bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
        extra={
          <>
            <Button size="small" disabled={saving} onClick={() => navigate("/data-sync")}>
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
      <div className="flex min-h-0 flex-1 gap-5 px-6 max-md:px-4">
        <main
          ref={editorScrollRef}
          aria-busy={saving}
          className="min-h-0 min-w-0 flex-1 space-y-4 overflow-y-auto pb-8 pt-5"
        >
          <fieldset disabled={saving} className="contents">
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
            <BasicSection draft={draft} form={form} patch={patch} />
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
            <StrategySection
              draft={draft}
              solution={solution}
              disabled={Boolean(
                schedule?.enabled ||
                scheduleNeedsReload ||
                scheduleLoading ||
                mappings.some((mapping) => mapping.writeMode === "RETRACT"),
              )}
              startupMode={form.startup?.mode}
              onChange={changeSteps}
            />
            <SourceSection
              replication={replication}
              prepareSchema={prepareSchema}
              form={form}
              sourceCatalog={sourceCatalog}
              targetCatalog={targetCatalog}
              selectedSourceDataSource={selectedSourceDataSource}
              mappings={mappings}
              updateMappings={updateMappings}
              writeMode={combination?.writeModes[0]}
              useIncremental={useIncremental}
              initialLoad={initialLoad}
              patchSource={patchSource}
              onSourceSchemaChange={(schema) =>
                requestSourceChange((current) => selectSourceSchema(current, schema))
              }
              onSourceTableChange={(table) =>
                requestSourceChange((current) => selectSourceTable(current, table))
              }
            />
            <TargetSection
              replication={replication}
              prepareSchema={prepareSchema}
              canPrepareSchema={Boolean(initialLoad && solution?.steps.includes("SCHEMA"))}
              onPrepareSchemaChange={changePrepareSchema}
              form={form}
              targetCatalog={targetCatalog}
              selectedTargetDataSource={selectedTargetDataSource}
              mappings={mappings}
              updateMappings={updateMappings}
              writeModes={writeModes}
              replicationMetadata={replicationMetadata}
              continuous={continuous}
              initialLoad={initialLoad}
              targetType={draft.targetType}
              saving={saving}
              patchSink={patchSink}
              setTaskDirty={setTaskDirty}
              setParametersDirty={setParametersDirty}
              setForm={setForm}
              requestTargetChange={requestTargetChange}
            />
            {replicationError && mappings.length ? <Alert>{replicationError}</Alert> : null}
            {!replication ? (
              <FieldMappingSection
                source={form.source}
                target={form.sink}
                targetTableExists={Boolean(
                  selectedTableKey(
                    targetCatalog.tables,
                    form.sink.database,
                    form.sink.schema,
                    form.sink.table,
                  ),
                )}
                value={form.transforms}
                onChange={(value) => patch("transforms", value)}
                disabled={saving}
              />
            ) : null}
            {hasAdvancedTransforms ? (
              <ExistingTransformRules
                value={form.transforms}
                disabled={saving}
                onChange={(value) => patch("transforms", value)}
              />
            ) : null}
            <RunConfigurationSection
              replication={replication}
              continuous={continuous}
              debug={form.debug}
              onDebugChange={(value) => patch("debug", value)}
              schedule={
                supportsScheduling
                  ? {
                      schedule,
                      scheduleForm,
                      scheduleError,
                      scheduleNeedsReload,
                      scheduleLoading,
                      saving,
                      onReload: reloadSchedule,
                      onChange: changeSchedule,
                    }
                  : undefined
              }
            />
          </fieldset>
        </main>
        <aside className="hidden h-fit w-44 shrink-0 self-start pt-5 lg:block">
          <EditorAnchorStepper items={anchorItems} scrollRootRef={editorScrollRef} />
        </aside>
      </div>
      {pendingChange ? (
        <Modal
          open
          centered
          width={440}
          title={pendingChange.title}
          onClose={() => setPendingChange(undefined)}
          footer={
            <>
              <Button size="small" onClick={() => setPendingChange(undefined)}>
                取消
              </Button>
              <Button
                size="small"
                variant="primary"
                onClick={() => {
                  pendingChange.apply();
                  setPendingChange(undefined);
                }}
              >
                确认
              </Button>
            </>
          }
        >
          <p className="text-sm">{pendingChange.message}</p>
        </Modal>
      ) : null}
    </div>
  );
}
