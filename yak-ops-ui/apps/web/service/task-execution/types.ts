/** Public Task Execution responses from the Boot API. */
export type TaskInstanceStatus =
  | "SUBMITTED_SUCCESS"
  | "DISPATCH"
  | "RUNNING_EXECUTION"
  | "DELAY_EXECUTION"
  | "NEED_FAULT_TOLERANCE"
  | "SUCCESS"
  | "FORCED_SUCCESS"
  | "FAILURE"
  | "PAUSE"
  | "KILL";

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
  debug?: boolean;
  taskType: string;
  triggerType?: string;
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
  parameters: Record<string, unknown>;
}

/** Legacy task-execution API page contract; DataSync and DataSource use PageInfo instead. */
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
