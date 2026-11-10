import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  getWorkflowInstanceDetail,
  getWorkflowInstanceTasks,
  listWorkflowInstances,
} from "@/service/workflow/management";
import type { PanelKey } from "../shared/types";

interface UseWorkflowInstancesOptions {
  workflowCode: number;
  workspaceId: string;
  activePanel: PanelKey | null;
}

export function useWorkflowInstances({
  workflowCode,
  workspaceId,
  activePanel,
}: UseWorkflowInstancesOptions) {
  const [selectedInstanceId, setSelectedInstanceId] = useState<number | null>(null);

  const instancesQuery = useQuery({
    queryKey: ["workflow-management", "instances", workspaceId, workflowCode],
    queryFn: ({ signal }) => listWorkflowInstances(workflowCode, signal),
    enabled: Boolean(
      workspaceId && (activePanel === "history" || activePanel === "ops"),
    ),
  });

  const selectedInstanceQuery = useQuery({
    queryKey: [
      "workflow-management",
      "instance-detail",
      workspaceId,
      selectedInstanceId,
    ],
    queryFn: ({ signal }) => getWorkflowInstanceDetail(selectedInstanceId!, signal),
    enabled: Boolean(
      workspaceId &&
        selectedInstanceId !== null &&
        (activePanel === "history" || activePanel === "ops"),
    ),
  });

  const instanceTasksQuery = useQuery({
    queryKey: [
      "workflow-management",
      "instance-tasks",
      workspaceId,
      selectedInstanceId,
    ],
    queryFn: ({ signal }) => getWorkflowInstanceTasks(selectedInstanceId!, signal),
    enabled: Boolean(
      workspaceId &&
        selectedInstanceId !== null &&
        (activePanel === "history" || activePanel === "ops"),
    ),
  });

  const instances = instancesQuery.data?.totalList ?? [];
  const selectedInstance = instances.find((item) => item.id === selectedInstanceId);
  const tasks = instanceTasksQuery.data?.taskList ?? [];
  const activeInstanceCount = instances.filter((item) =>
    ["SUBMITTED_SUCCESS", "RUNNING_EXECUTION", "READY_PAUSE", "READY_STOP", "SERIAL_WAIT"].includes(
      item.state,
    ),
  ).length;
  const failedInstanceCount = instances.filter((item) => item.state === "FAILURE").length;
  const successInstanceCount = instances.filter((item) => item.state === "SUCCESS").length;

  return {
    selectedInstanceId,
    setSelectedInstanceId,
    instancesQuery,
    selectedInstanceQuery,
    instanceTasksQuery,
    instances,
    selectedInstance,
    tasks,
    activeInstanceCount,
    failedInstanceCount,
    successInstanceCount,
  };
}
