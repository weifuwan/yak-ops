import { ScriptEditorPanel } from "./script-editor-panel";
import type { ScriptTaskForm, ScriptLanguage } from "./types";

export function ScriptEditorWorkspace({
  form,
  language,
  scriptLabel,
}: {
  form: ScriptTaskForm;
  language: ScriptLanguage;
  scriptLabel: string;
}) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <ScriptEditorPanel
        rawScript={form.rawScript}
        language={language}
        scriptLabel={scriptLabel}
      />
    </div>
  );
}
