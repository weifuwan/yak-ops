import type { WorkflowDagRecord } from "@/service/workflow/definition";
import type {
  WorkflowFailureStrategy,
  WorkflowMissedFirePolicy,
  WorkflowPriority,
  WorkflowWarningType,
} from "@/service/workflow/management";

export type PanelKey = "properties" | "schedule" | "history" | "versions" | "ops";
export type ScheduleSection = "strategy" | "time";
export type ExecutionType = "PARALLEL" | "SERIAL_WAIT" | "SERIAL_DISCARD" | "SERIAL_PRIORITY";
export type GlobalParameterType =
  | "VARCHAR"
  | "INTEGER"
  | "LONG"
  | "FLOAT"
  | "DOUBLE"
  | "DATE"
  | "TIME"
  | "TIMESTAMP"
  | "BOOLEAN"
  | "LIST"
  | "FILE";

export interface GlobalParameter {
  prop: string;
  direct: "IN" | "OUT";
  type: GlobalParameterType;
  value: string;
  sensitive: boolean;
}

export interface WorkflowPropertyForm {
  name: string;
  description: string;
  timeout: string;
  executionType: ExecutionType;
}

export interface WorkflowScheduleForm {
  startTime: string;
  endTime: string;
  timezoneId: string;
  crontab: string;
  missedFirePolicy: WorkflowMissedFirePolicy;
  failureStrategy: WorkflowFailureStrategy;
  warningType: WorkflowWarningType;
  warningGroupId: number;
  workflowInstancePriority: WorkflowPriority;
  workerGroup: string;
  tenantCode: string;
}

export interface WorkflowSidePanelProps {
  workflowCode: number;
  dag: WorkflowDagRecord;
  onVersionSwitched: () => void;
}
