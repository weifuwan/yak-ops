import { pipelineScope } from "@/service/data-sync/native-pipeline";
import { DatabaseTaskEditor } from "./database-editor";
import { MultiTableTaskEditor } from "./multi-table-editor";
import { SingleTableTaskEditor } from "./single-table-editor";
import type { TaskEditorProps } from "./editor-types";

export function TaskEditor(props: TaskEditorProps) {
  const definition = props.initialDefinition || props.initialDraft!.definition;
  const scope = props.initialDraft?.scope || pipelineScope(definition);

  if (scope === "DATABASE") return <DatabaseTaskEditor {...props} />;
  if (scope === "MULTI_TABLE") return <MultiTableTaskEditor {...props} />;
  return <SingleTableTaskEditor {...props} />;
}

export { DatabaseTaskEditor } from "./database-editor";
export { MultiTableTaskEditor } from "./multi-table-editor";
export { SingleTableTaskEditor } from "./single-table-editor";
export type { TaskEditorProps } from "./editor-types";
