import HttpUtils from "@/service/http/HttpUtils";

import type { WorkspaceCreatePayload, WorkspaceRecord } from "./types";

const WORKSPACE_API_PREFIX = "/api/v1/workspaces";

const WITHOUT_WORKSPACE_HEADER = {
  workspaceHeader: "omit" as const,
};

export const listWorkspaces = (): Promise<WorkspaceRecord[]> =>
  HttpUtils.getData<WorkspaceRecord[]>(WORKSPACE_API_PREFIX, WITHOUT_WORKSPACE_HEADER);

export const createWorkspace = (payload: WorkspaceCreatePayload): Promise<WorkspaceRecord> =>
  HttpUtils.postData<WorkspaceRecord>(WORKSPACE_API_PREFIX, payload, WITHOUT_WORKSPACE_HEADER);

export type { WorkspaceCreatePayload, WorkspaceRecord, WorkspaceRole } from "./types";
