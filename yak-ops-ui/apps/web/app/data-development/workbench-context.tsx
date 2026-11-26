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
import type { AuditRecord } from "@/service/audit";
import type { ProjectParameterRecord } from "@/service/project/parameter";
import type { TaskInstanceRecord } from "@/service/workflow/task-instances";
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
    }
  | {
      id: string;
      kind: "workflow-instance";
      instanceId: number;
      title: string;
    }
  | {
      id: string;
      kind: "catalog-table";
      dataSourceId: string;
      dataSourceName: string;
      database: string;
      schema?: string;
      objectType?: string;
      tableName: string;
      title: string;
    }
  | {
      id: string;
      kind: "audit-record";
      record: AuditRecord;
      title: string;
    }
  | {
      id: string;
      kind: "backfill-workflow";
      workflow: WorkflowDefinitionRecord;
      title: string;
    }
  | {
      id: string;
      kind: "workflow-lineage";
      workflow: WorkflowDefinitionRecord;
      title: string;
    }
  | {
      id: string;
      kind: "task-instance";
      record: TaskInstanceRecord;
      title: string;
    }
  | {
      id: string;
      kind: "project-parameter";
      record: ProjectParameterRecord | null;
      title: string;
    };

type DataDevelopmentWorkbenchContextValue = {
  tabs: WorkflowWorkbenchTab[];
  dirtyTabIds: ReadonlySet<string>;
  setTabDirty: (id: string, dirty: boolean) => void;
  activeTabId: string | null;
  activeTab: WorkflowWorkbenchTab | null;
  openWorkflow: (workflow: WorkflowDefinitionRecord) => void;
  openTask: (
    workflowCode: number,
    task: WorkflowTaskRecord,
    nodeId?: string,
    releaseState?: string,
    workflow?: WorkflowDefinitionRecord,
  ) => void;
  workflowStoreCodes: ReadonlySet<number>;
  openWorkflowInstance: (instanceId: number, title?: string) => void;
  openCatalogTable: (
    dataSourceId: string | number,
    dataSourceName: string,
    database: string,
    tableName: string,
    schema?: string,
    objectType?: string,
  ) => void;
  openAuditRecord: (record: AuditRecord, title?: string) => void;
  openBackfillWorkflow: (workflow: WorkflowDefinitionRecord) => void;
  openWorkflowLineage: (workflow: WorkflowDefinitionRecord) => void;
  openTaskInstanceRecord: (record: TaskInstanceRecord) => void;
  openProjectParameter: (record: ProjectParameterRecord | null) => void;
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
  const [workflowStoreCodes, setWorkflowStoreCodes] = useState<Set<number>>(
    () => new Set(),
  );
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
    workflow?: WorkflowDefinitionRecord,
  ) => {
    const taskTabId = "task:" + workflowCode + ":" + task.code;

    setTabs((current) => {
      const workflowTab = current.find(
        (tab): tab is Extract<WorkflowWorkbenchTab, { kind: "workflow" }> =>
          tab.kind === "workflow" && tab.workflowCode === workflowCode,
      );
      const nextTabs = [...current];

      // A task editor applies changes to its parent workflow store. Keep the
      // workflow tab mounted even when the task tab is the active view.
      if (!workflowTab && workflow) {
        const existingTaskIndex = nextTabs.findIndex((tab) => tab.id === taskTabId);
        nextTabs.splice(
          existingTaskIndex >= 0 ? existingTaskIndex : nextTabs.length,
          0,
          toWorkflowTab(workflow),
        );
      }

      const nextTask = toTaskTab(
        workflowCode,
        task,
        nodeId,
        releaseState ??
          workflowTab?.releaseState ??
          workflow?.releaseState ??
          "OFFLINE",
      );
      const taskIndex = nextTabs.findIndex((tab) => tab.id === taskTabId);
      if (taskIndex < 0) return [...nextTabs, nextTask];

      return nextTabs.map((tab, index) =>
        index === taskIndex ? nextTask : tab,
      );
    });

    setActiveTabId(taskTabId);
  };

  const openWorkflowInstance = (instanceId: number, title = "运行 #" + instanceId) =>
    openTab({
      id: "workflow-instance:" + instanceId,
      kind: "workflow-instance",
      instanceId,
      title,
    });

  const openCatalogTable = (
    dataSourceId: string | number,
    dataSourceName: string,
    database: string,
    tableName: string,
    schema?: string,
    objectType = "TABLE",
  ) =>
    openTab({
      id:
        "catalog-table:" +
        String(dataSourceId) +
        ":" +
        database +
        ":" +
        (schema ?? "") +
        ":" +
        objectType +
        ":" +
        tableName,
      kind: "catalog-table",
      dataSourceId: String(dataSourceId),
      dataSourceName,
      database,
      schema,
      objectType,
      tableName,
      title: tableName,
    });

  const openAuditRecord = (record: AuditRecord, title = "操作详情") =>
    openTab({
      id:
        "audit-record:" +
        String(record.createTime ?? "") +
        ":" +
        String(record.modelType ?? "") +
        ":" +
        String(record.modelName ?? "") +
        ":" +
        String(record.operation ?? ""),
      kind: "audit-record",
      record,
      title,
    });

  const openBackfillWorkflow = (workflow: WorkflowDefinitionRecord) =>
    openTab({
      id: "backfill-workflow:" + workflow.code,
      kind: "backfill-workflow",
      workflow,
      title: "补数 · " + workflow.name,
    });

  const openWorkflowLineage = (workflow: WorkflowDefinitionRecord) =>
    openTab({
      id: "workflow-lineage:" + workflow.code,
      kind: "workflow-lineage",
      workflow,
      title: "依赖 · " + workflow.name,
    });

  const openTaskInstanceRecord = (record: TaskInstanceRecord) =>
    openTab({
      id: "task-instance:" + record.id,
      kind: "task-instance",
      record,
      title: record.name || "任务 #" + record.id,
    });

  const openProjectParameter = (record: ProjectParameterRecord | null) =>
    openTab({
      id: record ? "project-parameter:" + record.code : "project-parameter:new",
      kind: "project-parameter",
      record,
      title: record?.paramName || "新建项目参数",
    });

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
    setTabs((current) => current.map((tab) => {
      if (tab.kind !== "workflow" && tab.kind !== "task") return tab;
      return tab.workflowCode === workflowCode ? { ...tab, releaseState } : tab;
    }));
  };

  const registerWorkflowStore = useCallback(
    (workflowCode: number, store: WorkflowStoreApi) => {
    workflowStoresRef.current.set(workflowCode, store);
    setWorkflowStoreCodes((current) => {
      if (current.has(workflowCode)) return current;
      const next = new Set(current);
      next.add(workflowCode);
      return next;
    });

    return () => {
      if (workflowStoresRef.current.get(workflowCode) !== store) return;
      workflowStoresRef.current.delete(workflowCode);
      setWorkflowStoreCodes((current) => {
        if (!current.has(workflowCode)) return current;
        const next = new Set(current);
        next.delete(workflowCode);
        return next;
      });
    };
    },
    [],
  );

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
    workflowStoreCodes,
    setTabDirty,
    activeTabId,
    activeTab,
    openWorkflow,
    openTask,
    openWorkflowInstance,
    openCatalogTable,
    openAuditRecord,
    openBackfillWorkflow,
    openWorkflowLineage,
    openTaskInstanceRecord,
    openProjectParameter,
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
  }), [tabs, dirtyTabIds, workflowStoreCodes, setTabDirty, activeTabId, activeTab, registerWorkflowStore, getWorkflowStore]);

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
