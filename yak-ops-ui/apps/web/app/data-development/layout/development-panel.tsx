import { PanelHeader } from "@yak-ops/yak-ui";

import { DataDevelopmentResourcePanel } from "../resource-panel";
import type { DataDevelopmentSection } from "./development-rail";

const PANEL_COPY: Record<
  Exclude<DataDevelopmentSection, "development">,
  { title: string; content: string }
> = {
  manual: { title: "手动", content: "这里将展示手动任务相关内容。" },
  component: { title: "组件管理", content: "这里将展示组件管理内容。" },
  catalog: { title: "数据目录", content: "这里将展示数据目录内容。" },
  resource: { title: "资源管理", content: "这里将展示资源管理内容。" },
  "ai-processing": { title: "AI 数据处理", content: "这里将展示 AI 数据处理内容。" },
  history: { title: "运行历史", content: "这里将展示运行历史内容。" },
  review: { title: "代码评审列表", content: "这里将展示代码评审内容。" },
  "operation-check": { title: "操作检查", content: "这里将展示操作检查内容。" },
  "smoke-test": { title: "冒烟测试记录", content: "这里将展示冒烟测试记录。" },
  "code-management": { title: "代码管理", content: "这里将展示代码管理内容。" },
  governance: { title: "数据资产治理", content: "这里将展示数据资产治理内容。" },
  "recycle-bin": { title: "回收站", content: "这里将展示回收站内容。" },
};

type DevelopmentPanelProps = {
  activeSection: DataDevelopmentSection;
  width: number;
};

export function DevelopmentPanel({
  activeSection,
  width,
}: DevelopmentPanelProps) {
  if (activeSection === "development") {
    return <DataDevelopmentResourcePanel width={width} />;
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
