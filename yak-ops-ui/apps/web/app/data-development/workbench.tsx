import { Code2, FileCode2, Workflow, X } from "lucide-react";

import {
  useDataDevelopmentWorkbench,
  type DataDevelopmentResourceType,
} from "./workbench-context";

function ResourceIcon({ type }: { type: DataDevelopmentResourceType }) {
  if (type === "sql") return <FileCode2 size={14} aria-hidden="true" />;
  if (type === "script") return <Code2 size={14} aria-hidden="true" />;
  return <Workflow size={14} aria-hidden="true" />;
}

export function DataDevelopmentWorkbench() {
  const { tabs, activeTab, activeTabId, activateTab, closeTab } =
    useDataDevelopmentWorkbench();

  return (
    <section
      aria-label="数据开发工作区"
      className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)]"
    >
      <div className="flex h-10 shrink-0 items-stretch overflow-x-auto border-b border-[var(--yak-components-border)]">
        {tabs.map((tab) => {
          const active = tab.id === activeTabId;

          return (
            <div
              key={tab.id}
              className={[
                "group flex min-w-[150px] max-w-[240px] shrink-0 items-center border-r border-[var(--yak-components-border)]",
                active
                  ? "bg-[var(--yak-components-panel-bg)]"
                  : "bg-[var(--yak-color-hover)]",
              ].join(" ")}
            >
              <button
                type="button"
                onClick={() => activateTab(tab.id)}
                className={[
                  "flex h-full min-w-0 flex-1 items-center gap-2 border-b-2 px-3 text-left text-[13px]",
                  active
                    ? "border-[var(--yak-color-primary)] text-[var(--yak-components-panel-text)]"
                    : "border-transparent text-[var(--yak-components-muted-text)]",
                ].join(" ")}
              >
                <span
                  className={
                    active
                      ? "text-[var(--yak-color-primary)]"
                      : "text-[var(--yak-components-muted-text)]"
                  }
                >
                  <ResourceIcon type={tab.type} />
                </span>
                <span className="truncate">{tab.title}</span>
              </button>
              <button
                type="button"
                aria-label={`关闭 ${tab.title}`}
                onClick={() => closeTab(tab.id)}
                className="mr-2 flex size-5 shrink-0 items-center justify-center rounded text-[var(--yak-components-muted-text)] opacity-0 transition-opacity hover:bg-[var(--yak-color-hover)] group-hover:opacity-100 focus:opacity-100"
              >
                <X size={13} />
              </button>
            </div>
          );
        })}
      </div>

      {activeTab ? (
        <div className="flex min-h-0 flex-1 flex-col">
          <div className="flex h-10 shrink-0 items-center justify-between border-b border-[var(--yak-components-border)] px-4">
            <div className="flex min-w-0 items-center gap-2">
              <span className="text-[var(--yak-color-primary)]">
                <ResourceIcon type={activeTab.type} />
              </span>
              <span className="truncate text-sm font-medium">{activeTab.title}</span>
            </div>
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              开发工作区
            </span>
          </div>

          <div className="flex min-h-0 flex-1 items-center justify-center p-8">
            <div className="max-w-md text-center">
              <div className="mx-auto mb-4 flex size-12 items-center justify-center rounded-xl bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                <ResourceIcon type={activeTab.type} />
              </div>
              <h1 className="text-base font-semibold">{activeTab.title}</h1>
              <p className="mt-2 text-[13px] leading-6 text-[var(--yak-components-muted-text)]">
                {activeTab.description}
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex flex-1 items-center justify-center p-8 text-center">
          <div>
            <h1 className="text-base font-semibold">数据开发工作区</h1>
            <p className="mt-2 text-[13px] text-[var(--yak-components-muted-text)]">
              从项目目录打开一个开发资源。
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
