import { Braces, FileCode2 } from "lucide-react";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

type TaskEditorProps = {
  workflowCode: number;
  task: WorkflowTaskRecord;
};

export function TaskEditor({
  workflowCode,
  task,
}: TaskEditorProps) {
  const payload = {
    workflowCode,
    task,
  };

  return (
    <section
      aria-label={`${task.name} 节点内容`}
      className="flex min-h-0 flex-1 flex-col bg-[var(--yak-components-panel-bg)]"
    >
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-border)] px-4">
        <div className="flex min-w-0 items-center gap-2">
          <FileCode2
            size={15}
            aria-hidden="true"
            className="shrink-0 text-[var(--yak-color-primary)]"
          />
          <span className="truncate text-sm font-medium">
            {task.name}
          </span>
          <span className="shrink-0 rounded bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[10px] text-[var(--yak-components-muted-text)]">
            {task.taskType}
          </span>
        </div>

        <div className="flex items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]">
          <Braces size={13} aria-hidden="true" />
          JSON
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto bg-[#f7f8fa] p-4">
        <pre className="min-w-max rounded-md border border-[var(--yak-components-border)] bg-white p-4 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
          {JSON.stringify(payload, null, 2)}
        </pre>
      </div>
    </section>
  );
}
