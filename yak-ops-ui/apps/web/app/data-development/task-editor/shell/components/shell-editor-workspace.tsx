import type { ShellTaskForm, ShellTaskUpdateField } from "../types";
import { ShellScriptPanel } from "./shell-script-panel";

export function ShellEditorWorkspace({
  form,
  readOnly,
  updateField,
}: {
  form: ShellTaskForm;
  readOnly: boolean;
  updateField: ShellTaskUpdateField;
}) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <ShellScriptPanel
        rawScript={form.rawScript}
        readOnly={readOnly}
        onChange={(value) => updateField("rawScript", value)}
      />
    </div>
  );
}
