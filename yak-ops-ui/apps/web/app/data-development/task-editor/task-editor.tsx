import { Braces, Settings2 } from "lucide-react";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { TaskTypeBadgeIcon } from "../workflow-editor/components/task-type-badge-icon";
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
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-border)] px-4">
        <div className="flex min-w-0 items-center gap-2">
          <TaskTypeBadgeIcon taskType={taskType} size={18} />
          <span className="truncate text-sm font-medium">
            {taskName}
          </span>
          <span className="shrink-0 rounded bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">
            {taskType}
          </span>
        </div>

        {isJavaTask ? (
          <div className="flex items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]">
            <Settings2 size={13} aria-hidden="true" />
            Java 配置
          </div>
        ) : (
          <div className="flex items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]">
            <Braces size={13} aria-hidden="true" />
            JSON
          </div>
        )}
      </div>

      {isJavaTask ? (
        <JavaTaskEditor workflowCode={workflowCode} task={task} />
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
