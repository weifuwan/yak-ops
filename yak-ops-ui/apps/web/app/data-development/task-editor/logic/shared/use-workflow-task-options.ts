import { useEffect, useState } from "react";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "../../../workbench-context";

export function useWorkflowTaskOptions(
  workflowCode: number,
  currentTaskCode: number,
): WorkflowTaskRecord[] {
  const { getWorkflowStore } = useDataDevelopmentWorkbench();
  const [tasks, setTasks] = useState<WorkflowTaskRecord[]>([]);

  useEffect(() => {
    const store = getWorkflowStore(workflowCode);
    if (!store) {
      setTasks([]);
      return;
    }

    const sync = () => {
      const next = store.getState().nodes
        .map((node) => node.data.task)
        .filter((task): task is WorkflowTaskRecord =>
          Boolean(task && task.code !== currentTaskCode),
        )
        .sort((a, b) => a.name.localeCompare(b.name, "zh-CN"));
      setTasks(next);
    };

    sync();
    return store.subscribe(sync);
  }, [currentTaskCode, getWorkflowStore, workflowCode]);

  return tasks;
}
