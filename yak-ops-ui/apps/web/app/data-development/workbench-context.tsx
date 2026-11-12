import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";

import type {
  WorkflowDefinitionRecord,
  WorkflowTaskRecord,
} from "@/service/workflow/definition";
import type { WorkflowStoreApi } from "./workflow-editor/store/workflow-store";

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
      nodeId: string;
      releaseState: string;
    };

type DataDevelopmentWorkbenchContextValue = {
  tabs: WorkflowWorkbenchTab[];
  dirtyTabIds: ReadonlySet<string>;
  setTabDirty: (id: string, dirty: boolean) => void;
  activeTabId: string | null;
  activeTab: WorkflowWorkbenchTab | null;
  openWorkflow: (workflow: WorkflowDefinitionRecord) => void;
  openTask: (workflowCode: number, task: WorkflowTaskRecord, nodeId?: string, releaseState?: string) => void;
  updateTaskTab: (id: string, task: WorkflowTaskRecord) => void;
  syncWorkflowTaskRecords: (workflowCode: number, tasks: WorkflowTaskRecord[]) => void;
  updateWorkflowDefinition: (workflow: WorkflowDefinitionRecord) => void;
  updateWorkflowReleaseState: (workflowCode: number, releaseState: string) => void;
  registerWorkflowStore: (workflowCode: number, store: WorkflowStoreApi) => () => void;
  getWorkflowStore: (workflowCode: number) => WorkflowStoreApi | undefined;
  activateTab: (id: string) => void;
  updateTabTitle: (id: string, title: string) => void;
  closeTab: (id: string) => void;
  closeTabs: (ids: string[]) => void;
};

const DataDevelopmentWorkbenchContext =
  createContext<DataDevelopmentWorkbenchContextValue | null>(null);

