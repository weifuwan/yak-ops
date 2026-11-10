import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  getWorkflowInstanceDetail,
  getWorkflowInstanceTasks,
  listWorkflowInstances,
} from "@/service/workflow/management";
import type { PanelKey } from "../shared/types";

const HISTORY_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
const OPERATIONS_PAGE_SIZE = 20;

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
  const [historyPagination, setHistoryPagination] = useState({
    workflowCode,
    pageNo: 1,
    pageSize: HISTORY_PAGE_SIZE_OPTIONS[0],
  });

  // Keep history pagination independent from the operations overview's fixed recent-20 query.
  const historyPageNo =
    historyPagination.workflowCode === workflowCode ? historyPagination.pageNo : 1;
  const historyPageSize =
    historyPagination.workflowCode === workflowCode
      ? historyPagination.pageSize
      : HISTORY_PAGE_SIZE_OPTIONS[0];
  const isOperationsPanel = activePanel === "ops";
  const requestPageNo = isOperationsPanel ? 1 : historyPageNo;
  const requestPageSize = isOperationsPanel ? OPERATIONS_PAGE_SIZE : historyPageSize;

  const instancesQuery = useQuery({
    queryKey: [
      "workflow-management",
      "instances",
      workspaceId,
      workflowCode,
      isOperationsPanel ? "operations" : "history",
      requestPageNo,
      requestPageSize,
    ],
    queryFn: ({ signal }) =>
      listWorkflowInstances(workflowCode, requestPageNo, requestPageSize, signal),
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

  const onHistoryPageChange = (pageNo: number, pageSize: number) => {
    setHistoryPagination({
      workflowCode,
      pageNo: pageSize === historyPageSize ? pageNo : 1,
      pageSize,
    });
  };

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
    historyPageNo,
    historyPageSize,
    historyTotal: instancesQuery.data?.total ?? 0,
    onHistoryPageChange,
  };
}
