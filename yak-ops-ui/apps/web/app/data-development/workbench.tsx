import { FileCode2, Workflow, X } from "lucide-react";

import { TaskEditor } from "./task-editor/task-editor";
import { WorkflowEditor } from "./workflow-editor/workflow-editor";
import { useDataDevelopmentWorkbench } from "./workbench-context";

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
          const TabIcon = tab.kind === "workflow" ? Workflow : FileCode2;

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
                <TabIcon
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
          {activeTab.kind === "workflow" ? (
            <WorkflowEditor
              key={activeTab.id}
              workflowCode={activeTab.workflowCode}
            />
          ) : (
            <TaskEditor
              key={activeTab.id}
              workflowCode={activeTab.workflowCode}
              task={activeTab.task}
            />
          )}
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
