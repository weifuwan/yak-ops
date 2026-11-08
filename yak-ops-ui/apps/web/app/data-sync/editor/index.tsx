import { pipelineScope } from "@/service/data-sync/native-pipeline";
import { DatabaseTaskEditor } from "./database-editor";
import { SingleTableTaskEditor } from "./single-table-editor";
import type { TaskEditorProps } from "./editor-types";

export function TaskEditor(props: TaskEditorProps) {
  const definition = props.initialDefinition || props.initialDraft!.definition;
  const scope = props.initialDraft?.scope || pipelineScope(definition);

  return scope === "DATABASE" ? (
    <DatabaseTaskEditor {...props} />
  ) : (
    <SingleTableTaskEditor {...props} />
  );
}

export { DatabaseTaskEditor } from "./database-editor";
export { SingleTableTaskEditor } from "./single-table-editor";
export type { TaskEditorProps } from "./editor-types";
