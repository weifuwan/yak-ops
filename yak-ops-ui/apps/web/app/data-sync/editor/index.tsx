import {
  Alert,
  Button,
  CollapseSection,
  Field,
  FieldLabel,
  Modal,
  PageHeader,
  Switch,
} from "@yak-ops/yak-ui";
import { useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { taskDraftKey } from "@/service/data-sync/draft";
import { offlineIncremental, supportsMultipleTables } from "@/service/data-sync/parameters";
import { EMPTY_INCREMENTAL } from "./incremental-fields";
import { selectSourceSchema, selectSourceTable, setSourcePrimaryKeys } from "./source-selection";
import { findStepCombination } from "../solution/presentation";
import { useSolutions } from "../solution/use-solutions";
import { EditorAnchorStepper, type EditorAnchorItem } from "./anchor-stepper";
import type { ScheduleTimeRule } from "@/service/schedule";
import type { DataSyncTableMapping, DataSyncTaskStatus } from "@/service/data-sync";
import { BasicSection } from "./basic-section";
import { DataSourceSection } from "./data-source-section";
import { SourceSection } from "./source-section";
import { TargetSection } from "./target-section";
import { EditorScheduleSection } from "./schedule-section";
import { useCatalogOptions } from "./use-catalog-options";
import { buildEditorPayload, canSaveEditor } from "./definition";
import { useEditorSubmission } from "./use-editor-submission";
import { useEditorDataSources } from "./use-editor-data-sources";
import { useEditorSchedule } from "./use-editor-schedule";
import { useEditorDraft } from "./use-editor-draft";
import {
  EMPTY_FORM,
  type TaskEditorInput,
  type EditorForm,
  type EditorSource,
  type EditorSink,
} from "./types";

export function TaskEditor({ draft, task: initialTask, connections = [] }: TaskEditorInput) {
  const batch = supportsMultipleTables(draft.solutionType);
  const [mappings, setMappings] = useState<DataSyncTableMapping[]>(
    initialTask?.tableMappings || draft.form?.tableMappings || [],
  );
  const [pendingConnection, setPendingConnection] = useState<{
    side: "source" | "target";
    value: string;
  }>();
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
  const solution = choices.find(
    (value) =>
      value.solutionType === draft.solutionType &&
      value.sourceConnector === draft.sourceConnector &&
      value.sinkConnector === draft.sinkConnector,
  );
  const combination = findStepCombination(solution, draft.steps);
  const supportsScheduling = Boolean(combination?.supportsScheduling);
  const prepareSchema = draft.steps.includes("SCHEMA");
  const useIncremental = offlineIncremental(draft.solutionType, draft.steps);
  const initialLoad = draft.steps.includes("FULL");
  const usesChangelog = draft.sourceProducesChangelog;
  const continuous = usesChangelog && draft.steps.includes("INCREMENTAL");

  const [form, setForm] = useState<EditorForm>(() => {
    const value = initialTask || draft.form;
    return {
      ...EMPTY_FORM,
      name: draft.name,
      remark: value?.remark || "",
      debug: value?.debug ?? false,
      source: {
        dataSourceId: value?.sourceDataSourceId || "",
        database: value?.sourceDatabase || "",
        schema: value?.sourceSchema || "",
        table: value?.sourceTable || "",
        sourceHasPrimaryKey: undefined,
        chunkKeyColumn: value?.chunkKeyColumn ?? "",
        incremental: useIncremental ? value?.incremental || EMPTY_INCREMENTAL : undefined,
      },
      sink: {
        dataSourceId: value?.targetDataSourceId || "",
        database: value?.targetDatabase || "",
        schema: value?.targetSchema || "",
        table: value?.targetTable || "",
        schemaChangesEnabled:
          continuous && (value?.schemaChangesEnabled ?? draft.targetType !== "ORACLE"),
        allowDropColumns: continuous && (value?.allowDropColumns ?? false),
        writeMode:
          value?.writeMode === "RETRACT"
            ? "RETRACT"
            : value?.writeMode === "UPSERT" || continuous || useIncremental
              ? "UPSERT"
              : "APPEND",
        targetPrimaryKeys: value?.targetPrimaryKeys ?? [],
      },
    };
  });
  const [taskStatus] = useState<DataSyncTaskStatus>(
    initialTask?.status === "PUBLISHED" ? "PUBLISHED" : "UNPUBLISHED",
  );
  const [parametersDirty, setParametersDirty] = useState(false);
  const [taskDirty, setTaskDirty] = useState(!editing);
  const savedTask = useRef(initialTask);
  const { dataSources, dataSourcesLoading, loadDataSources } = useEditorDataSources(
    connections,
    savedTask,
  );
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

  const editorScrollRef = useRef<HTMLElement | null>(null);
  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "basic", label: "基本信息" },
      { id: "datasource", label: "数据源" },
      { id: "source", label: "数据来源" },
      { id: "target", label: "数据去向" },
      { id: "env", label: "运行配置" },
    ],
    [],
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

  const sourceDataSources = useMemo(
    () => dataSources.filter((item) => item.dbType === draft.sourceType),
    [dataSources, draft.sourceType],
  );
  const targetDataSources = useMemo(
    () => dataSources.filter((item) => item.dbType === draft.targetType),
    [dataSources, draft.targetType],
  );
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.source.dataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.sink.dataSourceId);
  useEditorDraft(id, draftKey, draft, form, batch, mappings, savedTask);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => {
    setTaskDirty(true);
    if (key !== "name" && key !== "remark" && key !== "debug") setParametersDirty(true);
    setForm((current) => ({ ...current, [key]: value }));
  };

  const patchSource = <K extends keyof EditorSource>(key: K, value: EditorSource[K]) => {
    setTaskDirty(true);
    setParametersDirty(true);
    setForm((current) => ({ ...current, source: { ...current.source, [key]: value } }));
  };

  const patchSink = <K extends keyof EditorSink>(key: K, value: EditorSink[K]) => {
    setTaskDirty(true);
    setParametersDirty(true);
    setForm((current) => ({ ...current, sink: { ...current.sink, [key]: value } }));
  };

  const updateSourceSelection = (update: (current: EditorForm) => EditorForm) => {
    setTaskDirty(true);
    setParametersDirty(true);
    setForm(update);
  };

  const updateMappings = (value: DataSyncTableMapping[]) => {
    setMappings(value);
    setTaskDirty(true);
    setParametersDirty(true);
  };
  const changeConnection = (side: "source" | "target", value: string) => {
    const selected = dataSources.find((item) => item.id === value);
    setMappings([]);
    setTaskDirty(true);
    setParametersDirty(true);
    if (side === "source") {
      setForm((current) => ({
        ...current,
        source: {
          ...current.source,
          dataSourceId: value,
          database: selected?.database || "",
          schema: selected?.schema || "",
          table: "",
          sourceHasPrimaryKey: undefined,
          chunkKeyColumn: "",
        },
        sink: {
          ...current.sink,
          targetPrimaryKeys: [],
          writeMode: continuous ? "UPSERT" : current.sink.writeMode,
        },
      }));
    } else {
      setForm((current) => ({
        ...current,
        sink: {
          ...current.sink,
          dataSourceId: value,
          database: selected?.database || "",
          schema: selected?.schema || "",
          table: "",
          targetPrimaryKeys: [],
        },
      }));
    }
  };
  const requestConnectionChange = (side: "source" | "target", value: string) => {
    if (batch && mappings.length) setPendingConnection({ side, value });
    else changeConnection(side, value);
  };

  const payload = () =>
    buildEditorPayload(
      form,
      draft,
      batch,
      mappings,
      usesChangelog,
      initialTask?.startup,
      initialTask?.transforms,
      initialTask?.pipelineOptions,
    );

  const published = editing && taskStatus === "PUBLISHED";
  const canSave = canSaveEditor({
    published,
    combination: combination ?? undefined,
    solutionsLoading,
    solutionsError,
    useIncremental,
    initialLoad,
    batch,
    mappings,
    selectedSourceDataSource,
    selectedTargetDataSource,
    draft,
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
    payload,
    setTaskDirty,
    setParametersDirty,
    setSchedule,
    setScheduleForm,
    setScheduleError,
    setScheduleNeedsReload,
    navigate,
  });

  const pageTitle = editing ? form.name || "编辑同步任务" : "新建同步任务";

  if (published) {
    return (
      <div className="min-h-full bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
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
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title={pageTitle}
        bordered
        className="shrink-0 bg-white px-6 max-md:px-4"
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

      <div className="flex min-h-0 flex-1 gap-5 px-6 max-md:px-4">
        <main
          ref={editorScrollRef}
          className="min-h-0 min-w-0 flex-1 space-y-4 overflow-y-auto pb-8 pt-5"
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

          <SourceSection
            batch={batch}
            prepareSchema={prepareSchema}
            form={form}
            sourceCatalog={sourceCatalog}
            targetCatalog={targetCatalog}
            selectedSourceDataSource={selectedSourceDataSource}
            mappings={mappings}
            updateMappings={updateMappings}
            writeMode={combination?.writeModes[0]}
            allowChunkKey={usesChangelog && initialLoad && !batch && draft.sourceType === "MYSQL"}
            usesChangelog={usesChangelog}
            continuous={continuous}
            useIncremental={useIncremental}
            initialLoad={initialLoad}
            startupMode={initialTask?.startup?.mode}
            patchSource={patchSource}
            onSourceSchemaChange={(schema) =>
              updateSourceSelection((current) => selectSourceSchema(current, schema, continuous))
            }
            onSourceTableChange={(table) =>
              updateSourceSelection((current) => selectSourceTable(current, table, continuous))
            }
            onSourcePrimaryKeys={(hasPrimaryKey) =>
              setForm((current) => setSourcePrimaryKeys(current, hasPrimaryKey, continuous))
            }
          />

          <TargetSection
            batch={batch}
            prepareSchema={prepareSchema}
            form={form}
            targetCatalog={targetCatalog}
            selectedTargetDataSource={selectedTargetDataSource}
            mappings={mappings}
            updateMappings={updateMappings}
            writeModes={combination?.writeModes || []}
            hasCombination={Boolean(combination)}
            usesChangelog={usesChangelog}
            continuous={continuous}
            useIncremental={useIncremental}
            initialLoad={initialLoad}
            targetType={draft.targetType}
            published={published}
            saving={saving}
            patchSink={patchSink}
            setTaskDirty={setTaskDirty}
            setParametersDirty={setParametersDirty}
            setForm={setForm}
          />
          <CollapseSection id="env" title="运行配置">
            <div className="space-y-4 rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-4">
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>执行模式</FieldLabel>
                <span className="text-sm">{continuous ? "流式" : "批式"}</span>
              </Field>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>Debug</FieldLabel>
                <Switch
                  aria-label="Debug"
                  size="small"
                  checked={form.debug}
                  onCheckedChange={(checked) => patch("debug", checked)}
                />
              </Field>
              {supportsScheduling ? (
                <div className="border-t border-[var(--yak-components-panel-border)] pt-4">
                  <EditorScheduleSection
                    schedule={schedule}
                    scheduleForm={scheduleForm}
                    scheduleError={scheduleError}
                    scheduleNeedsReload={scheduleNeedsReload}
                    scheduleLoading={scheduleLoading}
                    saving={saving}
                    onReload={reloadSchedule}
                    onChange={changeSchedule}
                  />
                </div>
              ) : null}
            </div>
          </CollapseSection>
        </main>

        <aside className="hidden h-fit w-44 shrink-0 self-start pt-5 lg:block">
          <EditorAnchorStepper items={anchorItems} scrollRootRef={editorScrollRef} />
        </aside>
      </div>
      {pendingConnection ? (
        <Modal
          open
          centered
          width={420}
          title="更换数据源"
          onClose={() => setPendingConnection(undefined)}
          footer={
            <>
              <Button size="small" onClick={() => setPendingConnection(undefined)}>
                取消
              </Button>
              <Button
                size="small"
                variant="primary"
                onClick={() => {
                  changeConnection(pendingConnection.side, pendingConnection.value);
                  setPendingConnection(undefined);
                }}
              >
                确认
              </Button>
            </>
          }
        >
          <p className="text-sm">更换数据源将清除已选择的表映射。</p>
        </Modal>
      ) : null}
    </div>
  );
}
