import type { ResourceItemRecord } from "@/service/resources";

export type ResourceTypeFilter =
  | "ALL"
  | "FOLDER"
  | "FILE"
  | "SQL"
  | "JAR"
  | "PYTHON"
  | "SHELL"
  | "IMAGE"
  | "DOCUMENT"
  | "OTHER";

export type ResourceSort = "name" | "updated" | "size";
export type ViewMode = "list" | "grid";

export type ResourceDialog =
  | { kind: "create" }
  | { kind: "rename"; item: ResourceItemRecord }
  | { kind: "delete"; item: ResourceItemRecord }
  | { kind: "delete-selected"; items: ResourceItemRecord[] };

export type ResourceEditorMode = "preview" | "edit";
