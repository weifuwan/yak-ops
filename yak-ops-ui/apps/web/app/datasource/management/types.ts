export type DataSourceViewMode = "grid" | "list";

export interface DataSourcePermissions {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canTest: boolean;
}

export interface DataSourceActionState {
  testingId: string;
  editingId: string;
}
