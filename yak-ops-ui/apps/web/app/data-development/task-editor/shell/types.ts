import type { WorkflowTaskRecord } from "@/service/workflow/definition";
import type { NodeLocalParam } from "../common/node-resource-params/types";


  rawScript: string;
  resourceList: string[];
  localParams: NodeLocalParam[];
};

export type ShellEditorView = "form" | "json";

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
