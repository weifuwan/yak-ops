import { useQueries, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { listDataSources } from "@/service/datasource";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  listDataSyncTasks,
  getLatestDataSyncWorkflow,
  isWorkflowActive,
  listLatestDataSyncTaskInstances,
  type DataSyncTaskListRecord,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import { getUsersByIds } from "@/service/user";

import type { TaskListFilters } from "./filters";
import { listQueryKeys } from "./query-keys";

const PAGE_SIZE = 20;
const EMPTY_TASKS: DataSyncTaskListRecord[] = [];

function isReadableTask(record: DataSyncTaskListRecord): record is DataSyncTaskRecord {
  return !("configurationError" in record);
}

export function useTaskPage(scope: string, filters: TaskListFilters) {
  const query = useQuery({
    queryKey: [
      ...listQueryKeys.tasks(scope),
      filters.pageNo,
      filters.keyword,
      filters.status,
    ],
    queryFn: ({ signal }) =>
      listDataSyncTasks(
        {
          pageNo: filters.pageNo,
          pageSize: PAGE_SIZE,
          keyword: filters.keyword || undefined,
          status: filters.status === "ALL" ? undefined : filters.status,
        },
        signal,
      ),
    enabled: Boolean(scope),
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === scope ? previous : undefined,
  });
  const records = query.data?.totalList ?? EMPTY_TASKS;
  const validRecords = useMemo(() => records.filter(isReadableTask), [records]);
  return {
    records,
    validRecords,
    invalidCount: records.length - validRecords.length,
    total: query.data?.total ?? 0,
    loading: query.isPending || query.isFetching,
    error: query.isError,
    safeToAct: query.isSuccess && !query.isPlaceholderData && !query.isFetching,
    retry: () => void query.refetch(),
  };
}

export function useListDataSources(scope: string) {
  const { currentWorkspace } = useWorkspace();
  const query = useQuery({
    queryKey: listQueryKeys.dataSources(scope),
    queryFn: ({ signal }) => listDataSources({ pageNo: 1, pageSize: 200 }, signal),
    enabled: Boolean(scope && currentWorkspace?.id),
  });
  const dataSourceMap = useMemo(
    () =>
      new Map(
        (query.data?.totalList ?? []).flatMap((item) => (item.id ? [[item.id, item] as const] : [])),
      ),
    [query.data],
  );
  return { dataSourceMap, loading: query.isPending };
}

export function useListUpdateUsers(scope: string, records: DataSyncTaskRecord[]) {
  const ids = [
    ...new Set(
      records
        .map((item) => item.updateBy)
        .filter((id): id is string => Boolean(id && id !== "system")),
    ),
  ].sort();
  const query = useQuery({
    queryKey: [...listQueryKeys.users(scope), ids],
    queryFn: () => getUsersByIds(ids),
    enabled: Boolean(scope) && ids.length > 0,
  });
  const users = useMemo(
    () => new Map((query.data ?? []).map((user) => [user.id, user] as const)),
    [query.data],
  );
  return { users, loading: ids.length > 0 && query.isPending };
}

export function useListInstances(scope: string, records: DataSyncTaskRecord[], safeToAct: boolean) {
  const ids = records.map((item) => item.id).sort();
  const workflowQueries = useQueries({
    queries: records.map((record) => ({
      queryKey: [...listQueryKeys.instances(scope), "workflow", record.workflowCode],
      queryFn: ({ signal }) =>
        getLatestDataSyncWorkflow(record.workflowCode, signal),
      enabled: Boolean(scope),
      refetchInterval: (result) =>
        result.state.data && isWorkflowActive(result.state.data.state) ? 2000 : 15000,
    })),
  });
  const workflowRunning = workflowQueries.some(
    (result) => result.data && isWorkflowActive(result.data.state),
  );
  const query = useQuery({
    queryKey: [...listQueryKeys.instances(scope), ids],
    queryFn: async ({ signal }) => {
      const [latest, active] = await Promise.all([
        listLatestDataSyncTaskInstances(ids, false, signal),
        listLatestDataSyncTaskInstances(ids, true, signal),
      ]);
      return { latest, active };
    },
    enabled: Boolean(scope) && ids.length > 0,
    refetchInterval: (result) =>
      result.state.data?.active.length || workflowRunning ? 2000 : 15000,
  });
  const latestByTask = useMemo(
    () => new Map((query.data?.latest ?? []).map((item) => [item.definitionId, item] as const)),
    [query.data],
  );
  const activeByTask = useMemo(
    () => new Map((query.data?.active ?? []).map((item) => [item.definitionId, item] as const)),
    [query.data],
  );
  const workflowsByTask = new Map(
    records.map((record, index) => [record.id, workflowQueries[index]?.data] as const),
  );
  return {
    latestByTask,
    activeByTask,
    workflowsByTask,
    loading: ids.length > 0 && (query.isPending || workflowQueries.some((result) => result.isPending)),
    ready: safeToAct && (ids.length === 0 || (query.isSuccess &&
      workflowQueries.every((result) => result.isSuccess))),
    error: query.isError || workflowQueries.some((result) => result.isError),
  };
}
