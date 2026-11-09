import { useState } from "react";

import { DataDevelopmentWorkbench } from "../workbench";
import {
  DevelopmentRail,
  type DataDevelopmentSection,
} from "./development-rail";
import { DevelopmentPanel } from "./development-panel";
import { PanelResizer } from "./panel-resizer";

const DEFAULT_PANEL_WIDTH = 280;

export function DataDevelopmentLayout() {
  const [activeSection, setActiveSection] =
    useState<DataDevelopmentSection>("development");
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  return (
    <div className="flex h-full min-h-0 w-full overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <DevelopmentRail
        activeSection={activeSection}
        panelCollapsed={panelCollapsed}
        onSectionChange={setActiveSection}
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
          />
        </div>
      </div>

      {panelCollapsed ? null : (
        <PanelResizer width={panelWidth} onResize={setPanelWidth} />
      )}

      <DataDevelopmentWorkbench />
    </div>
  );
}
