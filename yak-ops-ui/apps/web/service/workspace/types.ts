export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER";

export interface WorkspaceRecord {
  id: string;
  name: string;
  description?: string | null;
  role: WorkspaceRole;
  roleName?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface WorkspaceCreatePayload {
  name: string;
  description?: string;
}
