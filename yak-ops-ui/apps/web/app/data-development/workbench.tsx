import {
  ArrowLeftToLine,
  ArrowRightToLine,
  Ellipsis,
  X,
  XCircle,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";

import { TaskEditor } from "./task-editor/task-editor";
import { WorkflowEditor } from "./workflow-editor/workflow-editor";
import { WorkflowWelcome } from "./workflow-welcome";
import { TaskTypeBadgeIcon } from "./workflow-editor/components/task-type-badge-icon";
import { WorkflowTreeIcon } from "./workflow-editor/components/workflow-tree-icon";
import { useDataDevelopmentWorkbench } from "./workbench-context";

export function DataDevelopmentWorkbench() {
  const {
    tabs,
    activeTab,
    activeTabId,
    activateTab,
    closeTab,
    closeTabs,
  } = useDataDevelopmentWorkbench();
  const activeTabIndex = tabs.findIndex((tab) => tab.id === activeTabId);
  const protectedTabIds = new Set<string>();
  if (activeTab) {
    protectedTabIds.add(activeTab.id);
    if (activeTab.kind === "draft-task") {
      // Keep the parent workflow open while its draft task remains active.
      protectedTabIds.add(`workflow:${activeTab.workflowCode}`);
    }
  }
  const tabsToCloseOther = tabs.filter(
    (tab) => !protectedTabIds.has(tab.id),
  );
  const tabsToCloseLeft = tabs
    .slice(0, activeTabIndex)
    .filter((tab) => !protectedTabIds.has(tab.id));
  const tabsToCloseRight = tabs
    .slice(activeTabIndex + 1)
    .filter((tab) => !protectedTabIds.has(tab.id));

  return (
    <section
      aria-label="数据开发工作区"
      className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)]"
    >
      {tabs.length > 0 ? (
        <div className="flex h-[35px] min-w-0 shrink-0 items-center gap-1 overflow-hidden border-b border-[#e2e2e2] bg-[#f2f2f2] px-2">
          <div
            role="tablist"
            aria-label="工作区选项卡"
            className="flex h-full min-w-0 flex-1 items-center gap-1 overflow-x-auto overflow-y-hidden [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
          >
            {tabs.map((tab) => {
              const active = tab.id === activeTabId;

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
                    {tab.kind === "workflow" ? (
                      <WorkflowTreeIcon size={16} />
                    ) : (
                      <TaskTypeBadgeIcon
                        taskType={
                          tab.kind === "task"
                            ? tab.task.taskType
                            : tab.taskType
                        }
                        size={16}
                      />
                    )}
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

          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="标签页更多操作"
              title="标签页更多操作"
              className="flex h-[27px] w-8 shrink-0 cursor-pointer items-center justify-center rounded-[3px] border-0 bg-transparent p-0 text-[#616161] outline-none transition-colors hover:bg-[#e8e8e8] hover:text-[#26282c] focus-visible:ring-1 focus-visible:ring-[#9bb6ff]"
            >
              <Ellipsis size={18} strokeWidth={1.8} />
            </DropdownMenuTrigger>
            <DropdownMenuContent side="bottom" align="end" sideOffset={4} className="w-48">
              <DropdownMenuItem
                disabled={activeTabIndex < 0}
                onClick={() => {
                  if (activeTabId) closeTab(activeTabId);
                }}
              >
                <X size={14} />
                关闭当前标签页
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={activeTabIndex < 0 || tabsToCloseOther.length === 0}
                onClick={() =>
                  closeTabs(tabsToCloseOther.map((tab) => tab.id))
                }
              >
                <XCircle size={14} />
                关闭其他标签页
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                disabled={activeTabIndex < 0 || tabsToCloseLeft.length === 0}
                onClick={() =>
                  closeTabs(tabsToCloseLeft.map((tab) => tab.id))
                }
              >
                <ArrowLeftToLine size={14} />
                关闭左侧标签页
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={activeTabIndex < 0 || tabsToCloseRight.length === 0}
                onClick={() =>
                  closeTabs(tabsToCloseRight.map((tab) => tab.id))
                }
              >
                <ArrowRightToLine size={14} />
                关闭右侧标签页
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                tone="danger"
                onClick={() => closeTabs(tabs.map((tab) => tab.id))}
              >
                <XCircle size={14} />
                关闭全部标签页
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
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
                tab.id === activeTabId ? "flex" : "hidden",
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
