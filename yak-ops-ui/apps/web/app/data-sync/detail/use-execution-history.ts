import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
  getDataSyncInstance,
  listDataSyncInstances,
  type DataSyncInstanceSummary,
  type DataSyncInstanceStatus,
} from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution/types";
import { isActiveDataSyncInstance } from "../execution/execution-detail";
import { detailQueryKeys } from "./query-keys";

const PAGE_SIZE = 20;
const POLL_MILLIS = 2000;
const EMPTY: DataSyncInstanceSummary[] = [];

export function useExecutionHistory(
  scope: string,
  taskId: string | undefined,
  activeInstance: TaskInstanceRecord | undefined,
  hasEnabledSchedule: boolean,
) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId");
  const activeTab = searchParams.get("tab") === "log" ? "log" : "status";
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const enabled = Boolean(scope && taskId);
  const instancesQuery = useQuery({
    queryKey: detailQueryKeys.instancePage(scope, taskId || "", pageNo, status),
    queryFn: () =>
      listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        taskId: taskId!,
        status: status === "ALL" ? undefined : status,
      }),
    enabled,
    refetchInterval: (query) =>
      autoRefresh &&
      (Boolean(activeInstance) ||
        hasEnabledSchedule ||
        Boolean(query.state.data?.totalList.some(isActiveDataSyncInstance)))
        ? POLL_MILLIS
        : false,
  });
  const instances = instancesQuery.data?.totalList ?? EMPTY;
  const total = instancesQuery.data?.total ?? 0;
  const instancesLoading = instancesQuery.isPending;
  const selectedQuery = useQuery({
    queryKey: detailQueryKeys.instance(scope, taskId || "", executionId || ""),
    queryFn: () => getDataSyncInstance(executionId!),
    enabled: enabled && Boolean(executionId),
    retry: false,
    refetchInterval: (query) =>
      autoRefresh &&
      (Boolean(activeInstance) ||
        hasEnabledSchedule ||
        instances.some(isActiveDataSyncInstance) ||
        isActiveDataSyncInstance(query.state.data))
        ? POLL_MILLIS
        : false,
  });
  const selectedInstance =
    selectedQuery.data?.id === executionId && selectedQuery.data?.taskId === taskId
      ? selectedQuery.data
      : undefined;
  const selectedLoading = Boolean(executionId) && selectedQuery.isPending;

  // Only first-load errors or cross-definition results clear the URL selection.
  // Background failures keep the previous verified observation.
  useEffect(() => {
    if (!executionId || !taskId) return;
    if (
      (selectedQuery.isError && !selectedQuery.data) ||
      (selectedQuery.data && selectedQuery.data.taskId !== taskId)
    ) {
      setSearchParams(
        (current) => {
          if (current.get("executionId") !== executionId) return current;
          const next = new URLSearchParams(current);
          next.delete("executionId");
          return next;
        },
        { replace: true },
      );
    }
  }, [executionId, taskId, selectedQuery.isError, selectedQuery.data, setSearchParams]);

  useEffect(() => {
    if (instancesLoading || executionId || instances.length === 0) return;
    setSearchParams(
      (current) => {
        if (current.has("executionId")) return current;
        const next = new URLSearchParams(current);
        next.set("executionId", instances[0].id);
        return next;
      },
      { replace: true },
    );
  }, [executionId, instances, instancesLoading, setSearchParams]);

  const loadInstances = async () => {
    await instancesQuery.refetch();
  };
  const refreshExecution = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await Promise.all([
        instancesQuery.refetch(),
        executionId ? selectedQuery.refetch() : Promise.resolve(),
      ]);
    } finally {
      setRefreshing(false);
    }
  };
  const selectInstance = (id: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("executionId", id);
      return next;
    });
  };
  const changeTab = (value: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (value === "status") next.delete("tab");
      else next.set("tab", value);
      return next;
    });
  };
  const changePage = (page: number) => {
    setPageNo(page);
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete("executionId");
        return next;
      },
      { replace: true },
    );
  };
  const changeStatus = (value: DataSyncInstanceStatus | "ALL") => {
    setStatus(value);
    changePage(1);
  };
  const selectSubmitted = (id: string) => {
    setStatus("ALL");
    setPageNo(1);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("executionId", id);
      next.delete("tab");
      return next;
    });
    void queryClient.invalidateQueries({
      queryKey: detailQueryKeys.instances(scope, taskId || ""),
    });
  };
  const refreshAfterCancel = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: detailQueryKeys.instances(scope, taskId || "") }),
      selectedQuery.refetch(),
    ]);
  };
  return {
    executionId,
    activeTab,
    instances,
    instancesLoading,
    status,
    pageNo,
    total,
    selectedInstance,
    selectedLoading,
    autoRefresh,
    setAutoRefresh,
    refreshing,
    loadInstances,
    refreshExecution,
    selectInstance,
    changeTab,
    changePage,
    changeStatus,
    selectSubmitted,
    refreshAfterCancel,
    listError: instancesQuery.isError && !instancesQuery.data,
  };
}
