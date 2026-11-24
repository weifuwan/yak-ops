import { ScriptEditorPanel } from "./script-editor-panel";
import type { ScriptTaskForm, ScriptTaskUpdateField, ScriptLanguage } from "./types";

export function ScriptEditorWorkspace({
  form,
  readOnly,
  updateField,
  language,
  scriptLabel,
}: {
  form: ScriptTaskForm;
  readOnly: boolean;
  updateField: ScriptTaskUpdateField;
  language: ScriptLanguage;
  scriptLabel: string;
}) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <ScriptEditorPanel
        rawScript={form.rawScript}
        readOnly={readOnly}
        onChange={(value) => updateField("rawScript", value)}
        language={language}
        scriptLabel={scriptLabel}
      />
    </div>
  );
}
