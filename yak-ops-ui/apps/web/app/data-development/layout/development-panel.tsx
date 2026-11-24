import { DataDevelopmentResourcePanel } from "../resource-panel";
import { ResourceBrowserPanel } from "../resource-browser-panel";
import { ManualRunPanel } from "../manual-run";
import { RunHistoryPanel } from "../run-history";
import { DataCatalogPanel } from "../data-catalog";
import { OperationHistoryPanel } from "../operation-history";
import { BackfillPanel } from "../backfill/panel";
import { WorkflowLineagePanel } from "../workflow-lineage/panel";
import { TaskInstanceRecordsPanel } from "../task-instance-records/panel";
import { ProjectParametersPanel } from "../project-parameters/panel";
import type { DataDevelopmentSection } from "./development-rail";

type DevelopmentPanelProps = {
  activeSection: DataDevelopmentSection;
  width: number;
  resourceDirectoryPaths: string[];
  onResourceDirectoryPathsChange: (paths: string[]) => void;
};

export function DevelopmentPanel({
  activeSection,
  width,
  resourceDirectoryPaths,
  onResourceDirectoryPathsChange,
}: DevelopmentPanelProps) {
  if (activeSection === "development") {
    return <DataDevelopmentResourcePanel width={width} />;
  }

  if (activeSection === "resource") {
    return (
      <ResourceBrowserPanel
        width={width}
        directoryPaths={resourceDirectoryPaths}
        onDirectoryPathsChange={onResourceDirectoryPathsChange}
      />
    );
  }

  if (activeSection === "manual") return <ManualRunPanel width={width} />;
  if (activeSection === "backfill") return <BackfillPanel width={width} />;
  if (activeSection === "lineage") return <WorkflowLineagePanel width={width} />;
  if (activeSection === "task-instances") {
    return <TaskInstanceRecordsPanel width={width} />;
  }
  if (activeSection === "parameters") {
    return <ProjectParametersPanel width={width} />;
  }
  if (activeSection === "history") return <RunHistoryPanel width={width} />;
  if (activeSection === "catalog") return <DataCatalogPanel width={width} />;
  if (activeSection === "operation-check") {
    return <OperationHistoryPanel width={width} />;
  }

  return null;
}
