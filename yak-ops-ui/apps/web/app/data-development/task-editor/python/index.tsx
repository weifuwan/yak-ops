import { ScriptTaskEditor } from "../script";
import type { PythonTaskEditorProps } from "./types";

export function PythonTaskEditor(props: PythonTaskEditorProps) {
  return (
    <ScriptTaskEditor
      {...props}
      taskType="PYTHON"
      language="python"
      scriptLabel="Python 脚本"
    />
  );
}
