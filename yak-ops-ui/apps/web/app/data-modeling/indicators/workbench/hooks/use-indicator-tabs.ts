import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { modelingEntityApi, type ModelingRecord } from "../../../service";
import { getDefinition } from "../config/indicator-definitions";
import type { IndicatorKind, IndicatorMode, IndicatorTab, PendingTabClose } from "../types";
import { initialFormValues } from "../utils/indicator-form";

type UseIndicatorTabsOptions = { projectCode: string; activeKind: IndicatorKind; dwType: string; onSelectTreeKey: (key: string) => void };

export function useIndicatorTabs({ projectCode, activeKind, dwType, onSelectTreeKey }: UseIndicatorTabsOptions) {
  const queryClient = useQueryClient();
  const [tabs, setTabs] = useState<IndicatorTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState("");
  const [openingId, setOpeningId] = useState("");
  const [pendingClose, setPendingClose] = useState<PendingTabClose | null>(null);
  const activeTab = tabs.find((tab) => tab.key === activeTabKey) || null;

  const openTab = (kind: IndicatorKind, mode: IndicatorMode, record?: ModelingRecord) => {
    const existing = tabs.find((tab) => tab.kind === kind && (mode === "list" ? tab.mode === "list" : Boolean(record?.id) && tab.recordId === String(record?.id)));
    if (existing) { setActiveTabKey(existing.key); return; }
    const key = kind + "-" + mode + "-" + String(Date.now());
    const label = mode === "create" ? "新建" + getDefinition(kind).title : mode === "list" ? getDefinition(kind).title + "列表" : String(record?.cnName || record?.enShortName || getDefinition(kind).title);
    const tab: IndicatorTab = {
      key, kind, mode, label, recordId: record?.id ? String(record.id) : undefined, record,
      draftValues: mode === "create" && kind === "atomIndicator" ? { ...initialFormValues(kind, undefined, getDefinition(kind).fields), dwType } : undefined,
    };
    setTabs((current) => [...current, tab]);
    setActiveTabKey(key);
  };

  const openEdit = async (kind: IndicatorKind, record: ModelingRecord) => {
    if (!record.id) return;
    const id = String(record.id);
    const existing = tabs.find((tab) => tab.kind === kind && tab.recordId === id);
    if (existing) { setActiveTabKey(existing.key); if (kind === activeKind) onSelectTreeKey("asset:" + id); return; }
    const openingKey = kind + ":" + id;
    if (openingId === openingKey) return;
    setOpeningId(openingKey);
    try {
      const detail = await modelingEntityApi.getById<ModelingRecord>(projectCode, kind, id);
      openTab(kind, "edit", detail);
      if (kind === activeKind) onSelectTreeKey("asset:" + id);
    } catch {
      toast.warning("获取详情失败，请刷新列表后重试");
    } finally { setOpeningId(""); }
  };

  const closeTabKeys = (keys: string[]) => {
    const closingKeys = new Set(keys);
    if (!closingKeys.size) return;
    const closingIndex = tabs.findIndex((tab) => closingKeys.has(tab.key));
    if (closingIndex < 0) return;
    const nextTabs = tabs.filter((tab) => !closingKeys.has(tab.key));
    setTabs(nextTabs);
    if (activeTabKey && closingKeys.has(activeTabKey)) setActiveTabKey(nextTabs[Math.min(closingIndex, nextTabs.length - 1)]?.key || "");
  };

  const requestCloseTabs = (keys: string[]) => {
    const closingKeys = Array.from(new Set(keys));
    if (!closingKeys.length) return;
    const dirtyTitles = tabs.filter((tab) => closingKeys.includes(tab.key) && tab.dirty).map((tab) => tab.label);
    if (dirtyTitles.length) { setPendingClose({ keys: closingKeys, dirtyTitles }); return; }
    closeTabKeys(closingKeys);
  };

  const getTabKeysToClose = (anchorKey: string, action: "current" | "other" | "left" | "right" | "all") => {
    const index = tabs.findIndex((tab) => tab.key === anchorKey);
    if (action === "all") return tabs.map((tab) => tab.key);
    if (index < 0) return [];
    if (action === "current") return [anchorKey];
    if (action === "other") return tabs.filter((tab) => tab.key !== anchorKey).map((tab) => tab.key);
    if (action === "left") return tabs.slice(0, index).map((tab) => tab.key);
    return tabs.slice(index + 1).map((tab) => tab.key);
  };

  const confirmPendingClose = () => { if (!pendingClose) return; closeTabKeys(pendingClose.keys); setPendingClose(null); };

  const onDraftChange = (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => {
    setTabs((current) => current.map((tab) => tab.kind === kind && tab.key === tabKey ? { ...tab, draftValues: values, dirty: true } : tab));
  };

  const onSaved = (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => {
    setTabs((current) => current.map((tab) => tab.kind === kind && tab.key === tabKey ? {
      ...tab, mode: "edit", recordId: record.id ? String(record.id) : tab.recordId, record,
      draftValues: undefined, dirty: false, label: String(record.cnName || record.enShortName || tab.label),
    } : tab));
    void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-tree", projectCode] });
    void queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-page", projectCode, kind] });
  };

  const removeTabsForRecord = (kind: IndicatorKind, recordId: string) => {
    const removedKeys = tabs.filter((tab) => tab.kind === kind && tab.recordId === recordId).map((tab) => tab.key);
    const nextTabs = tabs.filter((tab) => !removedKeys.includes(tab.key));
    setTabs(nextTabs);
    if (removedKeys.includes(activeTabKey)) {
      const activeIndex = tabs.findIndex((tab) => tab.key === activeTabKey);
      setActiveTabKey(nextTabs[Math.max(0, activeIndex - 1)]?.key || nextTabs[0]?.key || "");
    }
  };

  return { tabs, activeTab, activeTabKey, setActiveTabKey, pendingClose, setPendingClose, openTab, openEdit, closeTabKeys, requestCloseTabs, getTabKeysToClose, confirmPendingClose, onDraftChange, onSaved, removeTabsForRecord };
}
