import {
  createContext,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

export type DataDevelopmentResourceType = "workflow" | "sql" | "script";

export type DataDevelopmentResource = {
  id: string;
  title: string;
  type: DataDevelopmentResourceType;
  description: string;
};

type DataDevelopmentWorkbenchContextValue = {
  tabs: DataDevelopmentResource[];
  activeTabId: string | null;
  activeTab: DataDevelopmentResource | null;
  openResource: (resource: DataDevelopmentResource) => void;
  activateTab: (id: string) => void;
  closeTab: (id: string) => void;
};

const DataDevelopmentWorkbenchContext =
  createContext<DataDevelopmentWorkbenchContextValue | null>(null);

const INITIAL_TABS: DataDevelopmentResource[] = [
  {
    id: "workflow:customer-sync",
    title: "客户同步工作流",
    type: "workflow",
    description: "这里将接入工作流编辑器。",
  },
  {
    id: "sql:daily-report",
    title: "日报汇总.sql",
    type: "sql",
    description: "这里将接入 SQL 编辑器。",
  },
];

export function DataDevelopmentWorkbenchProvider({ children }: PropsWithChildren) {
  const [tabs, setTabs] = useState<DataDevelopmentResource[]>(INITIAL_TABS);
  const [activeTabId, setActiveTabId] = useState<string | null>(
    INITIAL_TABS[0]?.id ?? null,
  );

  const activeTab = tabs.find((tab) => tab.id === activeTabId) ?? null;

  const openResource = (resource: DataDevelopmentResource) => {
    setTabs((current) =>
      current.some((tab) => tab.id === resource.id)
        ? current
        : [...current, resource],
    );
    setActiveTabId(resource.id);
  };

  const activateTab = (id: string) => {
    if (tabs.some((tab) => tab.id === id)) {
      setActiveTabId(id);
    }
  };

  const closeTab = (id: string) => {
    const closingIndex = tabs.findIndex((tab) => tab.id === id);
    if (closingIndex < 0) return;

    const nextTabs = tabs.filter((tab) => tab.id !== id);
    setTabs(nextTabs);

    if (activeTabId === id) {
      setActiveTabId(
        nextTabs[Math.min(closingIndex, nextTabs.length - 1)]?.id ?? null,
      );
    }
  };

  const value = useMemo(
    () => ({
      tabs,
      activeTabId,
      activeTab,
      openResource,
      activateTab,
      closeTab,
    }),
    [tabs, activeTabId, activeTab],
  );

  return (
    <DataDevelopmentWorkbenchContext.Provider value={value}>
      {children}
    </DataDevelopmentWorkbenchContext.Provider>
  );
}

export function useDataDevelopmentWorkbench() {
  const context = useContext(DataDevelopmentWorkbenchContext);
  if (!context) {
    throw new Error(
      "useDataDevelopmentWorkbench must be used within DataDevelopmentWorkbenchProvider",
    );
  }
  return context;
}
