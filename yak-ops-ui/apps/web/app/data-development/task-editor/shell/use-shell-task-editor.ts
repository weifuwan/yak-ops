import { useScriptTaskEditor } from "../script/use-script-task-editor";
import type { ShellTaskEditorProps } from "./types";

export function useShellTaskEditor(props: ShellTaskEditorProps) {
  return useScriptTaskEditor({ ...props, taskType: "SHELL" });
}