const toWorkflowTab = (workflow: WorkflowDefinitionRecord): WorkflowWorkbenchTab => ({
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
  nodeId = String(task.code),
  releaseState = "OFFLINE",
): WorkflowWorkbenchTab => ({
  id: "task:" + workflowCode + ":" + task.code,
  kind: "task",
  workflowCode,
  title: task.name,
  task,
  nodeId,
  releaseState,
});

export function DataDevelopmentWorkbenchProvider({ children }: PropsWithChildren) {
  const [tabs, setTabs] = useState<WorkflowWorkbenchTab[]>([]);
  const [dirtyTabIds, setDirtyTabIds] = useState<Set<string>>(() => new Set());
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const workflowStoresRef = useRef(new Map<number, WorkflowStoreApi>());
  const activeTab = tabs.find((tab) => tab.id === activeTabId) ?? null;

  const setTabDirty = useCallback((id: string, dirty: boolean) => {
    setDirtyTabIds((current) => {
      if (current.has(id) === dirty) return current;
      const next = new Set(current);
      if (dirty) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);

  const openTab = (nextTab: WorkflowWorkbenchTab) => {
    setTabs((current) => {
      const index = current.findIndex((tab) => tab.id === nextTab.id);
      if (index < 0) return [...current, nextTab];
      return current.map((tab, itemIndex) => itemIndex === index ? nextTab : tab);
    });
    setActiveTabId(nextTab.id);
  };

  const openWorkflow = (workflow: WorkflowDefinitionRecord) => openTab(toWorkflowTab(workflow));

  const openTask = (
    workflowCode: number,
    task: WorkflowTaskRecord,
    nodeId = String(task.code),
    releaseState?: string,
  ) => {
    const workflowTab = tabs.find(
      (tab) => tab.kind === "workflow" && tab.workflowCode === workflowCode,
    );
    openTab(toTaskTab(
      workflowCode,
      task,
      nodeId,
      releaseState ?? workflowTab?.releaseState ?? "OFFLINE",
    ));
  };

  const updateTaskTab = (id: string, task: WorkflowTaskRecord) => {
    setTabs((current) => current.map((tab) =>
      tab.id === id && tab.kind === "task" ? { ...tab, title: task.name, task } : tab,
    ));
  };

  const syncWorkflowTaskRecords = (workflowCode: number, tasks: WorkflowTaskRecord[]) => {
    const taskByCode = new Map(tasks.map((task) => [task.code, task]));
    setTabs((current) => current.map((tab) => {
      if (tab.kind !== "task" || tab.workflowCode !== workflowCode) return tab;
      const task = taskByCode.get(tab.task.code);
      return task ? { ...tab, title: task.name, task } : tab;
    }));
  };

  const updateWorkflowDefinition = (workflow: WorkflowDefinitionRecord) => {
    setTabs((current) => current.map((tab) =>
      tab.kind === "workflow" && tab.workflowCode === workflow.code
        ? {
            ...tab,
            title: workflow.name,
            version: workflow.version,
            releaseState: workflow.releaseState,
            description: workflow.description,
          }
        : tab,
    ));
  };

  const updateWorkflowReleaseState = (workflowCode: number, releaseState: string) => {
    setTabs((current) => current.map((tab) =>
      tab.workflowCode === workflowCode ? { ...tab, releaseState } : tab,
    ));
  };

  const registerWorkflowStore = useCallback((workflowCode: number, store: WorkflowStoreApi) => {
    workflowStoresRef.current.set(workflowCode, store);
    return () => {
      if (workflowStoresRef.current.get(workflowCode) === store) {
        workflowStoresRef.current.delete(workflowCode);
      }
    };
  }, []);

  const getWorkflowStore = useCallback(
    (workflowCode: number) => workflowStoresRef.current.get(workflowCode),
    [],
  );

  const activateTab = (id: string) => {
    if (tabs.some((tab) => tab.id === id)) setActiveTabId(id);
  };

  const updateTabTitle = (id: string, title: string) => {
    setTabs((current) => current.map((tab) => {
      if (tab.id !== id) return tab;
      return tab.kind === "task"
        ? { ...tab, title, task: { ...tab.task, name: title } }
        : { ...tab, title };
    }));
  };

  const closeTabs = (ids: string[]) => {
    const closingIds = new Set(ids);
    if (!closingIds.size) return;
    for (const tab of tabs) {
      if (tab.kind !== "workflow" || !closingIds.has(tab.id)) continue;
      for (const related of tabs) {
        if (related.kind === "task" && related.workflowCode === tab.workflowCode) {
          closingIds.add(related.id);
        }
      }
    }
    const closingIndex = tabs.findIndex((tab) => closingIds.has(tab.id));
    if (closingIndex < 0) return;
    const nextTabs = tabs.filter((tab) => !closingIds.has(tab.id));
    setDirtyTabIds((current) => {
      const next = new Set(current);
      closingIds.forEach((id) => next.delete(id));
      return next;
    });
    setTabs(nextTabs);
    if (activeTabId && closingIds.has(activeTabId)) {
      setActiveTabId(nextTabs[Math.min(closingIndex, nextTabs.length - 1)]?.id ?? null);
    }
  };

  const closeTab = (id: string) => closeTabs([id]);

  const value = useMemo(() => ({
    tabs,
    dirtyTabIds,
    setTabDirty,
    activeTabId,
    activeTab,
    openWorkflow,
    openTask,
    updateTaskTab,
    syncWorkflowTaskRecords,
    updateWorkflowDefinition,
    updateWorkflowReleaseState,
    registerWorkflowStore,
    getWorkflowStore,
    activateTab,
    updateTabTitle,
    closeTab,
    closeTabs,
  }), [tabs, dirtyTabIds, setTabDirty, activeTabId, activeTab, registerWorkflowStore, getWorkflowStore]);

  return (
    <DataDevelopmentWorkbenchContext.Provider value={value}>
      {children}
    </DataDevelopmentWorkbenchContext.Provider>
  );
}

export function useDataDevelopmentWorkbench() {
  const context = useContext(DataDevelopmentWorkbenchContext);
  if (!context) {
    throw new Error("useDataDevelopmentWorkbench must be used within DataDevelopmentWorkbenchProvider");
  }
  return context;
}
