import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "../workbench-context";
import { JavaTaskEditor } from "./java-task-editor";

type DraftTaskRecord = {
  nodeId: string;
  name: string;
  taskType: string;
};

type TaskEditorProps = {
  workflowCode: number;
  task?: WorkflowTaskRecord;
  draftTask?: DraftTaskRecord;
};

export function TaskEditor({
  workflowCode,
  task,
  draftTask,
}: TaskEditorProps) {
  const { activateTab } = useDataDevelopmentWorkbench();
  const taskName = task?.name ?? draftTask?.name ?? "未命名节点";
  const taskType = task?.taskType ?? draftTask?.taskType ?? "UNKNOWN";
  const payload = task
    ? {
        workflowCode,
        task,
      }
    : {
        workflowCode,
        draft: true,
        node: draftTask,
      };
  const isJavaTask = taskType.toUpperCase() === "JAVA";

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
          onBackToWorkflow={() => activateTab("workflow:" + workflowCode)}
        />
      ) : (
        <div className="min-h-0 flex-1 overflow-auto bg-[#f7f8fa] p-4">
          <pre className="min-w-max rounded-md border border-[var(--yak-components-border)] bg-white p-4 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
            {JSON.stringify(payload, null, 2)}
          </pre>
        </div>
      )}
    </section>
  );
}
