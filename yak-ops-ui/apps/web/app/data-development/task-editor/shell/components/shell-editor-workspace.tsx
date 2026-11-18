import { useEffect, useRef, useState } from "react";
import type { TreeSelectOption } from "@yak-ops/yak-ui";

import { PanelHeightResizer } from "../../../layout/panel-height-resizer";
import type {
  ShellConfigTab,
  ShellLocalParam,
  ShellTaskForm,
  ShellTaskUpdateField,
} from "../types";
import { ShellConfigPanel } from "./shell-config-panel";
import { ShellScriptPanel } from "./shell-script-panel";

const MIN_CONFIG_PANEL_HEIGHT = 220;
const DEFAULT_CONFIG_PANEL_HEIGHT = 286;
const MAX_CONFIG_PANEL_HEIGHT = 520;
const MIN_SCRIPT_PANEL_HEIGHT = 310;
const RESIZER_HEIGHT = 8;

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
  const workspaceRef = useRef<HTMLDivElement>(null);
  const [workspaceHeight, setWorkspaceHeight] = useState(0);
  const [configPanelHeight, setConfigPanelHeight] = useState(DEFAULT_CONFIG_PANEL_HEIGHT);

  useEffect(() => {
    const workspace = workspaceRef.current;
    if (!workspace) return;

    const updateWorkspaceHeight = () => {
      setWorkspaceHeight(Math.round(workspace.getBoundingClientRect().height));
    };

    const observer = new ResizeObserver(updateWorkspaceHeight);
    observer.observe(workspace);
    updateWorkspaceHeight();

    return () => observer.disconnect();
  }, []);

  const maxConfigPanelHeight =
    workspaceHeight > 0
      ? Math.max(
          MIN_CONFIG_PANEL_HEIGHT,
          Math.min(
            MAX_CONFIG_PANEL_HEIGHT,
            workspaceHeight - MIN_SCRIPT_PANEL_HEIGHT - RESIZER_HEIGHT,
          ),
        )
      : MAX_CONFIG_PANEL_HEIGHT;
  const visibleConfigPanelHeight = Math.min(configPanelHeight, maxConfigPanelHeight);

  return (
    <div
      ref={workspaceRef}
      className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-white"
    >
      <ShellScriptPanel
        rawScript={form.rawScript}
        readOnly={readOnly}
        onChange={(value) => updateField("rawScript", value)}
      />
      <PanelHeightResizer
        height={visibleConfigPanelHeight}
        minHeight={MIN_CONFIG_PANEL_HEIGHT}
        maxHeight={maxConfigPanelHeight}
        onResize={setConfigPanelHeight}
        ariaLabel="调整 Shell 脚本与参数配置区域高度"
      />
      <ShellConfigPanel
        form={form}
        height={visibleConfigPanelHeight}
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
