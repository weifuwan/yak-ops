import type { TreeSelectOption } from "@yak-ops/yak-ui";

import type {
  ShellConfigTab,
  ShellLocalParam,
  ShellTaskForm,
  ShellTaskUpdateField,
} from "../types";
import { ShellConfigPanel } from "./shell-config-panel";
import { ShellScriptPanel } from "./shell-script-panel";

export function ShellEditorWorkspace({
  form,
  readOnly,
  activeTab,
  onActiveTabChange,
  updateField,
  updateLocalParam,
  resourceTree,
  resourceLoading,
  resourceError,
  onRetryResources,
}: {
  form: ShellTaskForm;
  readOnly: boolean;
  activeTab: ShellConfigTab;
  onActiveTabChange: (tab: ShellConfigTab) => void;
  updateField: ShellTaskUpdateField;
  updateLocalParam: (index: number, patch: Partial<ShellLocalParam>) => void;
  resourceTree: TreeSelectOption[];
  resourceLoading: boolean;
  resourceError: boolean;
  onRetryResources: () => void;
}) {
  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <ShellScriptPanel
        rawScript={form.rawScript}
        readOnly={readOnly}
        onChange={(value) => updateField("rawScript", value)}
      />
      <ShellConfigPanel
        form={form}
        readOnly={readOnly}
        activeTab={activeTab}
        onActiveTabChange={onActiveTabChange}
        updateField={updateField}
        updateLocalParam={updateLocalParam}
        resourceTree={resourceTree}
        resourceLoading={resourceLoading}
        resourceError={resourceError}
        onRetryResources={onRetryResources}
      />
    </div>
  );
}
