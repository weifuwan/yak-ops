/** Public Task Execution responses from the Boot API. */
export type TaskInstanceStatus =
  | "SUBMITTED"
  | "LAUNCHING"
  | "RUNNING"
  | "CANCELLING"
  | "RECONCILING"
  | "FINISHED"
  | "FAILED"
  | "CANCELED";

export interface TaskSubmissionResult {
  instanceId: string;
  definitionVersion: number;
  status: TaskInstanceStatus;
  replayed: boolean;
}

export interface TaskInstanceRecord {
  id: string;
  definitionId: string;
  definitionVersion: number;
  debug: boolean;
  taskType: string;
  triggerType: "MANUAL" | "SCHEDULE" | "WORKFLOW";
  scheduleId?: string | null;
  scheduleRevision?: number | null;
  scheduledTime?: string | null;
  status: TaskInstanceStatus;
  progressPercent?: number | null;
  executionReport?: string | null;
  runtimeEngine?: string | null;
  runtimeId?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  failureCode?: string | null;
  createTime?: string | null;
}

export interface TaskLogPageRecord {
  content: string;
  nextCursor: string;
  hasMore: boolean;
}

export interface TaskInstanceDefinitionRecord {
  definitionId: string;
  version: number;
  name: string;
  taskType: string;
  parameterSchemaVersion: number;
  parameters: Record<string, unknown>;
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
  definitionId?: string;
  status?: TaskInstanceStatus;
  taskType?: string;
}
