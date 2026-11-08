import type { TaskDefinitionRecord } from "@/service/data-sync";
import type { DataSourceRecord } from "@/service/datasource";
import type { PipelineDefinition } from "@/service/data-sync/native-pipeline";

export interface TaskEditorProps {
  initialDraft?: import("@/service/data-sync/native-draft").NativeSyncDraft;
  initialTask?: TaskDefinitionRecord;
  initialDefinition?: PipelineDefinition;
  connections?: DataSourceRecord[];
  executionLocked?: boolean;
  executionLockMessage?: string;
}

export interface AuthoredTask {
  name: string;
  remark: string;
  definition: PipelineDefinition;
}

export type EditorValidator = () => void;
