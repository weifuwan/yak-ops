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
    }
  | {
      id: string;
      kind: "draft-task";
      workflowCode: number;
      title: string;
      nodeId: string;
      taskType: string;
    };

type DataDevelopmentWorkbenchContextValue = {
  tabs: WorkflowWorkbenchTab[];
  activeTabId: string | null;
  activeTab: WorkflowWorkbenchTab | null;
  openWorkflow: (workflow: WorkflowDefinitionRecord) => void;
  openTask: (workflowCode: number, task: WorkflowTaskRecord) => void;
  openDraftTask: (
    workflowCode: number,
    nodeId: string,
    taskType: string,
    title: string,
  ) => void;
  activateTab: (id: string) => void;
  updateTabTitle: (id: string, title: string) => void;
  closeTab: (id: string) => void;
  closeTabs: (ids: string[]) => void;
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

const toDraftTaskTab = (
  workflowCode: number,
  nodeId: string,
  taskType: string,
  title: string,
): WorkflowWorkbenchTab => ({
  id: "draft-task:" + workflowCode + ":" + nodeId,
  kind: "draft-task",
  workflowCode,
  title,
  nodeId,
  taskType,
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

  const openDraftTask = (
    workflowCode: number,
    nodeId: string,
    taskType: string,
    title: string,
  ) => {
    openTab(toDraftTaskTab(workflowCode, nodeId, taskType, title));
  };

  const activateTab = (id: string) => {
    if (tabs.some((tab) => tab.id === id)) {
      setActiveTabId(id);
    }
  };

  const updateTabTitle = (id: string, title: string) => {
    setTabs((current) =>
      current.map((tab) => {
        if (tab.id !== id) return tab;
        if (tab.kind === "task") {
          return { ...tab, title, task: { ...tab.task, name: title } };
        }
        return { ...tab, title };
      }),
    );
  };

  const closeTabs = (ids: string[]) => {
    const closingIds = new Set(ids);
    if (closingIds.size === 0) return;

    // Draft task tabs depend on their parent workflow tab, so preserve the
    // existing behavior of closing those drafts together with the workflow.
    for (const tab of tabs) {
      if (tab.kind !== "workflow" || !closingIds.has(tab.id)) continue;

      for (const relatedTab of tabs) {
        if (
          relatedTab.kind === "draft-task" &&
          relatedTab.workflowCode === tab.workflowCode
        ) {
          closingIds.add(relatedTab.id);
        }
      }
    }

    const closingIndex = tabs.findIndex((tab) => closingIds.has(tab.id));
    if (closingIndex < 0) return;

    const nextTabs = tabs.filter((tab) => !closingIds.has(tab.id));
    setTabs(nextTabs);

    if (activeTabId && closingIds.has(activeTabId)) {
      setActiveTabId(
        nextTabs[Math.min(closingIndex, nextTabs.length - 1)]?.id ?? null,
      );
    }
  };

  const closeTab = (id: string) => closeTabs([id]);

  const value = useMemo(
    () => ({
      tabs,
      activeTabId,
      activeTab,
      openWorkflow,
      openTask,
      openDraftTask,
      activateTab,
      updateTabTitle,
      closeTab,
      closeTabs,
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
