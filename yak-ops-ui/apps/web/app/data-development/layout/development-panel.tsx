import { DataDevelopmentResourcePanel } from "../resource-panel";
import { ResourceBrowserPanel } from "../resource-browser-panel";
import { ManualRunPanel } from "../manual-run";
import { RunHistoryPanel } from "../run-history";
import { DataCatalogPanel } from "../data-catalog";
import { OperationHistoryPanel } from "../operation-history";
import type { DataDevelopmentSection } from "./development-rail";

const PANEL_COPY: Record<
  Exclude<DataDevelopmentSection, "development" | "resource" | "manual" | "catalog" | "history" | "operation-check">,
  { title: string; content: string }
> = {
  component: { title: "组件管理", content: "该能力暂未接入当前后端。" },
  "ai-processing": { title: "AI 数据处理", content: "该能力暂未接入当前后端。" },
  review: { title: "代码评审列表", content: "该能力暂未接入当前后端。" },
  "smoke-test": { title: "冒烟测试记录", content: "该能力暂未接入当前后端。" },
  "code-management": { title: "代码管理", content: "该能力暂未接入当前后端。" },
  governance: { title: "数据资产治理", content: "该能力暂未接入当前后端。" },
  "recycle-bin": { title: "回收站", content: "该能力暂未接入当前后端。" },
};

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
  if (activeSection === "history") return <RunHistoryPanel width={width} />;
  if (activeSection === "catalog") return <DataCatalogPanel width={width} />;
  if (activeSection === "operation-check") {
    return <OperationHistoryPanel width={width} />;
  }

  const panel = PANEL_COPY[activeSection];

  return (
    <aside
      aria-label={panel.title}
      className="flex shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title={panel.title} />
      <div className="p-4 text-[13px] leading-6 text-[var(--yak-components-muted-text)]">
        {panel.content}
      </div>
    </aside>
  );
}
