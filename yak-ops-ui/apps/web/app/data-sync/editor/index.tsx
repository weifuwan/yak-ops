import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Input, Table, type TableColumns, toast } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import type { TaskDefinitionRecord, TaskDefinitionVersion } from "@/service/data-sync";
import type { DataSourceRecord } from "@/service/datasource";
import { nativeDraftKey, writeNativeDraft, clearNativeDraft, type NativeSyncDraft } from "@/service/data-sync/native-draft";
import {
  sourceTable, setPipelineDatasource, setPipelineSourceTable, setPipelineSinkTable,
  readPipelineDefinition, validatePipelineDefinition, type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import {
  createNativeTask, updateNativeTask, publishNativeTask,
} from "@/service/data-sync/native-definition";
import { pipelineFingerprint } from "@/service/data-sync/versions";
import { TableTransferPanel, type TransferFilters } from "./table-transfer-panel";
import { DataSourceSection } from "./data-source-section";
import { EditorAnchorStepper } from "./anchor-stepper";
import { ContentField, ContentSection } from "./content-layout";
import { catalogPath, tableKey, tablePathLabel, type DataSyncTablePath } from "./table-catalog";
import { useCatalogOptions } from "./use-catalog-options";
import { useEditorDataSources } from "./use-editor-data-sources";
import { EditorHeader, EditorFooter } from "./editor-chrome";
import { PropertiesDrawer } from "./properties-drawer";
import { AdvancedDrawer } from "./advanced-drawer";
import { VersionsDrawer } from "./versions-drawer";
import { EditorConfirmationDialog } from "./editor-feedback";

type Drawer = "properties" | "advanced" | "versions";
interface Confirmation {
  title: string;
  message: string;
  apply: () => void;
}
interface EditorProps {
  initialDraft?: NativeSyncDraft;
  initialTask?: TaskDefinitionRecord;
  initialDefinition?: PipelineDefinition;
  connections?: DataSourceRecord[];
  executionLocked?: boolean;
  executionLockMessage?: string;
}
interface AuthoredTask {
  name: string;
  remark: string;
  definition: PipelineDefinition;
}

export function TaskEditor({
  initialDraft, initialTask, initialDefinition, connections = [],
  executionLocked = false, executionLockMessage,
}: EditorProps) {
  const { currentUser } = useAuth();
  const draftKey = nativeDraftKey(currentUser!.id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [task, setTask] = useState(initialTask);
  const [authored, setAuthored] = useState<AuthoredTask>(() => ({
    name: initialTask?.name || initialDraft?.name || "",
    remark: initialTask?.remark || initialDraft?.remark || "",
    definition: structuredClone(initialDefinition || initialDraft!.definition),
  }));
  const [baseline, setBaseline] = useState(() => pipelineFingerprint(authored));
  const [baselineDefinition, setBaselineDefinition] = useState(() => pipelineFingerprint(authored.definition));
  const [drawer, setDrawer] = useState<{ kind: Drawer; open: boolean }>();
  const [drawerDirty, setDrawerDirty] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [catalogError, setCatalogError] = useState("");
  const [sourceFilters, setSourceFilters] = useState<TransferFilters>({ database: "", table: "" });
  const [sourceChecked, setSourceChecked] = useState<string[]>([]);
  const { dataSources, dataSourcesLoading, dataSourcesError, loadDataSources } =
    useEditorDataSources(connections);
  const definition = authored.definition;
  const source = sourceTable(definition);
  const sinkValue = definition.route[0]["sink-table"] || "";
  const targetParts = sinkValue.split(".");
  const targetDatabase = targetParts[0] || "";
  const targetTable = targetParts.slice(1).join(".");
  const sourceId = definition.source["datasource-id"];
  const sinkId = definition.sink["datasource-id"];
  const sourceDatasource = dataSources.find((item) => item.id === sourceId);
  const sinkDatasource = dataSources.find((item) => item.id === sinkId);
  const sourceType = sourceDatasource?.dbType || initialDraft?.sourceType || "MYSQL";
  const sinkType = sinkDatasource?.dbType || initialDraft?.targetType || "MYSQL";
  const sourceCatalog = useCatalogOptions(sourceId, "", "", Boolean(sourceId));
  const sourceTables = sourceCatalog.tables.map(catalogPath).filter((item) => {
    try {
      const db = new RegExp(sourceFilters.database || ".*", "i");
      const table = new RegExp(sourceFilters.table || ".*", "i");
      return db.test(item.database || item.schema || "") && table.test(item.table);
    } catch { return false; }
  });
  const selectedTables: DataSyncTablePath[] = source ? [source] : [];
  const dirty = pipelineFingerprint(authored) !== baseline;
  const definitionDirty = pipelineFingerprint(definition) !== baselineDefinition;
  const locked = saving || executionLocked || Boolean(task?.archived) || task?.status === "ONLINE";

  useUnsavedChanges(dirty || drawerDirty);

  useEffect(() => {
    if (task || saving) return;
    writeNativeDraft(draftKey, {
      name: authored.name,
      remark: authored.remark,
      sourceType, targetType: sinkType,
      definition: authored.definition,
    });
  }, [task, saving, draftKey, authored, sourceType, sinkType]);

  const anchorItems = useMemo(() => [
    { id: "datasource", label: "数据源" },
    { id: "source", label: "数据来源" },
    { id: "target", label: "目标表映射" },
  ], []);

  const edit = (next: PipelineDefinition) => {
    setAuthored((current) => ({ ...current, definition: next }));
    setError("");
    setCatalogError("");
  };
  const changeSource = (value: string) => edit(setPipelineDatasource(definition, "source", value));
  const changeSink = (value: string) => edit(setPipelineDatasource(definition, "sink", value));
  const selectSource = (table: (typeof sourceCatalog.tables)[number]) => {
    const location = catalogPath(table);
    try {
      setSourceChecked([]);
      edit(setPipelineSourceTable(definition, {
        database: location.database || location.schema || sourceDatasource?.database || "",
        table: location.table,
      }));
    } catch (failure) {
      setCatalogError(failure instanceof Error ? failure.message : "来源表无效");
    }
  };
  const updateTarget = (database: string, table: string) => {
    const next = structuredClone(definition);
    // Intermediate keystrokes may be incomplete; only saving requires a valid path.
    next.route[0]["sink-table"] = database + "." + table;
    edit(next);
  };
  const openDrawer = (kind: Drawer) => setDrawer({ kind, open: true });
  const closeDrawer = () => {
    setDrawer((current) => current && { ...current, open: false });
    setDrawerDirty(false);
  };
  const closeComplete = () =>
    setDrawer((current) => current?.open ? current : undefined);

  const save = async (publish = false) => {
    if (locked) return;
    if (!authored.name.trim()) {
      setError("请输入任务名称");
      openDrawer("properties");
      return;
    }
    if (sourceType !== "MYSQL" || sinkType !== "MYSQL") {
      setError("当前 Runtime 仅支持 MySQL 来源和 MySQL 目标");
      return;
    }
    try {
      validatePipelineDefinition(definition);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Pipeline 配置无效");
      return;
    }
    setSaving(true);
    setError("");
    let persisted: TaskDefinitionRecord | undefined;
    try {
      const value = { name: authored.name.trim(), remark: authored.remark.trim(), definition };
      persisted = task && dirty
        ? await updateNativeTask(task.id, value, task.revision, definitionDirty)
        : task || await createNativeTask(value);
      setTask(persisted);
      setBaseline(pipelineFingerprint(authored));
      setBaselineDefinition(pipelineFingerprint(definition));
      clearNativeDraft(draftKey);
      if (publish) {
        const publication = await publishNativeTask(
          persisted.id, persisted.definitionVersion, persisted.revision,
        );
        persisted = publication;
        setTask(publication);
        toast.success("任务已发布");
      } else {
        toast.success("同步任务已保存");
      }
      void queryClient.invalidateQueries({ queryKey: ["data-sync-list"] });
      void queryClient.invalidateQueries({ queryKey: ["data-sync-detail"] });
      void queryClient.invalidateQueries({ queryKey: ["data-sync-editor"] });
      if (!initialTask && persisted) {
        navigate("/data-sync/" + persisted.id + "/edit", { replace: true });
      }
    } catch (failure) {
      setError((persisted ? "任务已保存，但后续操作未完成：" : "") +
        (failure instanceof Error ? failure.message : "操作失败"));
    } finally {
      setSaving(false);
    }
  };
  const restore = (version: TaskDefinitionVersion) => {
    if (!task || version.definitionId !== task.id) {
      setError("历史版本不属于当前任务");
      return;
    }
    try {
      const restored = readPipelineDefinition(version.parameters);
      setConfirmation({
        title: "还原任务定义版本 V" + version.version,
        message: "将使用历史 Pipeline 配置替换当前页面的配置，确认后仍需保存和发布。",
        apply: () => {
          edit(restored);
          closeDrawer();
        },
      });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "历史定义不受当前 Runtime 支持");
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <EditorHeader name={authored.name} locked={locked} saving={saving}
        onBack={() => navigate("/data-sync")}
        onProperties={() => openDrawer("properties")}
        onAdvanced={() => openDrawer("advanced")}
        onVersions={() => openDrawer("versions")} />
      <div className="flex min-h-0 flex-1 gap-4 px-4">
        <main className="min-h-0 min-w-0 flex-1 space-y-3 overflow-y-auto pb-6 pt-4">
          {executionLocked ? <Alert>{executionLockMessage || "任务正在执行，暂不可编辑"}</Alert> : null}
          {task?.status === "ONLINE" ? <Alert>请先下线任务，再修改或重新发布配置。</Alert> : null}
          {error ? <Alert>{error}</Alert> : null}
          {dataSourcesError ? (
            <Alert>{dataSourcesError}<Button size="small" onClick={loadDataSources}>重试</Button></Alert>
          ) : null}
          <fieldset disabled={locked} className="min-w-0 space-y-3">
            <DataSourceSection id={task?.id} sourceType={sourceType} targetType={sinkType}
              sourceDataSources={dataSources.filter((item) => item.dbType === "MYSQL")}
              targetDataSources={dataSources.filter((item) => item.dbType === "MYSQL")}
              sourceDataSourceId={sourceId} targetDataSourceId={sinkId}
              loading={dataSourcesLoading}
              onRefresh={loadDataSources}
              onSourceChange={changeSource} onTargetChange={changeSink} />
            <ContentSection id="source" title="数据来源">
              <div className="grid grid-cols-1 items-center gap-3 lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
                <TableTransferPanel title="来源库表" tables={sourceTables}
                  checked={sourceChecked} onCheckedChange={(keys) => setSourceChecked(keys.slice(-1))}
                  filters={sourceFilters} onFiltersChange={setSourceFilters}
                  loading={sourceCatalog.loading} onRefresh={sourceCatalog.refresh} />
                <Button size="small" disabled={!sourceChecked.length || !sourceId}
                  onClick={() => {
                    const selected = sourceCatalog.tables.find(
                      (item) => tableKey(catalogPath(item)) === sourceChecked[0],
                    );
                    if (selected) selectSource(selected);
                  }}>添加 →</Button>
                <TableTransferPanel title="已选库表" tables={selectedTables}
                  checked={[]} onCheckedChange={() => {
                    if (sourceId) edit(setPipelineDatasource(definition, "source", sourceId));
                  }}
                  filters={{ database: "", table: "" }}
                  onFiltersChange={() => {}} hideFilters />
              </div>
              {sourceCatalog.error || catalogError ? (
                <Alert>{sourceCatalog.error || catalogError}
                  <Button size="small" onClick={sourceCatalog.refresh}>刷新</Button>
                </Alert>
              ) : null}
              <p className="text-xs text-[var(--yak-components-muted-text)]">
                当前 Runtime 仅支持一张 MySQL 表的全量后持续增量同步。
              </p>
            </ContentSection>
            <ContentSection id="target" title="目标表映射">
              <Table<{ id: string }>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={[{ id: "mapping" }]}
                columns={[
                  {
                    key: "source", title: "来源表",
                    render: () => <span>{source ? tablePathLabel(source) : "请选择来源表"}</span>,
                  },
                  {
                    key: "database", title: "目标数据库",
                    render: () => (
                      <Input size="small" variant="outlined" aria-label="目标数据库"
                        value={targetDatabase} disabled={!sinkId} placeholder="目标数据库"
                        onChange={(event) => updateTarget(event.target.value, targetTable)} />
                    ),
                  },
                  {
                    key: "table", title: "目标表",
                    render: () => (
                      <Input size="small" variant="outlined" aria-label="目标表"
                        value={targetTable} disabled={!sinkId} placeholder="目标表"
                        onChange={(event) => updateTarget(targetDatabase, event.target.value)} />
                    ),
                  },
                  {
                    key: "write", title: "写入方式",
                    render: () => <span>UPSERT</span>,
                  },
                ] as TableColumns<{ id: string }>}
              />
              <p className="text-xs text-[var(--yak-components-muted-text)]">
                目标表将由 CDC Pipeline 创建或调整，写入时使用来源物理主键。
              </p>
            </ContentSection>
          </fieldset>
        </main>
        <aside className="hidden w-[176px] shrink-0 border-l border-[var(--yak-components-panel-border)] pt-4 lg:block">
          <EditorAnchorStepper items={anchorItems} />
        </aside>
      </div>
      <EditorFooter task={task} dirty={dirty} locked={locked} saving={saving}
        onSave={() => void save()} onPublish={() => void save(true)} />
      {drawer?.kind === "properties" ? (
        <PropertiesDrawer id={task?.id} value={{ name: authored.name, remark: authored.remark }}
          open={drawer.open}
          onApply={(properties) => { setAuthored((old) => ({ ...old, ...properties })); closeDrawer(); }}
          onClose={closeDrawer} onClosed={closeComplete} onDirtyChange={setDrawerDirty} />
      ) : null}
      {drawer?.kind === "advanced" ? (
        <AdvancedDrawer value={definition} open={drawer.open}
          onApply={(next) => { edit(next); closeDrawer(); }}
          onClose={closeDrawer} onClosed={closeComplete} onDirtyChange={setDrawerDirty} />
      ) : null}
      {drawer?.kind === "versions" ? (
        <VersionsDrawer task={task} open={drawer.open} onRestore={restore}
          allowRestore={!locked} onClose={closeDrawer} onClosed={closeComplete} />
      ) : null}
      {confirmation ? (
        <EditorConfirmationDialog value={confirmation} onClose={() => setConfirmation(undefined)} />
      ) : null}
    </div>
  );
}
