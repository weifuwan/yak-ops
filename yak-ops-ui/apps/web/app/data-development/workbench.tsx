import { FileCode2, Workflow, X } from "lucide-react";

import { TaskEditor } from "./task-editor/task-editor";
import { WorkflowEditor } from "./workflow-editor/workflow-editor";
import { WorkflowWelcome } from "./workflow-welcome";
import { useDataDevelopmentWorkbench } from "./workbench-context";

export function DataDevelopmentWorkbench() {
  const { tabs, activeTab, activeTabId, activateTab, closeTab } =
    useDataDevelopmentWorkbench();

  return (
    <section
      aria-label="数据开发工作区"
      className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)]"
    >
      {tabs.length > 0 ? (
        <div className="flex h-[35px] shrink-0 items-center overflow-hidden border-b border-[#e2e2e2] bg-[#f2f2f2] px-2">
          <div
            role="tablist"
            aria-label="工作区选项卡"
            className="flex h-full min-w-0 flex-1 items-center gap-1 overflow-x-auto [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
          >
            {tabs.map((tab) => {
              const active = tab.id === activeTabId;
              const TabIcon = tab.kind === "workflow" ? Workflow : FileCode2;

              return (
                <div
                  key={tab.id}
                  className={[
                    "group relative flex h-[27px] w-fit min-w-[120px] max-w-[240px] shrink-0 items-center rounded-[3px]",
                    active
                      ? "bg-[#cedaff] text-[#26282c]"
                      : "bg-transparent text-[#616161] hover:bg-[#e8e8e8]",
                  ].join(" ")}
                >
                  <button
                    type="button"
                    role="tab"
                    aria-selected={active}
                    tabIndex={active ? 0 : -1}
                    title={tab.title}
                    onClick={() => activateTab(tab.id)}
                    className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 pl-2 pr-0 text-left text-[13px] leading-none outline-none"
                  >
                    <TabIcon
                      size={16}
                      strokeWidth={1.8}
                      aria-hidden="true"
                      className={[
                        "shrink-0",
                        tab.kind === "workflow"
                          ? "text-[#7397f7]"
                          : "text-[#df985d]",
                      ].join(" ")}
                    />
                    <span className="min-w-0 flex-1 truncate whitespace-nowrap">
                      {tab.title}
                    </span>
                  </button>

                  <button
                    type="button"
                    aria-label={`关闭 ${tab.title}`}
                    title={`关闭 ${tab.title}`}
                    onClick={() => closeTab(tab.id)}
                    className={[
                      "mr-1 flex size-5 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[#343434] transition-opacity hover:bg-black/5 focus-visible:opacity-100 focus-visible:outline-none",
                      active
                        ? "opacity-100"
                        : "opacity-0 group-hover:opacity-100",
                    ].join(" ")}
                  >
                    <X size={15} strokeWidth={1.8} />
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      ) : null}

      <div className="relative min-h-0 flex-1">
        {tabs.map((tab) =>
          tab.kind === "workflow" ? (
            <div
              key={tab.id}
              aria-hidden={tab.id !== activeTabId}
              className={[
                "absolute inset-0 min-h-0",
                tab.id === activeTabId
                  ? "flex"
                  : "invisible pointer-events-none flex",
              ].join(" ")}
            >
              <WorkflowEditor workflowCode={tab.workflowCode} />
            </div>
          ) : null,
        )}

        {activeTab?.kind === "task" ? (
          <div className="absolute inset-0 flex min-h-0">
            <TaskEditor
              key={activeTab.id}
              workflowCode={activeTab.workflowCode}
              task={activeTab.task}
            />
          </div>
        ) : null}

        {activeTab?.kind === "draft-task" ? (
          <div className="absolute inset-0 flex min-h-0">
            <TaskEditor
              key={activeTab.id}
              workflowCode={activeTab.workflowCode}
              draftTask={{
                nodeId: activeTab.nodeId,
                name: activeTab.title,
                taskType: activeTab.taskType,
              }}
            />
          </div>
        ) : null}

        {!activeTab ? <WorkflowWelcome /> : null}
      </div>
    </section>
  );
}
