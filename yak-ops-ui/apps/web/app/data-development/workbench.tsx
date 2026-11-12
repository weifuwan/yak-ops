import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
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
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@yak-ops/yak-ui";

import { TaskEditor } from "./task-editor/task-editor";
import { WorkflowEditor } from "./workflow-editor/workflow-editor";
import { WorkflowWelcome } from "./workflow-welcome";
import { TaskTypeBadgeIcon } from "./workflow-editor/components/task-type-badge-icon";
import { WorkflowTreeIcon } from "./workflow-editor/components/workflow-tree-icon";
import { useDataDevelopmentWorkbench } from "./workbench-context";

type TabContextMenuState = {
  tabId: string;
  x: number;
  y: number;
};

type PendingTabClose = {
  ids: string[];
  dirtyTitles: string[];
};

export function DataDevelopmentWorkbench() {
  const {
    tabs,
    dirtyTabIds,
    activeTab,
    activeTabId,
    activateTab,
    closeTabs,
  } = useDataDevelopmentWorkbench();
  const [contextMenu, setContextMenu] = useState<TabContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] = useState<HTMLElement | null>(null);
  const [pendingClose, setPendingClose] = useState<PendingTabClose | null>(null);

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  const requestCloseTabs = (ids: string[]) => {
    const closingIds = new Set(ids);
    for (const tab of tabs) {
      if (tab.kind !== "workflow" || !closingIds.has(tab.id)) continue;
      for (const related of tabs) {
        if (related.kind === "task" && related.workflowCode === tab.workflowCode) {
          closingIds.add(related.id);
        }
      }
    }

    const dirtyTitles = tabs
      .filter((tab) => closingIds.has(tab.id) && dirtyTabIds.has(tab.id))
      .map((tab) => tab.title);
    if (dirtyTitles.length > 0) {
      setPendingClose({ ids: Array.from(closingIds), dirtyTitles });
      return;
    }

    closeTabs(Array.from(closingIds));
  };

  const confirmPendingClose = () => {
    if (!pendingClose) return;
    closeTabs(pendingClose.ids);
    setPendingClose(null);
  };

  const activeTabIndex = tabs.findIndex((tab) => tab.id === activeTabId);
  const protectedTabIds = new Set<string>();
  if (activeTab) {
    protectedTabIds.add(activeTab.id);
    if (activeTab.kind === "task") {
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

  const contextTab = tabs.find((tab) => tab.id === contextMenu?.tabId) ?? null;
  const contextTabIndex = contextTab
    ? tabs.findIndex((tab) => tab.id === contextTab.id)
    : -1;
  const contextProtectedTabIds = new Set<string>();
  if (contextTab) {
    contextProtectedTabIds.add(contextTab.id);
    if (contextTab.kind === "task") {
      contextProtectedTabIds.add("workflow:" + contextTab.workflowCode);
    }
  }
  const contextTabsToCloseOther = tabs.filter(
    (tab) => !contextProtectedTabIds.has(tab.id),
  );
  const contextTabsToCloseLeft = tabs
    .slice(0, contextTabIndex)
    .filter((tab) => !contextProtectedTabIds.has(tab.id));
  const contextTabsToCloseRight = tabs
    .slice(contextTabIndex + 1)
    .filter((tab) => !contextProtectedTabIds.has(tab.id));

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
                  onContextMenu={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    setContextMenu({
                      tabId: tab.id,
                      x: event.clientX,
                      y: event.clientY,
                    });
                  }}
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
                    title={dirtyTabIds.has(tab.id) ? tab.title + "（有未保存修改）" : tab.title}
                    onClick={() => activateTab(tab.id)}
                    className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 pl-2 pr-0 text-left text-[13px] leading-none outline-none"
                  >
                    {tab.kind === "workflow" ? (
                      <WorkflowTreeIcon size={16} />
                    ) : (
                      <TaskTypeBadgeIcon
                        taskType={
                          tab.task.taskType
                        }
                        size={16}
                      />
                    )}
                    <span className="min-w-0 flex-1 truncate whitespace-nowrap">
                      {tab.title}
                    </span>
                    {dirtyTabIds.has(tab.id) ? (
                      <span
                        aria-label="有未保存修改"
                        title="有未保存修改"
                        className="mr-1.5 size-1.5 shrink-0 rounded-full bg-[#e6a23c]"
                      />
                    ) : null}
                  </button>

                  <button
                    type="button"
                    aria-label={`关闭 ${tab.title}`}
                    title={`关闭 ${tab.title}`}
                    onClick={() => requestCloseTabs([tab.id])}
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
                  if (activeTabId) requestCloseTabs([activeTabId]);
                }}
              >
                <X size={14} />
                关闭当前标签页
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={activeTabIndex < 0 || tabsToCloseOther.length === 0}
                onClick={() =>
                  requestCloseTabs(tabsToCloseOther.map((tab) => tab.id))
                }
              >
                <XCircle size={14} />
                关闭其他标签页
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                disabled={activeTabIndex < 0 || tabsToCloseLeft.length === 0}
                onClick={() =>
                  requestCloseTabs(tabsToCloseLeft.map((tab) => tab.id))
                }
              >
                <ArrowLeftToLine size={14} />
                关闭左侧标签页
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={activeTabIndex < 0 || tabsToCloseRight.length === 0}
                onClick={() =>
                  requestCloseTabs(tabsToCloseRight.map((tab) => tab.id))
                }
              >
                <ArrowRightToLine size={14} />
                关闭右侧标签页
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                tone="danger"
                onClick={() => requestCloseTabs(tabs.map((tab) => tab.id))}
              >
                <XCircle size={14} />
                关闭全部标签页
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ) : null}

      {contextMenu && contextMenuPortalRoot && contextTab
        ? createPortal(
            <DropdownMenu
              open
              onOpenChange={(open) => {
                if (!open) setContextMenu(null);
              }}
            >
              <DropdownMenuTrigger
                aria-hidden="true"
                tabIndex={-1}
                className="pointer-events-none fixed size-px opacity-0"
                style={{ left: contextMenu.x, top: contextMenu.y }}
              />
              <DropdownMenuContent
                side="bottom"
                align="start"
                sideOffset={0}
                className="w-[208px] rounded-md p-1"
              >
                <DropdownMenuItem onClick={() => requestCloseTabs([contextTab.id])}>
                  <X size={14} />
                  关闭
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={contextTabsToCloseOther.length === 0}
                  onClick={() =>
                    requestCloseTabs(contextTabsToCloseOther.map((tab) => tab.id))
                  }
                >
                  <XCircle size={14} />
                  关闭其他标签页
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={contextTabIndex < 0 || contextTabsToCloseLeft.length === 0}
                  onClick={() =>
                    requestCloseTabs(contextTabsToCloseLeft.map((tab) => tab.id))
                  }
                >
                  <ArrowLeftToLine size={14} />
                  关闭左侧标签页
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={contextTabIndex < 0 || contextTabsToCloseRight.length === 0}
                  onClick={() =>
                    requestCloseTabs(contextTabsToCloseRight.map((tab) => tab.id))
                  }
                >
                  <ArrowRightToLine size={14} />
                  关闭右侧标签页
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  tone="danger"
                  onClick={() => requestCloseTabs(tabs.map((tab) => tab.id))}
                >
                  <XCircle size={14} />
                  关闭全部标签页
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>,
            contextMenuPortalRoot,
          )
        : null}

      <Dialog
        open={Boolean(pendingClose)}
        onOpenChange={(open) => {
          if (!open) setPendingClose(null);
        }}
      >
        {pendingClose ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">
              关闭未保存的标签页
            </DialogTitle>
            <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              {pendingClose.dirtyTitles.join("、")}存在未保存的修改。关闭后，这些修改将丢失，确定继续吗？
            </DialogDescription>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setPendingClose(null)}
              >
                取消
              </Button>
              <Button
                size="sm"
                variant="danger"
                onClick={confirmPendingClose}
              >
                仍然关闭
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <div className="relative min-h-0 flex-1">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTabId;
          const tabClassName = [
            "absolute inset-0 min-h-0",
            isActive ? "flex" : "hidden",
          ].join(" ");

          if (tab.kind === "workflow") {
            return (
              <div
                key={tab.id}
                aria-hidden={!isActive}
                className={tabClassName}
              >
                <WorkflowEditor workflowCode={tab.workflowCode} />
              </div>
            );
          }

          if (tab.kind === "task") {
            return (
              <div
                key={tab.id}
                aria-hidden={!isActive}
                className={tabClassName}
              >
                <TaskEditor
                  workflowCode={tab.workflowCode}
                  task={tab.task}
                  nodeId={tab.nodeId}
                  readOnly={tab.releaseState.toUpperCase() === "ONLINE"}
                />
              </div>
            );
          }

          return null;
        })}

        {!activeTab ? <WorkflowWelcome /> : null}
      </div>
    </section>
  );
}
