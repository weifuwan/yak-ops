import { useState } from "react";
import { RefreshCw, X } from "lucide-react";

import { PanelResizer } from "../../layout/panel-resizer";
import { PANEL_ITEMS } from "./workflow-side-panel/shared/constants";
import type {
  PanelKey,
  WorkflowSidePanelProps,
} from "./workflow-side-panel/shared/types";
import { useWorkflowSidePanelController } from "./workflow-side-panel/hooks/useWorkflowSidePanelController";
import { PropertiesPanel } from "./workflow-side-panel/properties/PropertiesPanel";
import { SchedulePanel } from "./workflow-side-panel/schedule/SchedulePanel";
import { WorkflowRunsPanel } from "./workflow-side-panel/instances/WorkflowRunsPanel";
import { VersionsPanel } from "./workflow-side-panel/versions/VersionsPanel";

const WORKFLOW_PANEL_WIDTH_STORAGE_KEY = "yak-ops:workflow-side-panel-width";
const DEFAULT_PANEL_WIDTH = 360;
const MIN_PANEL_WIDTH = 300;
const MAX_PANEL_WIDTH = 560;

const clampPanelWidth = (width: number) =>
  Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, width));

const readStoredPanelWidth = () => {
  if (typeof window === "undefined") return DEFAULT_PANEL_WIDTH;

  try {
    const storedWidth = window.localStorage.getItem(WORKFLOW_PANEL_WIDTH_STORAGE_KEY);
    if (!storedWidth) return DEFAULT_PANEL_WIDTH;

    const parsedWidth = Number(storedWidth);
    return Number.isFinite(parsedWidth)
      ? clampPanelWidth(parsedWidth)
      : DEFAULT_PANEL_WIDTH;
  } catch {
    return DEFAULT_PANEL_WIDTH;
  }
};

export function WorkflowSidePanel({
  workflowCode,
  dag,
  onVersionSwitched,
}: WorkflowSidePanelProps) {
  const [activePanel, setActivePanel] = useState<PanelKey | null>("properties");
  const [panelWidth, setPanelWidth] = useState(readStoredPanelWidth);
  const controller = useWorkflowSidePanelController({
    workflowCode,
    dag,
    activePanel,
    onVersionSwitched,
  });
  const activeTitle = PANEL_ITEMS.find((item) => item.key === activePanel)?.label;

  const handlePanelResize = (width: number) => {
    const nextWidth = clampPanelWidth(width);
    setPanelWidth(nextWidth);

    try {
      window.localStorage.setItem(
        WORKFLOW_PANEL_WIDTH_STORAGE_KEY,
        String(nextWidth),
      );
    } catch {
      // Keep the resized width for the current session if storage is unavailable.
    }
  };

  return (
    <aside className="flex h-full min-h-0 shrink-0 bg-white">
      {activePanel ? (
        <>
          <PanelResizer
            width={panelWidth}
            minWidth={MIN_PANEL_WIDTH}
            maxWidth={MAX_PANEL_WIDTH}
            resizeDirection="left"
            ariaLabel="调整工作流侧边栏宽度"
            onResize={handlePanelResize}
          />
          <section
            className="flex h-full min-h-0 max-w-[calc(100vw-64px)] flex-col"
            style={{ width: panelWidth }}
          >
            <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
              <div className="flex h-6 items-center">
                <span className="text-sm font-semibold text-[#303846]">{activeTitle}</span>
              </div>
              <div className="inline-flex items-center gap-1">
                <button
                  type="button"
                  title="刷新当前面板"
                  onClick={() => void controller.refreshActivePanel()}
                  className="inline-flex items-center gap-1 rounded px-1 py-1 text-xs text-[#687385] transition-colors hover:bg-[#f3f4f6] hover:text-[#303846]"
                >
                  <RefreshCw size={13} />
                  刷新
                </button>
                <span aria-hidden="true" className="mx-1 h-4 w-px bg-[#cbd0d8]" />
                <button
                  type="button"
                  title="收起面板"
                  aria-label="收起面板"
                  onClick={() => setActivePanel(null)}
                  className="flex size-6 items-center justify-center rounded text-[#8b93a1] transition-colors hover:bg-[#f0f2f5] hover:text-[#303846]"
                >
                  <X size={14} />
                </button>
              </div>
            </header>
            {activePanel === "properties" ? (
              <div className="min-h-0 flex-1 overflow-y-auto">
                <PropertiesPanel controller={controller} />
              </div>
            ) : null}
            {activePanel === "schedule" ? (
              <SchedulePanel controller={controller} />
            ) : null}
            {activePanel === "history" ? (
              <div className="min-h-0 flex-1 overflow-y-auto">
                <WorkflowRunsPanel mode="history" controller={controller} />
              </div>
            ) : null}
            {activePanel === "versions" ? (
              <div className="min-h-0 flex-1 overflow-y-auto">
                <VersionsPanel controller={controller} />
              </div>
            ) : null}
            {activePanel === "ops" ? (
              <div className="min-h-0 flex-1 overflow-y-auto">
                <WorkflowRunsPanel mode="ops" controller={controller} />
              </div>
            ) : null}
          </section>
        </>
      ) : null}
      <nav
        aria-label="工作流配置面板"
        className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white"
      >
        {PANEL_ITEMS.map((item) => {
          const selected = activePanel === item.key;
          return (
            <button
              key={item.key}
              type="button"
              title={item.label}
              aria-label={item.label}
              aria-pressed={selected}
              onClick={() =>
                setActivePanel((current) => (current === item.key ? null : item.key))
              }
              className={[
                "relative flex w-full shrink-0 cursor-pointer items-center justify-center border-b border-[#e5e7eb] px-0 py-2 text-xs leading-[18px] transition-colors first:border-t first:border-[#e5e7eb] hover:bg-[#f7f9fc] hover:text-[#315efb]",
                selected ? "bg-white text-[#315efb]" : "bg-white text-[#7c8796]",
              ].join(" ")}
            >
              {selected ? (
                <span
                  aria-hidden="true"
                  className="absolute inset-y-0 left-0 z-[1] w-0.5 bg-[#315efb]"
                />
              ) : null}
              <span className="[writing-mode:vertical-rl] [text-orientation:upright] [word-break:keep-all] whitespace-nowrap tracking-[2px]">
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
