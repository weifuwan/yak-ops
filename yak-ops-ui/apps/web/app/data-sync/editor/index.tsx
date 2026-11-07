import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Modal, toast } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import type { TaskDefinitionRecord } from "@/service/data-sync";
import type { DataSourceRecord } from "@/service/datasource";
import { nativeDraftKey, writeNativeDraft, clearNativeDraft, type NativeSyncDraft } from "@/service/data-sync/native-draft";
import {
  sourceTable, setPipelineDatasource, setPipelineSourceTable, setPipelineSinkTable,
  validatePipelineDefinition, type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import {
  createNativeTask, updateNativeTask, publishNativeTask, previewNativeTask,
} from "@/service/data-sync/native-definition";
import { pipelineFingerprint } from "@/service/data-sync/versions";
import { TableTransferPanel } from "./table-transfer-panel";
import { DataSourceSection } from "./data-source-section";
import { EditorAnchorStepper } from "./anchor-stepper";
import { ContentSection } from "./content-layout";
import { catalogPath, tableKey, type DataSyncTablePath } from "./table-catalog";
import { useCatalogOptions } from "./use-catalog-options";
import { useEditorDataSources } from "./use-editor-data-sources";
import { EditorHeader, EditorFooter } from "./editor-footer";
import { PropertiesDrawer } from "./properties-drawer";

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
  const [propertiesOpen, setPropertiesOpen] = useState<boolean | undefined>();
  const [drawerDirty, setDrawerDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [previewYaml, setPreviewYaml] = useState<string>();
  const [error, setError] = useState("");
  const [catalogError, setCatalogError] = useState("");
  const [sourceTableFilter, setSourceTableFilter] = useState("");
  const [sourceTableKeyword, setSourceTableKeyword] = useState("");
  const [sourcePageNo, setSourcePageNo] = useState(1);
  const [sourcePageSize, setSourcePageSize] = useState(100);
  const [sourceChecked, setSourceChecked] = useState<string[]>([]);
  const { dataSources, dataSourcesLoading, dataSourcesError, loadDataSources } =
    useEditorDataSources(connections);
  const definition = authored.definition;
  const source = sourceTable(definition);
  const sourceId = definition.source["datasource-id"];
  const sinkId = definition.sink["datasource-id"];
  const sourceDatasource = dataSources.find((item) => item.id === sourceId);
  const sinkDatasource = dataSources.find((item) => item.id === sinkId);
  const sourceType = sourceDatasource?.dbType || initialDraft?.sourceType || "MYSQL";
  const sinkType = sinkDatasource?.dbType || initialDraft?.targetType || "MYSQL";
  const sourceCatalog = useCatalogOptions(
    sourceId,
    sourceDatasource?.database || "",
    sourceTableKeyword,
    sourcePageNo,
    sourcePageSize,
    Boolean(sourceId),
  );
  const sourceTables = sourceCatalog.tables.map(catalogPath);
  const selectedTables: DataSyncTablePath[] = source ? [source] : [];
  const dirty = pipelineFingerprint(authored) !== baseline;
  const definitionDirty = pipelineFingerprint(definition) !== baselineDefinition;
  const locked = saving || executionLocked || Boolean(task?.archived) || task?.status === "ONLINE";

  useUnsavedChanges(dirty || drawerDirty);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSourceTableKeyword(sourceTableFilter.trim());
      setSourcePageNo(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [sourceTableFilter]);

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
  ], []);

  const edit = (next: PipelineDefinition) => {
    setAuthored((current) => ({ ...current, definition: next }));
    setError("");
    setCatalogError("");
  };
  const changeSource = (value: string) => {
    setSourceTableFilter("");
    setSourceTableKeyword("");
    setSourcePageNo(1);
    setSourceChecked([]);
    edit(setPipelineDatasource(definition, "source", value));
  };
  const changeSink = (value: string) => {
    let next = setPipelineDatasource(definition, "sink", value);
    const target = dataSources.find((item) => item.id === value);
    if (source && target?.database) {
      next = setPipelineSinkTable(next, { database: target.database, table: source.table });
    }
    edit(next);
  };
  const selectSource = (table: (typeof sourceCatalog.tables)[number]) => {
    const location = catalogPath(table);
    try {
      setSourceChecked([]);
      let next = setPipelineSourceTable(definition, {
        database: location.database || location.schema || sourceDatasource?.database || "",
        table: location.table,
      });
      if (sinkDatasource?.database) {
        next = setPipelineSinkTable(next, { database: sinkDatasource.database, table: location.table });
      }
      edit(next);
    } catch (failure) {
      setCatalogError(failure instanceof Error ? failure.message : "来源表无效");
    }
  };
  const openProperties = () => setPropertiesOpen(true);
  const closeProperties = () => {
    setPropertiesOpen(false);
    setDrawerDirty(false);
  };
  const propertiesClosed = () =>
    setPropertiesOpen((current) => current === false ? undefined : current);

  const preview = async () => {
    if (previewing) return;
    if (!authored.name.trim()) {
      setError("请输入任务名称");
      openProperties();
      return;
    }
    try {
      validatePipelineDefinition(definition);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Pipeline 配置无效");
      return;
    }
    setPreviewing(true);
    setError("");
    try {
      const yaml = await previewNativeTask({
        name: authored.name.trim(),
        remark: authored.remark.trim(),
        definition,
      });
      setPreviewYaml(yaml);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Pipeline 预览失败");
    } finally {
      setPreviewing(false);
    }
  };

  const save = async (publish = false) => {
    if (locked) return;
    if (!authored.name.trim()) {
      setError("请输入任务名称");
      openProperties();
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

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <EditorHeader name={authored.name} locked={locked}
        onBack={() => navigate("/data-sync")}
        onProperties={openProperties} />
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
                  filter={sourceTableFilter}
                  onFilterChange={setSourceTableFilter}
                  loading={sourceCatalog.loading}
                  pageNo={sourcePageNo} pageSize={sourcePageSize} total={sourceCatalog.total}
                  onPageChange={(pageNo, pageSize) => {
                    setSourcePageNo(pageNo);
                    setSourcePageSize(pageSize);
                    setSourceChecked([]);
                  }}
                  onRefresh={sourceCatalog.refresh} />
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
                  hideFilters />
              </div>
              {sourceCatalog.error || catalogError ? (
                <Alert>{sourceCatalog.error || catalogError}
                  <Button size="small" onClick={sourceCatalog.refresh}>刷新</Button>
                </Alert>
              ) : null}
            </ContentSection>
          </fieldset>
        </main>
        <aside className="hidden w-[176px] shrink-0  pt-4 lg:block">
          <EditorAnchorStepper items={anchorItems} />
        </aside>
      </div>
      <EditorFooter task={task} dirty={dirty} locked={locked} saving={saving}
        previewing={previewing} onSave={() => void save()} onPreview={() => void preview()}
        onPublish={() => void save(true)} />
      {previewYaml !== undefined ? (
        <Modal open centered width={760} title="Pipeline YAML"
          onClose={() => setPreviewYaml(undefined)}
          footer={<Button size="small" onClick={() => setPreviewYaml(undefined)}>关闭</Button>}>
          <pre className="max-h-[600px] overflow-auto whitespace-pre-wrap rounded border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 font-mono text-xs leading-5">
            {previewYaml}
          </pre>
        </Modal>
      ) : null}
      {propertiesOpen !== undefined ? (
        <PropertiesDrawer id={task?.id} value={{ name: authored.name, remark: authored.remark }}
          open={propertiesOpen}
          onApply={(properties) => { setAuthored((old) => ({ ...old, ...properties })); closeProperties(); }}
          onClose={closeProperties} onClosed={propertiesClosed} onDirtyChange={setDrawerDirty} />
      ) : null}
    </div>
  );
}
