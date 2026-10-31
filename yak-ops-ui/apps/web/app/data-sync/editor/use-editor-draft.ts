import { useEffect, type MutableRefObject } from "react";
import { writeTaskDraft } from "@/service/data-sync/draft";
import type { DataSyncDraft, DataSyncTaskRecord, DataSyncTableMapping } from "@/service/data-sync";
import type { EditorForm } from "./types";
import { buildEditorPayload } from "./definition";
export function useEditorDraft(
  id: string | undefined,
  draftKey: string,
  draft: DataSyncDraft,
  form: EditorForm,
  replication: boolean,
  mappings: DataSyncTableMapping[],
  savedTask: MutableRefObject<DataSyncTaskRecord | undefined>,
) {
  useEffect(() => {
    if (id || savedTask.current) return;
    writeTaskDraft(draftKey, {
      sourceType: draft.sourceType,
      targetType: draft.targetType,
      sourceConnector: draft.sourceConnector,
      sinkConnector: draft.sinkConnector,
      sourceProducesChangelog: draft.sourceProducesChangelog,
      productType: draft.productType,
      steps: draft.steps,
      name: form.name,
      form: buildEditorPayload(form, draft, replication, mappings),
    });
  }, [
    id,
    draftKey,
    draft.sourceType,
    draft.targetType,
    draft.sourceConnector,
    draft.sinkConnector,
    draft.sourceProducesChangelog,
    draft.productType,
    draft.steps,
    form,
    replication,
    mappings,
  ]);
}
