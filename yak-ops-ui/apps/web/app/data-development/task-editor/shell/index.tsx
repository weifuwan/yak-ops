import { ScriptTaskEditor } from "../script";
import type { ShellTaskEditorProps } from "./types";

export function ShellTaskEditor(props: ShellTaskEditorProps) {
  return (
    <ScriptTaskEditor
      {...props}
      taskType="SHELL"
      language="shell"
      scriptLabel="Shell 脚本"
    />
  );
}
