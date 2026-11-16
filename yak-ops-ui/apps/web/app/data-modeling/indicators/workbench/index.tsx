import { useEffect, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { getIndicatorApi } from "@/service/data-modeling/indicators";
import AtomIndicatorPage from "../atom-indicator";
import DerivativeIndicatorPage from "../derivative-indicator";
import CompositeIndicatorPage from "../composite-indicator";
import ModifierPage from "../modifier";
import TimePeriodPage from "../time-period";
import { getDefinition } from "./config/indicator-definitions";
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
  const tabs = useIndicatorTabs({ projectCode, dwType });
  const [contextMenu, setContextMenu] = useState<IndicatorTreeContextMenuState>(null);
  const [tabContextMenu, setTabContextMenu] = useState<IndicatorTabContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] = useState<HTMLElement | null>(null);
  const [pendingDelete, setPendingDelete] = useState<IndicatorDeleteTarget | null>(null);

  const contextTab = tabs.tabs.find((tab) => tab.key === tabContextMenu?.tabKey) || null;
  const contextTabIndex = contextTab ? tabs.tabs.findIndex((tab) => tab.key === contextTab.key) : -1;

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  useEffect(() => {
    setContextMenu(null);
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
          <AtomIndicatorPage
            {...pageProps}
            active={activeKind === "atomIndicator"}
            onCreate={() => tabs.openTab("atomIndicator", "create")}
            onList={() => tabs.openTab("atomIndicator", "list")}
            onSelect={(record) => void tabs.openEdit("atomIndicator", record)}
          />
          <DerivativeIndicatorPage
            {...pageProps}
            active={activeKind === "derivativeIndicator"}
            onCreate={() => tabs.openTab("derivativeIndicator", "create")}
            onList={() => tabs.openTab("derivativeIndicator", "list")}
            onSelect={(record) => void tabs.openEdit("derivativeIndicator", record)}
          />
          <CompositeIndicatorPage
            {...pageProps}
            active={activeKind === "composeIndicator"}
            onCreate={() => tabs.openTab("composeIndicator", "create")}
            onList={() => tabs.openTab("composeIndicator", "list")}
            onSelect={(record) => void tabs.openEdit("composeIndicator", record)}
          />
          <ModifierPage
            {...pageProps}
            active={activeKind === "modifier"}
            onCreate={() => tabs.openTab("modifier", "create")}
            onList={() => tabs.openTab("modifier", "list")}
            onSelect={(record) => void tabs.openEdit("modifier", record)}
          />
          <TimePeriodPage
            {...pageProps}
            active={activeKind === "timePeriod"}
            onCreate={() => tabs.openTab("timePeriod", "create")}
            onList={() => tabs.openTab("timePeriod", "list")}
            onSelect={(record) => void tabs.openEdit("timePeriod", record)}
          />
        </div>

        <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-color-hover)]">
          <IndicatorTabBar
            tabs={tabs.tabs}
            activeTabKey={tabs.activeTabKey}
            activeTab={tabs.activeTab}
            onSelect={tabs.setActiveTabKey}
            onClose={tabs.requestCloseTabs}
            onContextMenu={(tabKey, x, y) => setTabContextMenu({ tabKey, x, y })}
            getTabKeysToClose={tabs.getTabKeysToClose}
          />
          <IndicatorWorkspace
            projectCode={projectCode}
            dwType={dwType}
            tabs={tabs.tabs}
            activeTabKey={tabs.activeTabKey}
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
        tabs={tabs.tabs}
        onCloseMenu={() => setTabContextMenu(null)}
        onCloseTabs={tabs.requestCloseTabs}
        getTabKeysToClose={tabs.getTabKeysToClose}
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
