import { useCallback } from "react";
import { toast } from "@yak-ops/yak-ui";
import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "../workbench-context";
import { GenericTaskEditor } from "./generic-task-editor";
import { JavaTaskEditor } from "./java-task-editor";

type TaskEditorProps = {
  workflowCode: number;
  task: WorkflowTaskRecord;
  nodeId: string;
  readOnly: boolean;
};

export function TaskEditor({ workflowCode, task, nodeId, readOnly }: TaskEditorProps) {
  const { activateTab, getWorkflowStore, setTabDirty, updateTaskTab } = useDataDevelopmentWorkbench();
  const tabId = "task:" + workflowCode + ":" + task.code;
  const handleDirtyChange = useCallback(
    (dirty: boolean) => setTabDirty(tabId, dirty),
    [setTabDirty, tabId],
  );
  const taskName = task.name || "未命名节点";
  const taskType = task.taskType || "UNKNOWN";
  const isJavaTask = taskType.toUpperCase() === "JAVA";
  const isDraft = task.version <= 0 || task.id <= 0;

  const applyTask = (nextTask: WorkflowTaskRecord) => {
    const store = getWorkflowStore(workflowCode);
    if (!store) {
      toast.error("工作流画布尚未就绪，请稍后再保存节点配置");
      return false;
    }
    if (!store.getState().updateTaskDefinition(nodeId, nextTask)) {
      toast.error("节点已从当前工作流移除，请重新打开节点");
      return false;
    }
    updateTaskTab("task:" + workflowCode + ":" + task.code, nextTask);
    return true;
  };

  return (
    <section
      aria-label={taskName + " 节点内容"}
      className="flex min-h-0 flex-1 flex-col bg-[var(--yak-components-panel-bg)]"
    >
      {isJavaTask ? (
        <JavaTaskEditor
          workflowCode={workflowCode}
          task={task}
          taskName={taskName}
          isDraft={isDraft}
          readOnly={readOnly}
          canRun={readOnly && !isDraft}
          onApplyTask={applyTask}
          onDirtyChange={handleDirtyChange}
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
        />
      ) : (
        <GenericTaskEditor
          workflowCode={workflowCode}
          task={task}
          taskName={taskName}
          taskType={taskType}
          readOnly={readOnly}
          onApplyTask={applyTask}
          onDirtyChange={handleDirtyChange}
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
        />
      )}
    </section>
  );
}
