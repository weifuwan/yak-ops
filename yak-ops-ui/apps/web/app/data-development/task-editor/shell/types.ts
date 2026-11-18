import type { WorkflowTaskRecord } from "@/service/workflow/definition";

export type ShellLocalParam = {
  prop: string;
  direct: "IN" | "OUT";
  type: string;
  value: string;
  sensitive: boolean;
};

export type ShellTaskForm = {
  rawScript: string;
  resourceList: string[];
  localParams: ShellLocalParam[];
};

export type ShellEditorView = "form" | "json";
export type ShellConfigTab = "parameters" | "resources";

export type ShellTaskEditorProps = {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  isDraft: boolean;
  readOnly: boolean;
  canRun: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
};

export type ShellTaskUpdateField = <K extends keyof ShellTaskForm>(
  field: K,
  value: ShellTaskForm[K],
) => void;
