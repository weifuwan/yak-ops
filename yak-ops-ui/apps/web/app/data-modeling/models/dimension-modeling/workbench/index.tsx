import { useState } from "react";
import { toast } from "@yak-ops/yak-ui";
import { Layers3 } from "lucide-react";

import {
  dimensionModelingApi,
  type DimensionTableRecord,
  type DimensionTreeNode,
} from "@/service/data-modeling/models/dimension-modeling";
import { DimensionSidebar } from "./components/dimension-sidebar";
import { DimensionTabBar } from "./components/dimension-tab-bar";
import { DimensionEditor, DimensionList } from "./components/dimension-editor";
import { useDimensionTree } from "./hooks/use-dimension-tree";
import type { DimensionTab } from "./types";

type Props = {
  projectCode: string;
};

export function DimensionModelingWorkbench({ projectCode }: Props) {
  const tree = useDimensionTree(projectCode);
  const [tabs, setTabs] = useState<DimensionTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState("");
  const [selectedTreeKey, setSelectedTreeKey] = useState("");

  const openTab = (mode: DimensionTab["mode"], record?: DimensionTableRecord, tabDwType: string = tree.dwType) => {
    const existing = tabs.find((tab) => {
      if (mode === "create" || mode === "list") return tab.mode === mode && tab.dwType === tabDwType;
      return tab.mode === "edit" && String(tab.record?.id || "") === String(record?.id || "");
    });
    if (existing) {
      setActiveTabKey(existing.key);
      return;
    }

    const key = "dimension-" + mode + "-" + String(Date.now());
    const label = mode === "create"
      ? "新建维度表"
      : mode === "list"
        ? "维度表列表"
        : String(record?.cnName || record?.tableName || "维度表");
    const nextTab: DimensionTab = { key, mode, dwType: tabDwType, label, record };
    setTabs((current) => [...current, nextTab]);
    setActiveTabKey(key);
  };

  const openEdit = async (node: DimensionTreeNode) => {
    const id = node.key.endsWith("-DIM") ? node.key.slice(0, -4) : node.key;
    if (!id || id === "DimensionTable_ROOT") return;

    const existing = tabs.find((tab) => tab.mode === "edit" && String(tab.record?.id || "") === id);
    if (existing) {
      setActiveTabKey(existing.key);
      return;
    }

    try {
      const record = await dimensionModelingApi.getById(projectCode, id);
      openTab("edit", record, tree.dwType);
    } catch {
      toast.warning("获取维度表详情失败，请刷新目录后重试");
    }
  };

  const closeTab = (key: string) => {
    const closingIndex = tabs.findIndex((tab) => tab.key === key);
    if (closingIndex < 0) return;
    const nextTabs = tabs.filter((tab) => tab.key !== key);
    setTabs(nextTabs);
    if (activeTabKey === key) {
      setActiveTabKey(nextTabs[Math.min(closingIndex, nextTabs.length - 1)]?.key || "");
    }
  };

  const onSaved = (tabKey: string, record: DimensionTableRecord) => {
    setTabs((current) => current.map((tab) => tab.key === tabKey
      ? {
          ...tab,
          mode: "edit",
          record,
          label: String(record.cnName || record.tableName || "维度表"),
          dirty: false,
        }
      : tab));
  };

  const onDirtyChange = (tabKey: string, dirty: boolean) => {
    setTabs((current) => current.map((tab) => tab.key === tabKey ? { ...tab, dirty } : tab));
  };

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
        <DimensionSidebar
          projectCode={projectCode}
          dwType={tree.dwType}
          onDwTypeChange={(value) => {
            tree.setDwType(value);
            setSelectedTreeKey("");
          }}
          groupBy={tree.groupBy}
          onGroupByChange={(value) => {
            tree.setGroupBy(value);
            tree.setScopeValue("");
          }}
          scopeValue={tree.scopeValue}
          onScopeValueChange={tree.setScopeValue}
          keyword={tree.keyword}
          onKeywordChange={tree.setKeyword}
          root={tree.root}
          expandedKeys={tree.expandedKeys}
          selectedTreeKey={selectedTreeKey}
          loading={tree.treeQuery.isFetching}
          initialLoading={tree.treeQuery.isPending && tree.treeQuery.isFetching}
          error={tree.treeQuery.isError}
          onRefresh={() => void tree.treeQuery.refetch()}
          onCreate={() => openTab("create")}
          onList={() => openTab("list")}
          onToggle={tree.toggleExpanded}
          onSelect={(node) => {
            setSelectedTreeKey(node.key);
            void openEdit(node);
          }}
        />

        <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[var(--yak-color-hover)]">
          <DimensionTabBar
            tabs={tabs}
            activeTabKey={activeTabKey}
            onSelect={setActiveTabKey}
            onClose={closeTab}
          />
          <div className="relative min-h-0 flex-1 overflow-hidden">
            {tabs.map((tab) => {
              const active = tab.key === activeTabKey;
              return (
                <div
                  key={tab.key}
                  aria-hidden={!active}
                  className={active ? "absolute inset-0 flex min-h-0" : "hidden"}
                >
                  {tab.mode === "list" ? (
                    <DimensionList
                      projectCode={projectCode}
                      dwType={tab.dwType || tree.dwType}
                      onEdit={(record) => openTab("edit", record, tab.dwType || tree.dwType)}
                    />
                  ) : (
                    <DimensionEditor
                      projectCode={projectCode}
                      dwType={tab.dwType || tree.dwType}
                      tab={tab}
                      onSaved={(record) => onSaved(tab.key, record)}
                      onDirtyChange={(dirty) => onDirtyChange(tab.key, dirty)}
                    />
                  )}
                </div>
              );
            })}

            {!tabs.some((tab) => tab.key === activeTabKey) ? (
              <div className="absolute inset-0 flex min-h-0 items-center justify-center bg-white">
                <div className="flex flex-col items-center text-center">
                  <div className="mb-4 flex size-24 items-center justify-center rounded-full bg-[#f5f7fb] text-[#a8b1c1]">
                    <Layers3 size={42} strokeWidth={1.2} />
                  </div>
                  <p className="text-xs leading-5 text-[var(--yak-components-muted-text)]">
                    请从左侧目录选择维度表，或点击 + 新建维度表
                  </p>
                </div>
              </div>
            ) : null}
          </div>
        </main>
      </div>
    </div>
  );
}

export default DimensionModelingWorkbench;
