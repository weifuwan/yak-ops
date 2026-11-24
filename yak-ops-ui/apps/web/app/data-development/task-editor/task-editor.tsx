import { lazy, Suspense, useCallback } from "react";
import { toast } from "@yak-ops/yak-ui";
import { LoadingIndicator } from "@/app/components/loading-indicator";
import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "../workbench-context";
import { GenericTaskEditor } from "./generic-task-editor";
import { HttpTaskEditor } from "./http-task-editor";
import { JavaTaskEditor } from "./java-task-editor";
import { ConditionsEditor } from "./logic/conditions";
import { SwitchEditor } from "./logic/switch";
import { DependentEditor } from "./logic/dependent";
import { SubWorkflowEditor } from "./logic/sub-workflow";
import { remapSwitchCaseHandles } from "./logic/switch/adapter";
import { useWorkflowConditionGraph } from "./logic/shared/use-workflow-condition-graph";

const ShellTaskEditor = lazy(async () => {
  const module = await import("./shell");
  return { default: module.ShellTaskEditor };
});

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
  const isHttpTask = taskType.toUpperCase() === "HTTP";
  const isJavaTask = taskType.toUpperCase() === "JAVA";
  const isShellTask = taskType.toUpperCase() === "SHELL";
  const isConditionsTask = taskType.toUpperCase() === "CONDITIONS";
  const isSwitchTask = taskType.toUpperCase() === "SWITCH";
  const isDependentTask = taskType.toUpperCase() === "DEPENDENT";
  const isSubWorkflowTask = taskType.toUpperCase() === "SUB_WORKFLOW";
  const isDraft = task.version <= 0 || task.id <= 0;
  const conditionGraph = useWorkflowConditionGraph(workflowCode, nodeId, isConditionsTask);

  const applyTask = (nextTask: WorkflowTaskRecord) => {
    const store = getWorkflowStore(workflowCode);
    if (!store) {
      toast.error("工作流画布尚未就绪，请稍后再保存节点配置");
      return false;
    }
    const currentState = store.getState();
    const switchPreviewEdges =
      taskType.toUpperCase() === "SWITCH" &&
      currentState.previewNodeId === nodeId
        ? currentState.previewEdges
        : null;
    if (!currentState.updateTaskDefinition(nodeId, nextTask)) {
      toast.error("节点已从当前工作流移除，请重新打开节点");
      return false;
    }
    if (taskType.toUpperCase() === "SWITCH") {
      const remappedEdges = switchPreviewEdges ?? remapSwitchCaseHandles(
        nodeId,
        task.taskParams,
        nextTask.taskParams,
        store.getState().edges,
      );
      store.setState({
        edges: remappedEdges,
        previewEdges: null,
        previewNodeId: null,
      });
    }
    updateTaskTab("task:" + workflowCode + ":" + task.code, nextTask);
    return true;
  };

  return (
    <section
      aria-label={taskName + " 节点内容"}
      className="flex min-h-0 min-w-0 flex-1 flex-col bg-[var(--yak-components-panel-bg)]"
    >
      {isHttpTask ? (
        <HttpTaskEditor
          workflowCode={workflowCode}
          task={task}
          taskName={taskName}
          readOnly={readOnly}
          onApplyTask={applyTask}
          onDirtyChange={handleDirtyChange}
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
        />
      ) : isShellTask ? (
        <Suspense
          fallback={
            <div
              role="status"
              aria-label="正在加载 Shell 编辑器"
              className="flex min-h-0 flex-1 items-center justify-center"
            >
              <LoadingIndicator />
              <span className="sr-only">正在加载 Shell 编辑器</span>
            </div>
          }
        >
          <ShellTaskEditor
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
        </Suspense>
      ) : isJavaTask ? (
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
          {...conditionGraph}
        />
      ) : isSwitchTask ? (
        <SwitchEditor
          workflowCode={workflowCode}
          task={task}
          nodeId={nodeId}
          taskName={taskName}
          readOnly={readOnly}
          onApplyTask={applyTask}
          onDirtyChange={handleDirtyChange}
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
        />
      ) : isDependentTask ? (
        <DependentEditor
          workflowCode={workflowCode}
          task={task}
          taskName={taskName}
          readOnly={readOnly}
          onApplyTask={applyTask}
          onDirtyChange={handleDirtyChange}
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
        />
      ) : isSubWorkflowTask ? (
        <SubWorkflowEditor
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
