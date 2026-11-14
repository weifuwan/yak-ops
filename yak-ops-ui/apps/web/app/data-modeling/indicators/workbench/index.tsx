import { useEffect, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { modelingEntityApi, type ModelingRecord } from "../../service";
import { getDefinition } from "./config/indicator-definitions";
import { IndicatorDeleteDialog } from "./components/dialogs/indicator-delete-dialog";
import { IndicatorTreeContextMenu } from "./components/dialogs/indicator-tree-context-menu";
import { IndicatorSidebar } from "./components/sidebar/indicator-sidebar";
import { IndicatorTabBar } from "./components/tabs/indicator-tab-bar";
import { IndicatorTabCloseDialog } from "./components/tabs/indicator-tab-close-dialog";
import { IndicatorTabMenu } from "./components/tabs/indicator-tab-menu";
import { IndicatorWorkspace } from "./components/workspace/indicator-workspace";
import { useIndicatorTabs } from "./hooks/use-indicator-tabs";
import { useIndicatorTree } from "./hooks/use-indicator-tree";
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
  const tree = useIndicatorTree(projectCode, activeKind);
  const tabs = useIndicatorTabs({
    projectCode,
    activeKind,
    dwType: tree.dwType,
    onSelectTreeKey: tree.setSelectedTreeKey,
  });
  const [contextMenu, setContextMenu] = useState<IndicatorTreeContextMenuState>(null);
  const [tabContextMenu, setTabContextMenu] = useState<IndicatorTabContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] = useState<HTMLElement | null>(null);
  const [pendingDelete, setPendingDelete] = useState<IndicatorDeleteTarget | null>(null);

  const definition = getDefinition(activeKind);
  const contextTab = tabs.tabs.find((tab) => tab.key === tabContextMenu?.tabKey) || null;
  const contextTabIndex = contextTab ? tabs.tabs.findIndex((tab) => tab.key === contextTab.key) : -1;

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  useEffect(() => {
    setContextMenu(null);
  }, [activeKind, tree.groupBy, tree.dwType]);

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
      await modelingEntityApi.remove(projectCode, target.kind, String(target.record.id));
      toast.success(getDefinition(target.kind).title + "已删除");
      tabs.removeTabsForRecord(target.kind, String(target.record.id));
      setPendingDelete(null);
      void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-tree", projectCode] });
      void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-page", projectCode, target.kind] });
    } catch {
      toast.warning("删除失败，请确认该指标没有被其他建模对象引用");
    }
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
        <IndicatorSidebar
          activeKind={activeKind}
          definition={definition}
          dwType={tree.dwType}
          onDwTypeChange={tree.setDwType}
          groupBy={tree.groupBy}
          onGroupByChange={tree.setGroupBy}
          treeKeyword={tree.treeKeyword}
          onTreeKeywordChange={tree.setTreeKeyword}
          visibleTree={tree.visibleTree}
          expandedKeys={tree.visibleExpandedKeys}
          selectedTreeKey={tree.selectedTreeKey}
          loading={tree.treeQuery.isPending || tree.treeQuery.isFetching}
          error={tree.treeQuery.isError}
          onRefresh={() => void tree.treeQuery.refetch()}
          onCreate={() => tabs.openTab(activeKind, "create")}
          onList={() => tabs.openTab(activeKind, "list")}
          onToggle={(key) => {
            tree.setSelectedTreeKey(key);
            tree.setExpandedKeys((current) => {
              const next = new Set(current);
              if (next.has(key)) next.delete(key);
              else next.add(key);
              return next;
            });
          }}
          onSelect={(record) => void tabs.openEdit(activeKind, record)}
          onContextMenu={onTreeContextMenu}
        />

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
            dwType={tree.dwType}
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
