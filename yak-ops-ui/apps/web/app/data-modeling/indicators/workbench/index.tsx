import { useEffect, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { getIndicatorApi } from "@/service/data-modeling/indicators";
import { getDefinition } from "./config/indicator-definitions";
import { INDICATOR_PAGE_REGISTRY } from "./config/indicator-page-registry";
import { IndicatorDeleteDialog } from "./components/dialogs/indicator-delete-dialog";
import { IndicatorTreeContextMenu } from "./components/dialogs/indicator-tree-context-menu";
import { IndicatorTabBar } from "./components/tabs/indicator-tab-bar";
import { IndicatorTabCloseDialog } from "./components/tabs/indicator-tab-close-dialog";
import { IndicatorTabMenu } from "./components/tabs/indicator-tab-menu";
import { IndicatorWorkspace } from "./components/workspace/indicator-workspace";
import { useIndicatorTabs } from "./hooks/use-indicator-tabs";
import type {
  IndicatorDeleteTarget,
  IndicatorKind,
  IndicatorTabContextMenuState,
  IndicatorTreeContextMenuState,
  IndicatorTreeNode,
} from "./types";

export type DataIndicatorsWorkbenchProps = { projectCode: string; activeKind: IndicatorKind };

export function DataIndicatorsWorkbench({ projectCode, activeKind }: DataIndicatorsWorkbenchProps) {
  const queryClient = useQueryClient();
  const [dwType, setDwType] = useState("PUBLIC_LAYER");
  const tabs = useIndicatorTabs({ projectCode, defaultDwType: dwType });
  const [contextMenu, setContextMenu] = useState<IndicatorTreeContextMenuState>(null);
  const [tabContextMenu, setTabContextMenu] = useState<IndicatorTabContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] = useState<HTMLElement | null>(null);
  const [pendingDelete, setPendingDelete] = useState<IndicatorDeleteTarget | null>(null);

  const visibleTabs = tabs.tabs.filter((tab) => tab.kind === activeKind);
  const visibleActiveTabKey = visibleTabs.some((tab) => tab.key === tabs.activeTabKey)
    ? tabs.activeTabKey
    : visibleTabs[visibleTabs.length - 1]?.key || "";
  const visibleActiveTab = visibleTabs.find((tab) => tab.key === visibleActiveTabKey) || null;
  const getVisibleTabKeysToClose = (
    anchorKey: string,
    action: "current" | "other" | "left" | "right" | "all",
  ) => {
    const index = visibleTabs.findIndex((tab) => tab.key === anchorKey);
    if (action === "all") return visibleTabs.map((tab) => tab.key);
    if (index < 0) return [];
    if (action === "current") return [anchorKey];
    if (action === "other") return visibleTabs.filter((tab) => tab.key !== anchorKey).map((tab) => tab.key);
    if (action === "left") return visibleTabs.slice(0, index).map((tab) => tab.key);
    return visibleTabs.slice(index + 1).map((tab) => tab.key);
  };
  const contextTab = visibleTabs.find((tab) => tab.key === tabContextMenu?.tabKey) || null;
  const contextTabIndex = contextTab ? visibleTabs.findIndex((tab) => tab.key === contextTab.key) : -1;

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  useEffect(() => {
    setContextMenu(null);
    setTabContextMenu(null);
  }, [activeKind, dwType]);

  const onTreeContextMenu = (event: ReactMouseEvent<HTMLButtonElement>, node: IndicatorTreeNode) => {
    if (!node.record) return;
    event.preventDefault();
    setContextMenu({
      x: Math.min(event.clientX, window.innerWidth - 180),
      y: Math.min(event.clientY, window.innerHeight - 90),
      record: node.record,
    });
  };

  const deleteRecord = async () => {
    if (!pendingDelete?.record.id) return;
    const target = pendingDelete;
    try {
      await getIndicatorApi(target.kind).remove(projectCode, String(target.record.id));
      toast.success(getDefinition(target.kind).title + "已删除");
      tabs.removeTabsForRecord(target.kind, String(target.record.id));
      setPendingDelete(null);
      void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-tree", projectCode] });
      void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-page", projectCode, target.kind] });
    } catch {
      toast.warning("删除失败，请确认该指标没有被其他建模对象引用");
    }
  };

  const pageProps = {
    projectCode,
    dwType,
    onDwTypeChange: setDwType,
    onContextMenu: onTreeContextMenu,
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
        <div className="flex h-full min-h-0 shrink-0">
          {(Object.keys(INDICATOR_PAGE_REGISTRY) as IndicatorKind[]).map((kind) => {
            const Page = INDICATOR_PAGE_REGISTRY[kind].Page;
            return (
              <Page
                key={kind}
                {...pageProps}
                active={activeKind === kind}
                onCreate={() => tabs.openTab(kind, "create")}
                onList={() => tabs.openTab(kind, "list")}
                onSelect={(record) => void tabs.openEdit(kind, record)}
              />
            );
          })}
        </div>

        <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-color-hover)]">
          <IndicatorTabBar
            tabs={visibleTabs}
            activeTabKey={visibleActiveTabKey}
            activeTab={visibleActiveTab}
            onSelect={tabs.setActiveTabKey}
            onClose={tabs.requestCloseTabs}
            onContextMenu={(tabKey, x, y) => setTabContextMenu({ tabKey, x, y })}
            getTabKeysToClose={getVisibleTabKeysToClose}
          />
          <IndicatorWorkspace
            projectCode={projectCode}
            dwType={dwType}
            tabs={visibleTabs}
            activeTabKey={visibleActiveTabKey}
            onEdit={tabs.openEdit}
            onDelete={(kind, record) => setPendingDelete({ kind, record })}
            onSaved={tabs.onSaved}
            onDraftChange={tabs.onDraftChange}
          />
        </main>
      </div>

      <IndicatorTreeContextMenu
        contextMenu={contextMenu}
        onClose={() => setContextMenu(null)}
        onDelete={() => {
          if (contextMenu) setPendingDelete({ kind: activeKind, record: contextMenu.record });
          setContextMenu(null);
        }}
      />
      <IndicatorTabMenu
        contextMenu={tabContextMenu}
        portalRoot={contextMenuPortalRoot}
        contextTab={contextTab}
        contextTabIndex={contextTabIndex}
        tabs={visibleTabs}
        onCloseMenu={() => setTabContextMenu(null)}
        onCloseTabs={tabs.requestCloseTabs}
        getTabKeysToClose={getVisibleTabKeysToClose}
      />
      <IndicatorTabCloseDialog
        pendingClose={tabs.pendingClose}
        onCancel={() => tabs.setPendingClose(null)}
        onConfirm={tabs.confirmPendingClose}
      />
      <IndicatorDeleteDialog
        target={pendingDelete}
        onClose={() => setPendingDelete(null)}
        onDelete={() => void deleteRecord()}
      />
    </div>
  );
}

export default DataIndicatorsWorkbench;
