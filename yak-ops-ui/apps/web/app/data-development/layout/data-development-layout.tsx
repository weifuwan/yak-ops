import { useState } from "react";

import { DataDevelopmentWorkbench } from "../workbench";
import {
  DevelopmentRail,
  type DataDevelopmentSection,
} from "./development-rail";
import { DevelopmentPanel } from "./development-panel";

export function DataDevelopmentLayout() {
  const [activeSection, setActiveSection] =
    useState<DataDevelopmentSection>("development");

  return (
    <div className="flex h-full min-h-0 w-full overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <DevelopmentRail
        activeSection={activeSection}
        onSectionChange={setActiveSection}
      />
      <DevelopmentPanel activeSection={activeSection} />
      <DataDevelopmentWorkbench />
    </div>
  );
}
