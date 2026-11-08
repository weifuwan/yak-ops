import { useEffect, useMemo, useState } from "react";
import { toast } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";

import { useAuth } from "@/hooks/use-auth";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import type { DataSyncScope } from "@/service/data-sync";
import {
  clearNativeDraft,
  nativeDraftKey,
  writeNativeDraft,
} from "@/service/data-sync/native-draft";
import {
  pipelineSteps,
  validatePipelineDefinition,
  type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import {
  createNativeTask,
  publishNativeTask,
  previewNativeTask,
  updateNativeTask,
} from "@/service/data-sync/native-definition";
import { pipelineFingerprint } from "@/service/data-sync/versions";
import { useEditorDataSources } from "./use-editor-data-sources";
import type { AuthoredTask, EditorValidator, TaskEditorProps } from "./editor-types";

export function useTaskEditorController(
  {
    initialDraft,
    initialTask,
    initialDefinition,
    connections = [],
    executionLocked = false,
    executionLockMessage,
  }: TaskEditorProps,
  scope: DataSyncScope,
) {
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
  const [baselineDefinition, setBaselineDefinition] = useState(() =>
    pipelineFingerprint(authored.definition),
  );
  const [propertiesOpen, setPropertiesOpen] = useState<boolean | undefined>();
  const [advancedOpen, setAdvancedOpen] = useState<boolean | undefined>();
  const [drawerDirty, setDrawerDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [previewYaml, setPreviewYaml] = useState<string>();
  const [error, setError] = useState("");
  const { dataSources, dataSourcesLoading, dataSourcesError, loadDataSources } =
    useEditorDataSources(connections);

  const definition = authored.definition;
  const steps = useMemo(() => pipelineSteps(definition), [definition]);
  const hasFull = steps.includes("FULL");
  const hasIncremental = steps.includes("INCREMENTAL");
  const scopeLabel =
    scope === "DATABASE"
      ? "整库同步"
      : scope === "MULTI_TABLE"
        ? "多表同步"
        : "单表同步";
  const modeLabel =
    hasFull && hasIncremental ? "全量 + 增量" : hasFull ? "全量" : "增量";
  const syncPlan = `MySQL · ${scopeLabel} · ${modeLabel}`;

  const sourceId = definition.source["datasource-id"];
  const sinkId = definition.sink["datasource-id"];
  const sourceDatasource = dataSources.find((item) => item.id === sourceId);
  const sinkDatasource = dataSources.find((item) => item.id === sinkId);
  const sourceType = sourceDatasource?.dbType || initialDraft?.sourceType || "MYSQL";
  const sinkType = sinkDatasource?.dbType || initialDraft?.targetType || "MYSQL";

  const dirty = pipelineFingerprint(authored) !== baseline;
  const definitionDirty =
    pipelineFingerprint(definition) !== baselineDefinition;
  const locked =
    saving ||
    executionLocked ||
    Boolean(task?.archived) ||
    task?.status === "ONLINE";

  useUnsavedChanges(dirty || drawerDirty);

  useEffect(() => {
    if (task || saving) return;
    writeNativeDraft(draftKey, {
      name: authored.name,
      remark: authored.remark,
      sourceType,
      targetType: sinkType,
      scope,
      steps,
      definition: authored.definition,
    });
  }, [
    task,
    saving,
    draftKey,
    authored,
    sourceType,
    sinkType,
    scope,
    steps,
  ]);

  const edit = (next: PipelineDefinition) => {
    setAuthored((current) => ({ ...current, definition: next }));
    setError("");
  };

  const openAdvanced = () => setAdvancedOpen(true);
  const closeAdvanced = () => {
    setAdvancedOpen(false);
    setDrawerDirty(false);
  };
  const advancedClosed = () =>
    setAdvancedOpen((current) => (current === false ? undefined : current));

  const openProperties = () => setPropertiesOpen(true);
  const closeProperties = () => {
    setPropertiesOpen(false);
    setDrawerDirty(false);
  };
  const propertiesClosed = () =>
    setPropertiesOpen((current) => (current === false ? undefined : current));

  const validate = (validateMode: EditorValidator) => {
    validatePipelineDefinition(definition);
    validateMode();
  };

  const preview = async (validateMode: EditorValidator) => {
    if (previewing) return;
    if (!authored.name.trim()) {
      setError("请输入任务名称");
      openProperties();
      return;
    }
    try {
      validate(validateMode);
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

  const copyPreviewYaml = async () => {
    if (!previewYaml) return;
    try {
      await navigator.clipboard.writeText(previewYaml);
      toast.success("YAML 已复制");
    } catch {
      toast.error("复制失败");
    }
  };

  const save = async (publish: boolean, validateMode: EditorValidator) => {
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
      validate(validateMode);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Pipeline 配置无效");
      return;
    }

    setSaving(true);
    setError("");
    let persisted = task;
    try {
      const value = {
        name: authored.name.trim(),
        remark: authored.remark.trim(),
        definition,
      };
      persisted =
        task && dirty
          ? await updateNativeTask(
              task.id,
              value,
              task.revision,
              definitionDirty,
            )
          : task || (await createNativeTask(value));
      setTask(persisted);
      setBaseline(pipelineFingerprint(authored));
      setBaselineDefinition(pipelineFingerprint(definition));
      clearNativeDraft(draftKey);

      if (publish) {
        const publication = await publishNativeTask(
          persisted.id,
          persisted.definitionVersion,
          persisted.revision,
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
      setError(
        (persisted ? "任务已保存，但后续操作未完成：" : "") +
          (failure instanceof Error ? failure.message : "操作失败"),
      );
    } finally {
      setSaving(false);
    }
  };

  return {
    task,
    authored,
    setAuthored,
    definition,
    steps,
    syncPlan,
    sourceId,
    sinkId,
    sourceDatasource,
    sinkDatasource,
    sourceType,
    sinkType,
    dataSources,
    dataSourcesLoading,
    dataSourcesError,
    loadDataSources,
    dirty,
    locked,
    saving,
    previewing,
    previewYaml,
    setPreviewYaml,
    error,
    setError,
    executionLocked,
    executionLockMessage,
    propertiesOpen,
    advancedOpen,
    drawerDirty,
    setDrawerDirty,
    edit,
    openAdvanced,
    closeAdvanced,
    advancedClosed,
    openProperties,
    closeProperties,
    propertiesClosed,
    preview,
    copyPreviewYaml,
    save,
  };
}

export type TaskEditorController = ReturnType<typeof useTaskEditorController>;
