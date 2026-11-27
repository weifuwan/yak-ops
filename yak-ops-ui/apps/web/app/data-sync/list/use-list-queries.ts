import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

import { listDataSources } from "@/service/datasource";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  listDataSyncTasks,
  listDataSyncTaskExecutionStates,
  type DataSyncTaskListRecord,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import { isTaskInstanceActive } from "@/service/data-sync/execution";
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
      filters.scope,
    ],
    queryFn: ({ signal }) =>
      listDataSyncTasks(
        {
          pageNo: filters.pageNo,
          pageSize: PAGE_SIZE,
          keyword: filters.keyword || undefined,
          status: filters.status === "ALL" ? undefined : filters.status,
          scope: filters.scope === "ALL" ? undefined : filters.scope,
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
  const query = useQuery({
    queryKey: [...listQueryKeys.instances(scope), ids],
    queryFn: ({ signal }) => listDataSyncTaskExecutionStates(ids, signal),
    enabled: Boolean(scope) && safeToAct && ids.length > 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const stateByTask = useMemo(
    () => new Map((query.data ?? []).map((item) => [item.taskCode, item] as const)),
    [query.data],
  );
  const latestByTask = useMemo(
    () =>
      new Map(
        (query.data ?? []).flatMap((item) =>
          item.taskInstance ? [[item.taskCode, item.taskInstance] as const] : [],
        ),
      ),
    [query.data],
  );
  const activeByTask = useMemo(
    () =>
      new Map(
        (query.data ?? []).flatMap((item) =>
          item.taskInstance && isTaskInstanceActive(item.taskInstance.status)
            ? [[item.taskCode, item.taskInstance] as const]
            : [],
        ),
      ),
    [query.data],
  );
  const workflowsByTask = useMemo(
    () =>
      new Map(
        records.map((record) => [
          record.id,
          stateByTask.get(record.id)?.workflow ?? undefined,
        ] as const),
      ),
    [records, stateByTask],
  );
  const stateReadyByTask = new Map(
    records.map((record) => [
      record.id,
      safeToAct && query.isSuccess && !query.isFetching && stateByTask.has(record.id),
    ] as const),
  );
  const stateLoadingByTask = new Map(
    records.map((record) => [
      record.id,
      !safeToAct || query.isPending || query.isFetching,
    ] as const),
  );
  const stateErrorByTask = new Set(
    query.isError && safeToAct ? records.map((record) => record.id) : [],
  );
  const refresh = () => {
    if (safeToAct && ids.length > 0) void query.refetch();
  };
  return {
    latestByTask,
    activeByTask,
    workflowsByTask,
    stateReadyByTask,
    stateLoadingByTask,
    stateErrorByTask,
    error: query.isError && safeToAct,
    loading: query.isPending || query.isFetching,
    refreshing: query.isFetching,
    refresh,
  };
}
