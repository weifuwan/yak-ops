import { Alert, Button, PageHeader, Modal } from "@yak-ops/yak-ui";
import { useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { taskDraftKey } from "@/service/data-sync/draft";
import {
  offlineIncremental,
  solutionSyncType,
  supportsMultipleTables,
} from "@/service/data-sync/parameters";
import { EMPTY_INCREMENTAL } from "./incremental-fields";
import { findStepCombination } from "../solution/presentation";
import { useSolutions } from "../solution/use-solutions";
import { EditorAnchorStepper, type EditorAnchorItem } from "./anchor-stepper";
import type { ScheduleTimeRule } from "@/service/schedule";
import type { DataSyncTableMapping, DataSyncTaskStatus } from "@/service/data-sync";
import { BasicSection } from "./basic-section";
import { DataSourcesSection } from "./data-sources-section";
import { SourceSection } from "./source-section";
import { TargetSection } from "./target-section";
import { EditorScheduleSection } from "./schedule-section";
import { useCatalogOptions } from "./use-catalog-options";
import { buildEditorPayload, canSaveEditor } from "./definition";
import { useEditorSubmission } from "./use-editor-submission";
import { useEditorDataSources } from "./use-editor-data-sources";
import { useEditorSchedule } from "./use-editor-schedule";
import { useEditorDraft } from "./use-editor-draft";
import { EMPTY_FORM, type TaskEditorInput, type EditorForm } from "./types";

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
  const solution = choices.find((value) => value.solutionType === draft.solutionType);
  const combination = findStepCombination(solution, draft.steps);
  const supportsScheduling = Boolean(combination?.supportsScheduling);
  const prepareSchema = draft.steps.includes("SCHEMA");
  const useIncremental = offlineIncremental(draft.solutionType, draft.steps);
  const initialLoad = draft.steps.includes("FULL");
  const usesChangelog =
    draft.steps.includes("INCREMENTAL") && solutionSyncType(draft.solutionType) === "REALTIME";

  const [form, setForm] = useState<EditorForm>(() => {
    const value = initialTask || draft.form;
    return {
      ...EMPTY_FORM,
      name: draft.name,
      remark: value?.remark || "",
      debug: value?.debug ?? false,
      schemaChangesEnabled: value?.schemaChangesEnabled ?? draft.targetType !== "ORACLE",
      allowDropColumns: value?.allowDropColumns ?? false,
      writeMode: value?.keylessEnabled
        ? "RETRACT"
        : value?.writeMode === "UPSERT" || usesChangelog || useIncremental
          ? "UPSERT"
          : "APPEND",
      keylessEnabled: value?.keylessEnabled ?? false,
      xaEnabled: value?.xaEnabled ?? false,
      chunkKeyColumn: value?.chunkKeyColumn ?? "",
      incremental: useIncremental ? value?.incremental || EMPTY_INCREMENTAL : undefined,
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
  useEditorDraft(id, draftKey, draft, form, batch, mappings, useIncremental, savedTask);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => {
    setTaskDirty(true);
    if (key !== "name" && key !== "remark" && key !== "debug") setParametersDirty(true);
    setForm((current) => ({ ...current, [key]: value }));
  };

  const updateMappings = (value: DataSyncTableMapping[]) => {
    setMappings(value);
    setTaskDirty(true);
    setParametersDirty(true);
  };
  const changeConnection = (side: "source" | "target", value: string) => {
    setMappings([]);
    if (side === "source") {
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
    } else {
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
    }
  };
  const requestConnectionChange = (side: "source" | "target", value: string) => {
    if (batch && mappings.length) setPendingConnection({ side, value });
    else changeConnection(side, value);
  };

  const payload = () => buildEditorPayload(form, draft, batch, mappings, usesChangelog);

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

          <DataSourcesSection
            id={id}
            draft={draft}
            form={form}
            sourceDataSources={sourceDataSources}
            targetDataSources={targetDataSources}
            dataSourcesLoading={dataSourcesLoading}
            loadDataSources={loadDataSources}
            requestConnectionChange={requestConnectionChange}
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
            allowKeyless={usesChangelog && !batch && !prepareSchema && draft.targetType === "MYSQL"}
            usesChangelog={usesChangelog}
            useIncremental={useIncremental}
            initialLoad={initialLoad}
            patch={patch}
            setTaskDirty={setTaskDirty}
            setParametersDirty={setParametersDirty}
            setForm={setForm}
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
            canSave={Boolean(canSave)}
            payload={payload}
            usesChangelog={usesChangelog}
            useIncremental={useIncremental}
            initialLoad={initialLoad}
            targetType={draft.targetType}
            published={published}
            saving={saving}
            patch={patch}
            setTaskDirty={setTaskDirty}
            setParametersDirty={setParametersDirty}
            setForm={setForm}
          />
          {supportsScheduling ? (
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
          ) : null}
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
