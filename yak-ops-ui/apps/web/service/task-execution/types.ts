/** Public Task Execution responses from the Boot API. */
export type TaskInstanceStatus =
  | "SUBMITTED"
  | "RUNNING"
  | "CANCELLING"
  | "RECONCILING"
  | "FINISHED"
  | "FAILED"
  | "CANCELED";

export type TaskAttemptStatus =
  | "PENDING"
  | "LAUNCHING"
  | "RUNNING"
  | "FINISHED"
  | "FAILED"
  | "CANCELED"
  | "INTERRUPTED";

export interface TaskSubmissionResult {
  instanceId: string;
  attemptId: string;
  definitionVersion: number;
  status: TaskInstanceStatus;
  replayed: boolean;
}

export interface TaskInstanceRecord {
  id: string;
  definitionId: string;
  definitionVersion: number;
  taskType: string;
  status: TaskInstanceStatus;
  startTime?: string | null;
  endTime?: string | null;
  failureCode?: string | null;
  createTime?: string | null;
}

export interface TaskAttemptRecord {
  id: string;
  attemptNumber: number;
  status: TaskAttemptStatus;
  runtimeEngine?: string | null;
  runtimeId?: string | null;
  logReference?: string | null;
  failureCode?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}

export interface TaskDefinitionVersionRecord {
  definitionId: string;
  version: number;
  parameterSchemaVersion: number;
  parameters: string;
  createTime?: string | null;
}

export interface TaskInstancePageResult {
  bizData: TaskInstanceRecord[];
  pagination: {
    pageNo: number;
    pageSize: number;
    total: number;
    pages: number;
  };
}

export interface TaskInstancePageParams {
  pageNo: number;
  pageSize: number;
}
