import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { toast } from "@yak-ops/yak-ui";
import {
  getWorkflowDefinition,
  updateWorkflowTaskDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { NodeEditorFrame } from "./node-editor-frame";
import {
  TaskSchedulingSection,
  useTaskScheduling,
} from "./task-scheduling-section";

type DraftTaskRecord = {
  nodeId: string;
  name: string;
  taskType: string;
};

export function GenericTaskEditor({
  workflowCode,
  task,
  draftTask,
  taskName,
  taskType,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task?: WorkflowTaskRecord;
  draftTask?: DraftTaskRecord;
  taskName: string;
  taskType: string;
  onBackToWorkflow: () => void;
}) {
  const queryClient = useQueryClient();
  const [currentTask, setCurrentTask] = useState(task);
  const [activeView, setActiveView] = useState<"form" | "json">("form");
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const scheduling = useTaskScheduling(currentTask);

  useEffect(() => {
    setCurrentTask(task);
  }, [task]);

  const isDirty = scheduling.isDirty;
  const normalizedSettings = scheduling.getNormalizedSettings();
  const jsonText = useMemo(
    () =>
      JSON.stringify(
        currentTask
          ? {
              workflowCode,
              task: {
                ...currentTask,
                taskType: currentTask.taskType || taskType,
                taskGroupId: normalizedSettings.taskGroupId,
                taskGroupPriority: normalizedSettings.taskGroupPriority,
              },
            }
          : {
              workflowCode,
              draft: true,
              node: draftTask,
              taskGroupId: normalizedSettings.taskGroupId,
              taskGroupPriority: normalizedSettings.taskGroupPriority,
            },
        null,
        2,
      ),
    [
      currentTask,
      draftTask,
      normalizedSettings.taskGroupId,
      normalizedSettings.taskGroupPriority,
      taskType,
      workflowCode,
    ],
  );

  const handleSave = async () => {
    if (!currentTask) return;
    const validationError = scheduling.getValidationError();
    if (validationError) {
      toast.warning(validationError);
      return;
    }

    const nextSettings = scheduling.getNormalizedSettings();
    setSaving(true);
    try {
      await updateWorkflowTaskDefinition(workflowCode, currentTask.code, {
        taskGroupId: nextSettings.taskGroupId,
        taskGroupPriority: nextSettings.taskGroupPriority,
      });
      setCurrentTask((current) =>
        current
          ? {
              ...current,
              taskGroupId: nextSettings.taskGroupId,
              taskGroupPriority: nextSettings.taskGroupPriority,
            }
          : current,
      );
      scheduling.markSaved(nextSettings);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-definition"],
      });
      toast.success("节点调度配置已保存");
    } catch (error) {
      toast.error("保存节点调度配置失败", {
        description:
          error instanceof Error ? error.message : "请稍后重试。",
      });
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
      if (scheduling.projectCode) {
        await scheduling.taskGroupQuery.refetch();
      }
      if (!currentTask) {
        toast.success("任务组列表已刷新");
        return;
      }

      const dag = await getWorkflowDefinition(workflowCode);
      const latestTask = dag.taskDefinitionList.find(
        (item) => item.code === currentTask.code,
      );
      if (!latestTask) throw new Error("节点不存在或已被删除");
      setCurrentTask(latestTask);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-definition"],
      });
      toast.success("节点配置已刷新");
    } catch (error) {
      toast.error("刷新失败", {
        description:
          error instanceof Error ? error.message : "请稍后重试。",
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
      refreshing={refreshing || scheduling.taskGroupQuery.isFetching}
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
            <TaskSchedulingSection
              settings={scheduling.settings}
              onChange={scheduling.setSettings}
              projectCode={scheduling.projectCode}
              taskGroups={scheduling.taskGroups}
              isFetching={scheduling.taskGroupQuery.isFetching}
              isError={scheduling.taskGroupQuery.isError}
            />
            <section className="rounded-md border border-[var(--yak-components-panel-border)] bg-white p-3">
              <h3 className="mb-2 text-xs font-semibold text-[var(--yak-components-panel-text)]">
                节点参数
              </h3>
              <p className="mb-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                当前节点类型暂未提供可视化参数编辑器，原有节点参数保持不变。
              </p>
              <pre className="max-h-64 overflow-auto rounded-sm bg-[var(--yak-color-hover)] p-3 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
                {JSON.stringify(currentTask?.taskParams ?? {}, null, 2)}
              </pre>
            </section>
          </div>
        </div>
      )}
    </NodeEditorFrame>
  );
}
