import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { toast } from "@yak-ops/yak-ui";
import {
  getWorkflowDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { NodeEditorFrame } from "./node-editor-frame";

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
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    setCurrentTask(task);
  }, [task]);

  const jsonText = useMemo(
    () =>
      JSON.stringify(
        currentTask
          ? {
              workflowCode,
              task: {
                ...currentTask,
                taskType: currentTask.taskType || taskType,
              },
            }
          : {
              workflowCode,
              draft: true,
              node: draftTask,
            },
        null,
        2,
      ),
    [currentTask, draftTask, taskType, workflowCode],
  );

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      if (!currentTask) {
        toast.success("节点信息已刷新");
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
      isDirty={false}
      saving={false}
      running={false}
      stopping={false}
      refreshing={refreshing}
      lastWorkflowInstanceId={null}
      canRun={false}
      onRun={() => undefined}
      onStop={() => undefined}
      onSave={() => undefined}
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
          <div className="mx-auto max-w-[1120px]">
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
