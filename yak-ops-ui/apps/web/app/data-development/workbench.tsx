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
        <div className="flex h-[35px] shrink-0 items-stretch overflow-hidden bg-[#f2f2f2]">
          <div
            role="tablist"
            aria-label="工作区选项卡"
            className="flex min-w-0 flex-1 items-stretch overflow-x-auto [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
          >
            {tabs.map((tab) => {
              const active = tab.id === activeTabId;
              const TabIcon = tab.kind === "workflow" ? Workflow : FileCode2;

              return (
                <div
                  key={tab.id}
                  className={[
                    "group relative flex h-[35px] w-[120px] min-w-fit shrink-0 items-center border-r border-[#e5e5e5]",
                    active
                      ? "bg-white text-[#26282c]"
                      : "bg-[#f2f2f2] text-[#616161] hover:bg-white/70",
                  ].join(" ")}
                >
                  <button
                    type="button"
                    role="tab"
                    aria-selected={active}
                    tabIndex={active ? 0 : -1}
                    title={tab.title}
                    onClick={() => activateTab(tab.id)}
                    className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 pl-[5px] pr-0 text-left text-[13px] leading-none outline-none"
                  >
                    <TabIcon
                      size={16}
                      strokeWidth={1.8}
                      aria-hidden="true"
                      className={
                        active
                          ? "shrink-0 text-[#0033cc]"
                          : "shrink-0 text-[#6b7280]"
                      }
                    />
                    <span className="min-w-0 flex-1 whitespace-nowrap">
                      {tab.title}
                    </span>
                  </button>

                  <button
                    type="button"
                    aria-label={`关闭 ${tab.title}`}
                    title={`关闭 ${tab.title}`}
                    onClick={() => closeTab(tab.id)}
                    className={[
                      "mr-1 flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[#616161] transition-opacity hover:bg-black/5 hover:opacity-100 focus-visible:opacity-100 focus-visible:outline-none",
                      active
                        ? "opacity-50"
                        : "opacity-0 group-hover:opacity-100",
                    ].join(" ")}
                  >
                    <X size={14} strokeWidth={1.8} />
                  </button>

                  {active ? (
                    <span
                      aria-hidden="true"
                      className="pointer-events-none absolute inset-x-0 bottom-0 h-[2px] bg-[rgba(0,51,204,0.9)]"
                    />
                  ) : null}
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
