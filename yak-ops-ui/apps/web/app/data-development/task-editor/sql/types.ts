import type { WorkflowTaskRecord } from "@/service/workflow/definition";
import type { NodeLocalParam } from "../common/node-resource-params/types";

export type SqlSource = "SCRIPT" | "FILE";
export type SqlExecutionType = "0" | "1";

export type SqlTaskForm = {
  type: string;
  datasource: string;
  sql: string;
  sqlSource: SqlSource;
  sqlResource: string;
  sqlType: SqlExecutionType;
  sendEmail: boolean;
  displayRows: string;
  showType: string;
  connParams: string;
  preStatements: string[];
  postStatements: string[];
  groupId: string;
  title: string;
  limit: string;
  localParams: NodeLocalParam[];
};

export type SqlTaskEditorProps = {
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

export type SqlTaskUpdateField = <K extends keyof SqlTaskForm>(
  field: K,
  value: SqlTaskForm[K],
) => void;
