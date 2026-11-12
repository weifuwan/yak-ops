import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { Textarea, toast } from "@yak-ops/yak-ui";
import { getWorkflowDefinition, type WorkflowTaskRecord } from "@/service/workflow/definition";

import { NodeEditorFrame } from "./node-editor-frame";
import {
  getTaskGroupValidationError,
  normalizeTaskCommonSettings,
  parseTaskCommonSettings,
  TaskCommonSettingsSection,
  useTaskGroups,
  type TaskCommonSettings,
} from "./task-common-settings-section";

function formatTaskParams(value: unknown): string {
  if (typeof value === "string") {
    try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
  }
  return JSON.stringify(value ?? {}, null, 2);
}

function parseTaskParams(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("节点专属参数必须是 JSON 对象");
  }
  return parsed as Record<string, unknown>;
}

export function GenericTaskEditor({
  workflowCode,
  task,
  taskName,
  taskType,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  taskType: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
}) {
  const queryClient = useQueryClient();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const [currentTask, setCurrentTask] = useState(task);
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [commonSettings, setCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [baselineCommonSettings, setBaselineCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [taskParamsText, setTaskParamsText] = useState(() => formatTaskParams(task.taskParams));
  const [baselineTaskParamsText, setBaselineTaskParamsText] = useState(() => formatTaskParams(task.taskParams));
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const paramsText = formatTaskParams(task.taskParams);
    setCurrentTask(task);
    setCommonSettings(initialCommonSettings);
    setBaselineCommonSettings(initialCommonSettings);
    setTaskParamsText(paramsText);
    setBaselineTaskParamsText(paramsText);
  }, [task, initialCommonSettings]);

  const isDirty =
    JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings) ||
    taskParamsText !== baselineTaskParamsText;

  useEffect(() => {
    onDirtyChange(isDirty);
    return () => onDirtyChange(false);
  }, [isDirty, onDirtyChange]);

  const jsonText = useMemo(() => {
    let commonFields: Record<string, unknown>;
    let taskParams: unknown = taskParamsText;
    try { commonFields = normalizeTaskCommonSettings(commonSettings); }
    catch { commonFields = commonSettings; }
    try { taskParams = parseTaskParams(taskParamsText); } catch { /* show the in-progress JSON */ }
    return JSON.stringify(
      { workflowCode, task: { ...currentTask, ...commonFields, taskParams, taskType } },
      null,
      2,
    );
  }, [commonSettings, currentTask, taskParamsText, taskType, workflowCode]);

  const handleSave = async () => {
    let commonPatch;
    let taskParams: Record<string, unknown>;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
      taskParams = parseTaskParams(taskParamsText);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点配置");
      return false;
    }

    const taskGroupError = getTaskGroupValidationError(commonSettings, {
      projectCode,
      taskGroups,
      isPending: taskGroupsQuery.isPending,
      isError: taskGroupsQuery.isError,
    });
    if (taskGroupError) {
      toast.warning(taskGroupError);
      return false;
    }

    setSaving(true);
    try {
      const nextTask = { ...currentTask, ...commonPatch, taskParams };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setBaselineCommonSettings(commonSettings);
      setBaselineTaskParamsText(taskParamsText);
      toast.success("节点配置已应用，请保存工作流");
      return true;
    } finally {
      setSaving(false);
    }
  };

  const handleRefresh = async () => {
    if (isDirty) {
      toast.warning("当前配置有未保存修改，请先保存后再刷新");
      return;
    }
    if (currentTask.version <= 0 || currentTask.id <= 0) {
      toast.info("新建节点尚未保存到后端，暂无可刷新的服务端配置");
      return;
    }
    setRefreshing(true);
    try {
      const dag = await getWorkflowDefinition(workflowCode);
      const latestTask = dag.taskDefinitionList.find(item => item.code === currentTask.code);
      if (!latestTask) throw new Error("节点不存在或已被删除");
      if (!onApplyTask(latestTask)) return;
      const nextCommonSettings = parseTaskCommonSettings(latestTask);
      const paramsText = formatTaskParams(latestTask.taskParams);
      setCurrentTask(latestTask);
      setCommonSettings(nextCommonSettings);
      setBaselineCommonSettings(nextCommonSettings);
      setTaskParamsText(paramsText);
      setBaselineTaskParamsText(paramsText);
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
      readOnly={readOnly}
      onRun={() => undefined}
      onStop={() => undefined}
      onSave={() => void handleSave()}
      onRefresh={() => void handleRefresh()}
      onBack={onBackToWorkflow}
      runConfig={
        <TaskCommonSettingsSection
          id="generic-run-config"
          settings={commonSettings}
          onChange={setCommonSettings}
          disabled={readOnly}
          compact
        />
      }
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
              <p className="mb-3 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                当前节点类型暂未提供可视化参数表单，可以在这里编辑原始 taskParams JSON。
              </p>
              <Textarea
                size="small"
                variant="outlined"
                rows={18}
                value={taskParamsText}
                onValueChange={(value) => setTaskParamsText(String(value ?? ""))}
                disabled={readOnly}
                aria-label="节点专属参数 JSON"
                className="w-full font-mono text-xs leading-5"
              />
            </section>
          </div>
        </div>
      )}
    </NodeEditorFrame>
  );
}
