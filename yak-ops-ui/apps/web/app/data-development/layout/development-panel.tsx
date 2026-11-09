import { DataDevelopmentResourcePanel } from "../resource-panel";
import type { DataDevelopmentSection } from "./development-rail";

const PANEL_COPY: Record<
  Exclude<DataDevelopmentSection, "development">,
  { title: string; content: string }
> = {
  schedule: {
    title: "调度",
    content: "这里将展示调度相关内容。",
  },
  resource: {
    title: "资源",
    content: "这里将展示资源相关内容。",
  },
  publish: {
    title: "发布",
    content: "这里将展示发布相关内容。",
  },
};

type DevelopmentPanelProps = {
  activeSection: DataDevelopmentSection;
};

export function DevelopmentPanel({
  activeSection,
}: DevelopmentPanelProps) {
  if (activeSection === "development") {
    return <DataDevelopmentResourcePanel />;
  }

  const panel = PANEL_COPY[activeSection];

  return (
    <aside
      aria-label={panel.title}
      className="flex w-[280px] shrink-0 flex-col border-r border-[var(--yak-components-border)] bg-[var(--yak-components-panel-bg)]"
    >
      <div className="flex h-10 shrink-0 items-center border-b border-[var(--yak-components-border)] px-3">
        <span className="text-sm font-semibold">{panel.title}</span>
      </div>
      <div className="p-4 text-[13px] leading-6 text-[var(--yak-components-muted-text)]">
        {panel.content}
      </div>
    </aside>
  );
}
