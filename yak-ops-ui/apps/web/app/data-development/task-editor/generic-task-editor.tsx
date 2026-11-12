import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { toast } from "@yak-ops/yak-ui";
import {
  getWorkflowDefinition, updateWorkflowTaskDefinition, type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { NodeEditorFrame } from "./node-editor-frame";
import {
  getTaskGroupValidationError, normalizeTaskCommonSettings, parseTaskCommonSettings,
  TaskCommonSettingsSection, useTaskGroups, type TaskCommonSettings,
} from "./task-common-settings-section";

type DraftTaskRecord = { nodeId: string; name: string; taskType: string };

export function GenericTaskEditor({
  workflowCode, task, draftTask, taskName, taskType, onBackToWorkflow,
}: {
  workflowCode: number;
  task?: WorkflowTaskRecord;
  draftTask?: DraftTaskRecord;
  taskName: string;
  taskType: string;
  onBackToWorkflow: () => void;
}) {
  const queryClient = useQueryClient();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const [currentTask, setCurrentTask] = useState(task);
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [commonSettings, setCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [baselineCommonSettings, setBaselineCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setCurrentTask(task);
    setCommonSettings(initialCommonSettings);
    setBaselineCommonSettings(initialCommonSettings);
  }, [task, initialCommonSettings]);

  const isDirty = JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings);

  const jsonText = useMemo(() => {
    let commonFields: Record<string, unknown>;
    try {
      commonFields = normalizeTaskCommonSettings(commonSettings);
    } catch {
      commonFields = commonSettings;
    }
    return JSON.stringify(
      currentTask
        ? { workflowCode, task: { ...currentTask, ...commonFields, taskType: currentTask.taskType || taskType } }
        : { workflowCode, draft: true, node: draftTask, commonSettings },
      null,
      2,
    );
  }, [commonSettings, currentTask, draftTask, taskType, workflowCode]);

  const handleSave = async () => {
    if (!currentTask) return false;
    let commonPatch;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点通用配置");
      return false;
    }
    const taskGroupError = getTaskGroupValidationError(commonSettings, {
      projectCode, taskGroups, isPending: taskGroupsQuery.isPending, isError: taskGroupsQuery.isError,
    });
    if (taskGroupError) {
      toast.warning(taskGroupError);
      return false;
    }

    setSaving(true);
    try {
      await updateWorkflowTaskDefinition(workflowCode, currentTask.code, commonPatch);
      setCurrentTask(current => current ? { ...current, ...commonPatch } : current);
      setBaselineCommonSettings(commonSettings);
      await queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-definition"] });
      toast.success("节点通用配置已保存");
      return true;
    } catch (error) {
      toast.error("保存节点通用配置失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
      return false;
    } finally {
      setSaving(false);
    }
  };

  const handleRefresh = async () => {
    if (isDirty) {
      toast.warning("当前配置有未保存修改，请先保存后再刷新");
      return;
    }
    setRefreshing(true);
    try {
      if (!currentTask) {
        toast.success("节点信息已刷新");
        return;
      }
      const dag = await getWorkflowDefinition(workflowCode);
      const latestTask = dag.taskDefinitionList.find(item => item.code === currentTask.code);
      if (!latestTask) throw new Error("节点不存在或已被删除");
      const nextCommonSettings = parseTaskCommonSettings(latestTask);
      setCurrentTask(latestTask);
      setCommonSettings(nextCommonSettings);
      setBaselineCommonSettings(nextCommonSettings);
      await queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-definition"] });
      toast.success("节点信息已刷新");
    } catch (error) {
      toast.error("刷新失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <NodeEditorFrame
      task={currentTask}
      taskName={taskName}
      taskType={taskType}
      workflowCode={workflowCode}
      activeView={activeView}
      onActiveViewChange={setActiveView}
      isDirty={isDirty}
      saving={saving}
      running={false}
      stopping={false}
      refreshing={refreshing}
      lastWorkflowInstanceId={null}
      canRun={false}
      onRun={() => undefined}
      onStop={() => undefined}
      onSave={() => void handleSave()}
      onRefresh={() => void handleRefresh()}
      onBack={onBackToWorkflow}
    >
      {activeView === "json" ? (
        <div className="min-h-0 flex-1 overflow-auto bg-[var(--yak-components-panel-bg)] p-4">
          <pre className="w-full overflow-auto rounded-sm border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
            {jsonText}
          </pre>
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-components-panel-bg)] p-4">
          <div className="mx-auto max-w-[1120px] space-y-3">
            <section className="rounded-md border border-[var(--yak-components-panel-border)] bg-white p-3">
              <h3 className="mb-2 text-xs font-semibold text-[var(--yak-components-panel-text)]">节点专属参数</h3>
              <p className="mb-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                当前节点类型暂未提供可视化参数编辑器，原有节点参数保持不变。
              </p>
              <pre className="max-h-64 overflow-auto rounded-sm bg-[var(--yak-color-hover)] p-3 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
                {JSON.stringify(currentTask?.taskParams ?? {}, null, 2)}
              </pre>
            </section>
            <TaskCommonSettingsSection
              id="generic-common-settings"
              title="节点通用配置"
              settings={commonSettings}
              onChange={setCommonSettings}
              disabled={!currentTask}
              description={currentTask
                ? "此区域适用于所有节点类型；节点专属参数仍由对应类型的编辑器负责。"
                : "节点创建流程尚未完成，保存为正式节点后即可配置公共执行属性。"}
            />
          </div>
        </div>
      )}
    </NodeEditorFrame>
  );
}
