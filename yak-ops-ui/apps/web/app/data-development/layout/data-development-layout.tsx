import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { DataDevelopmentWorkbench } from "../workbench";
import {
  DevelopmentRail,
  type DataDevelopmentSection,
} from "./development-rail";
import { DevelopmentPanel } from "./development-panel";
import { PanelResizer } from "./panel-resizer";

const DEFAULT_PANEL_WIDTH = 280;

const FULL_PAGE_SECTIONS = new Set<DataDevelopmentSection>([
  "backfill",
  "lineage",
  "task-instances",
  "parameters",
]);

export function DataDevelopmentLayout() {
  const navigate = useNavigate();
  const [activeSection, setActiveSection] =
    useState<DataDevelopmentSection>("development");
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const [panelCollapsed, setPanelCollapsed] = useState(false);
  const [resourceDirectoryPaths, setResourceDirectoryPaths] = useState<string[]>([]);

  const handleSectionChange = (section: DataDevelopmentSection) => {
    setActiveSection(section);
    setPanelCollapsed(FULL_PAGE_SECTIONS.has(section));
  };

  return (
    <div className="flex h-full min-h-0 w-full overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <DevelopmentRail
        activeSection={activeSection}
        panelCollapsed={panelCollapsed}
        onSectionChange={handleSectionChange}
        onOpenAgent={() => navigate("/data-agent")}
        onTogglePanel={() => setPanelCollapsed((collapsed) => !collapsed)}
      />

      <div
        aria-hidden={panelCollapsed}
        className="shrink-0 overflow-hidden transition-[width] duration-200 ease-out motion-reduce:transition-none"
        style={{ width: panelCollapsed ? 0 : panelWidth }}
      >
        <div
          className={[
            "h-full transition-[opacity,transform] duration-200 ease-out motion-reduce:transition-none",
            panelCollapsed
              ? "-translate-x-2 opacity-0"
              : "translate-x-0 opacity-100",
          ].join(" ")}
          style={{ width: panelWidth }}
        >
          <DevelopmentPanel
            activeSection={activeSection}
            width={panelWidth}
            resourceDirectoryPaths={resourceDirectoryPaths}
            onResourceDirectoryPathsChange={setResourceDirectoryPaths}
          />
        </div>
      </div>

      {panelCollapsed ? null : (
        <PanelResizer width={panelWidth} onResize={setPanelWidth} />
      )}

      <DataDevelopmentWorkbench activeSection={activeSection} />
    </div>
  );
}
