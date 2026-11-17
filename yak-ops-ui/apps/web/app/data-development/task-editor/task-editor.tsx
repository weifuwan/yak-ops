import { useCallback } from "react";
import { toast } from "@yak-ops/yak-ui";
import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "../workbench-context";
import { GenericTaskEditor } from "./generic-task-editor";
import { JavaTaskEditor } from "./java-task-editor";
import { ConditionsEditor } from "./logic/conditions";
import { SwitchEditor } from "./logic/switch";
import { remapSwitchCaseHandles } from "./logic/switch/adapter";
import { useWorkflowTaskOptions } from "./logic/shared/use-workflow-task-options";

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
  const isConditionsTask = taskType.toUpperCase() === "CONDITIONS";
  const isSwitchTask = taskType.toUpperCase() === "SWITCH";
  const isDraft = task.version <= 0 || task.id <= 0;
  const workflowTasks = useWorkflowTaskOptions(workflowCode, task.code);

  const applyTask = (nextTask: WorkflowTaskRecord) => {
    const store = getWorkflowStore(workflowCode);
    if (!store) {
      toast.error("工作流画布尚未就绪，请稍后再保存节点配置");
      return false;
    }
    if (taskType.toUpperCase() === "SWITCH") {
      const state = store.getState();
      const remappedEdges = remapSwitchCaseHandles(
        nodeId,
        task.taskParams,
        nextTask.taskParams,
        state.edges,
      );
      store.setState({ edges: remappedEdges });
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
      ) : isConditionsTask ? (
        <ConditionsEditor
          workflowCode={workflowCode}
          task={task}
          taskName={taskName}
          readOnly={readOnly}
          onApplyTask={applyTask}
          onDirtyChange={handleDirtyChange}
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
          workflowTasks={workflowTasks}
        />
      ) : isSwitchTask ? (
        <SwitchEditor
          workflowCode={workflowCode}
          task={task}
          taskName={taskName}
          readOnly={readOnly}
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
