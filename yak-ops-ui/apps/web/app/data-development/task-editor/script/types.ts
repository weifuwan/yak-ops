import type { WorkflowTaskRecord } from "@/service/workflow/definition";
import type { NodeLocalParam } from "../common/node-resource-params/types";

export type ScriptTaskType = "SHELL" | "PYTHON";
export type ScriptLanguage = "shell" | "python";

export type ScriptTaskForm = {
  rawScript: string;
  resourceList: string[];
  localParams: NodeLocalParam[];
};

export type ScriptEditorView = "form" | "json";

export type ScriptTaskEditorProps = {
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

export type ScriptTaskUpdateField = <K extends keyof ScriptTaskForm>(
  field: K,
  value: ScriptTaskForm[K],
) => void;
