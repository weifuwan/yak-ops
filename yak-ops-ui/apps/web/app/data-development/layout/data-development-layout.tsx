import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { DataDevelopmentWorkbench } from "../workbench";
import {
  DevelopmentRail,
  type DataDevelopmentSection,
} from "./development-rail";
import { DevelopmentPanel } from "./development-panel";
import { PanelResizer } from "./panel-resizer";

const DEFAULT_PANEL_WIDTH = 280;

const DATA_DEVELOPMENT_SECTIONS = new Set<DataDevelopmentSection>([
  "development",
  "manual",
  "backfill",
  "lineage",
  "task-instances",
  "parameters",
  "catalog",
  "resource",
  "history",
  "operation-check",
]);

const resolveActiveSection = (search: string): DataDevelopmentSection => {
  const section = new URLSearchParams(search).get("section");
  return section && DATA_DEVELOPMENT_SECTIONS.has(section as DataDevelopmentSection)
    ? (section as DataDevelopmentSection)
    : "development";
};

export function DataDevelopmentLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [activeSection, setActiveSection] = useState<DataDevelopmentSection>(() =>
    resolveActiveSection(location.search),
  );
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const [panelCollapsed, setPanelCollapsed] = useState(false);
  const [resourceDirectoryPaths, setResourceDirectoryPaths] = useState<string[]>([]);

  useEffect(() => {
    setActiveSection(resolveActiveSection(location.search));
    setPanelCollapsed(false);
  }, [location.search]);

  const handleSectionChange = (section: DataDevelopmentSection) => {
    setActiveSection(section);
    setPanelCollapsed(false);

    const searchParams = new URLSearchParams(location.search);
    if (section === "development") {
      searchParams.delete("section");
    } else {
      searchParams.set("section", section);
    }
    const search = searchParams.toString();
    navigate(
      {
        pathname: location.pathname,
        search: search ? `?${search}` : "",
        hash: location.hash,
      },
      { replace: true },
    );
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

      <DataDevelopmentWorkbench />
    </div>
  );
}
