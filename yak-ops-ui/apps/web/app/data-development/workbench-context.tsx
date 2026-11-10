import {
  createContext,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

import type {
  WorkflowDefinitionRecord,
  WorkflowTaskRecord,
} from "@/service/workflow/definition";

export type WorkflowWorkbenchTab =
  | {
      id: string;
      kind: "workflow";
      workflowCode: number;
      title: string;
      version: number;
      releaseState: string;
      description?: string | null;
    }
  | {
      id: string;
      kind: "task";
      workflowCode: number;
      title: string;
      task: WorkflowTaskRecord;
    };

type DataDevelopmentWorkbenchContextValue = {
  tabs: WorkflowWorkbenchTab[];
  activeTabId: string | null;
  activeTab: WorkflowWorkbenchTab | null;
  openWorkflow: (workflow: WorkflowDefinitionRecord) => void;
  openTask: (workflowCode: number, task: WorkflowTaskRecord) => void;
  activateTab: (id: string) => void;
  closeTab: (id: string) => void;
};

const DataDevelopmentWorkbenchContext =
  createContext<DataDevelopmentWorkbenchContextValue | null>(null);

const toWorkflowTab = (
  workflow: WorkflowDefinitionRecord,
): WorkflowWorkbenchTab => ({
  id: "workflow:" + workflow.code,
  kind: "workflow",
  workflowCode: workflow.code,
  title: workflow.name,
  version: workflow.version,
  releaseState: workflow.releaseState,
  description: workflow.description,
});

const toTaskTab = (
  workflowCode: number,
  task: WorkflowTaskRecord,
): WorkflowWorkbenchTab => ({
  id: "task:" + workflowCode + ":" + task.code,
  kind: "task",
  workflowCode,
  title: task.name,
  task,
});

export function DataDevelopmentWorkbenchProvider({ children }: PropsWithChildren) {
  const [tabs, setTabs] = useState<WorkflowWorkbenchTab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);

  const activeTab = tabs.find((tab) => tab.id === activeTabId) ?? null;

  const openTab = (nextTab: WorkflowWorkbenchTab) => {
    setTabs((current) => {
      const existingIndex = current.findIndex((tab) => tab.id === nextTab.id);
      if (existingIndex < 0) return [...current, nextTab];

      return current.map((tab, index) =>
        index === existingIndex ? nextTab : tab,
      );
    });
    setActiveTabId(nextTab.id);
  };

  const openWorkflow = (workflow: WorkflowDefinitionRecord) => {
    openTab(toWorkflowTab(workflow));
  };

  const openTask = (workflowCode: number, task: WorkflowTaskRecord) => {
    openTab(toTaskTab(workflowCode, task));
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
      openWorkflow,
      openTask,
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
