import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Textarea, toast } from "@yak-ops/yak-ui";

import {
  getWorkflowDefinition,
  type WorkflowTaskCommonSettingsPatch,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { NodeEditorFrame } from "../../node-editor-frame";
import type { LogicTaskRunConfigController } from "./logic-task-run-config";

export type LogicTaskParams = Record<string, unknown>;

export type LogicTaskFormProps = {
  params: LogicTaskParams;
  onChange: (next: LogicTaskParams) => void;
  disabled: boolean;
};

function formatTaskParams(value: unknown): string {
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value ?? {}, null, 2);
}

function parseTaskParams(value: string): LogicTaskParams {
  const parsed: unknown = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("节点专属参数必须是 JSON 对象");
  }
  return parsed as LogicTaskParams;
}

export function LogicTaskEditor({
  workflowCode,
  task,
  taskName,
  taskType,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
  children,
  validateParams,
  onParamsChange,
  runConfig,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  taskType: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
  runConfig: LogicTaskRunConfigController;
  children: (props: LogicTaskFormProps) => ReactNode;
  validateParams?: (params: LogicTaskParams) => string | null | Promise<string | null>;
  onParamsChange?: (params: LogicTaskParams) => void;
}) {
  const queryClient = useQueryClient();
  const [currentTask, setCurrentTask] = useState(task);
  const [taskParamsText, setTaskParamsText] = useState(() => formatTaskParams(task.taskParams));
  const [baselineTaskParamsText, setBaselineTaskParamsText] = useState(() => formatTaskParams(task.taskParams));
  const [params, setParams] = useState<LogicTaskParams>(() => {
    try {
      return parseTaskParams(formatTaskParams(task.taskParams));
    } catch {
      return {};
    }
  });
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const paramsText = formatTaskParams(task.taskParams);
    setCurrentTask(task);
    setTaskParamsText(paramsText);
    setBaselineTaskParamsText(paramsText);
    try {
      setParams(parseTaskParams(paramsText));
    } catch {
      setParams({});
    }
  }, [task]);

  const isDirty = runConfig.isDirty || taskParamsText !== baselineTaskParamsText;

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const jsonText = useMemo(() => {
    let taskParams: unknown = taskParamsText;
    try {
      taskParams = parseTaskParams(taskParamsText);
    } catch {
      // Keep the in-progress JSON visible in the preview.
    }
    return JSON.stringify(
      { workflowCode, task: { ...currentTask, ...runConfig.getPreviewPatch(), taskParams, taskType } },
      null,
      2,
    );
  }, [currentTask, runConfig, taskParamsText, taskType, workflowCode]);

  const updateParams = (next: LogicTaskParams) => {
    setParams(next);
    setTaskParamsText(JSON.stringify(next, null, 2));
    onParamsChange?.(next);
  };

  const handleSave = async () => {
    if (saving) return false;
    setSaving(true);
    try {
      let commonPatch: WorkflowTaskCommonSettingsPatch;
      let taskParams: LogicTaskParams;
      try {
        commonPatch = runConfig.getPatch();
        taskParams = parseTaskParams(taskParamsText);
        const runConfigError = runConfig.validate();
        if (runConfigError) {
          toast.warning(runConfigError);
          return false;
        }
        const validationError = await validateParams?.(taskParams);
        if (validationError) {
          toast.warning(validationError);
          return false;
        }
      } catch (error) {
        toast.warning(error instanceof Error ? error.message : "请检查节点配置");
        return false;
      }

      const nextTask = { ...currentTask, ...commonPatch, taskParams };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setParams(taskParams);
      runConfig.markSaved();
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
      const latestTask = dag.taskDefinitionList.find((item) => item.code === currentTask.code);
      if (!latestTask) throw new Error("节点不存在或已被删除");
      if (!onApplyTask(latestTask)) return;
      const paramsText = formatTaskParams(latestTask.taskParams);
      const nextParams = parseTaskParams(paramsText);
      setCurrentTask(latestTask);
      runConfig.refreshFromTask(latestTask);
      setTaskParamsText(paramsText);
      setBaselineTaskParamsText(paramsText);
      setParams(nextParams);
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
      runConfig={runConfig.content}
    >
      {activeView === "json" ? (
        <div className="min-h-0 flex-1 overflow-auto bg-[var(--yak-components-panel-bg)] p-4">
          <Textarea
            size="small"
            variant="outlined"
            rows={24}
            value={taskParamsText}
            onValueChange={(value) => {
              const nextText = String(value ?? "");
              setTaskParamsText(nextText);
              try {
                const nextParams = parseTaskParams(nextText);
                setParams(nextParams);
                onParamsChange?.(nextParams);
              } catch {
                // Keep the previous form state until the JSON becomes valid.
              }
            }}
            disabled={readOnly}
            aria-label="节点专属参数 JSON"
            className="w-full font-mono text-xs leading-5"
          />
          <pre className="mt-3 overflow-auto rounded-sm border border-[var(--yak-components-panel-border)] bg-white p-3 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
            {jsonText}
          </pre>
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-components-panel-bg)] p-4">
          <div className={"mx-auto space-y-3 " + (taskType === "CONDITIONS" ? "max-w-[1440px]" : "max-w-[1120px]")}>
            {children({ params, onChange: updateParams, disabled: readOnly })}
          </div>
        </div>
      )}
    </NodeEditorFrame>
  );
}
