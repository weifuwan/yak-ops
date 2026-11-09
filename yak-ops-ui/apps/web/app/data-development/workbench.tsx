import { Workflow, X } from "lucide-react";

import { useDataDevelopmentWorkbench } from "./workbench-context";

const stateText = (state: string) => {
  if (state === "ONLINE") return "已上线";
  if (state === "OFFLINE") return "未上线";
  return state || "-";
};

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
                  "flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 border-b-2 px-3 text-left text-[13px]",
                  active
                    ? "border-[var(--yak-color-primary)] text-[var(--yak-components-panel-text)]"
                    : "border-transparent text-[var(--yak-components-muted-text)]",
                ].join(" ")}
              >
                <Workflow
                  size={14}
                  aria-hidden="true"
                  className={
                    active
                      ? "text-[var(--yak-color-primary)]"
                      : "text-[var(--yak-components-muted-text)]"
                  }
                />
                <span className="truncate">{tab.title}</span>
              </button>
              <button
                type="button"
                aria-label={`关闭 ${tab.title}`}
                onClick={() => closeTab(tab.id)}
                className="mr-2 flex size-5 shrink-0 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] opacity-0 transition-opacity hover:bg-[var(--yak-color-hover)] group-hover:opacity-100 focus:opacity-100"
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
              <Workflow
                size={14}
                aria-hidden="true"
                className="text-[var(--yak-color-primary)]"
              />
              <span className="truncate text-sm font-medium">{activeTab.title}</span>
            </div>
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              工作流开发
            </span>
          </div>

          <div className="flex min-h-0 flex-1 items-center justify-center p-8">
            <div className="w-full max-w-lg text-center">
              <div className="mx-auto mb-4 flex size-12 items-center justify-center rounded-xl bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                <Workflow size={18} aria-hidden="true" />
              </div>
              <h1 className="text-base font-semibold">{activeTab.title}</h1>
              <div className="mt-3 flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-[var(--yak-components-muted-text)]">
                <span>工作流编码：{activeTab.workflowCode}</span>
                <span>版本：v{activeTab.version}</span>
                <span>状态：{stateText(activeTab.releaseState)}</span>
              </div>
              {activeTab.selectedTask ? (
                <div className="mx-auto mt-5 max-w-sm rounded-md border border-[var(--yak-components-border)] px-4 py-3 text-left">
                  <div className="text-xs text-[var(--yak-components-muted-text)]">
                    当前节点
                  </div>
                  <div className="mt-1 text-sm font-medium">
                    {activeTab.selectedTask.name}
                  </div>
                  <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                    {activeTab.selectedTask.taskType || "-"} · {activeTab.selectedTask.code}
                  </div>
                </div>
              ) : null}
              <p className="mt-5 text-[13px] leading-6 text-[var(--yak-components-muted-text)]">
                工作流定义与节点已经接入真实后端数据，工作流编辑器将在这里继续接入。
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex flex-1 items-center justify-center p-8 text-center">
          <div>
            <Workflow
              size={24}
              aria-hidden="true"
              className="mx-auto text-[var(--yak-components-muted-text)]"
            />
            <h1 className="mt-3 text-base font-semibold">工作流开发</h1>
            <p className="mt-2 text-[13px] text-[var(--yak-components-muted-text)]">
              从左侧打开一个工作流定义。
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
