import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import { listAlertRecords } from "@/service/alert";
import {
  getTaskInstanceStateCount,
  getWorkflowInstanceStateCount,
  listErrorCommands,
  listPendingCommands,
} from "@/service/operations/analysis";
import { OperationsPageShell } from "../components/operations-page-shell";
import { instanceStatusText, workflowStateText } from "../presentation";
import { OverviewMetrics } from "./components/overview-metrics";
import { RecentAlerts } from "./components/recent-alerts";
import { SchedulingIssues } from "./components/scheduling-issues";
import { StatusOverviewCard } from "./components/status-overview-card";

type RangeKey = "1d" | "7d" | "30d";

const RANGE_ITEMS: Record<RangeKey, string> = {
  "1d": "最近 24 小时",
  "7d": "最近 7 天",
  "30d": "最近 30 天",
};

function formatDate(value: Date) {
  const pad = (item: number) => String(item).padStart(2, "0");
  return [
    value.getFullYear(),
    "-",
    pad(value.getMonth() + 1),
    "-",
    pad(value.getDate()),
    " ",
    pad(value.getHours()),
    ":",
    pad(value.getMinutes()),
    ":",
    pad(value.getSeconds()),
  ].join("");
}

function rangeDates(range: RangeKey) {
  const end = new Date();
  const start = new Date(end);
  const days = range === "1d" ? 1 : range === "7d" ? 7 : 30;
  start.setDate(start.getDate() - days);
  return { startDate: formatDate(start), endDate: formatDate(end) };
}

export function OperationsOverviewPage() {
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [range, setRange] = useState<RangeKey>("7d");

  const workflowQuery = useQuery({
    queryKey: ["operations", "overview", "workflow-state", scope, range],
    queryFn: ({ signal }) => {
      const dates = rangeDates(range);
      return getWorkflowInstanceStateCount(dates.startDate, dates.endDate, signal);
    },
    enabled: Boolean(scope),
    refetchInterval: 30_000,
  });

  const taskQuery = useQuery({
    queryKey: ["operations", "overview", "task-state", scope, range],
    queryFn: ({ signal }) => {
      const dates = rangeDates(range);
      return getTaskInstanceStateCount(dates.startDate, dates.endDate, signal);
    },
    enabled: Boolean(scope),
    refetchInterval: 30_000,
  });

  const pendingQuery = useQuery({
    queryKey: ["operations", "overview", "pending-commands", scope],
    queryFn: ({ signal }) => listPendingCommands(1, 1, signal),
    enabled: Boolean(scope),
    refetchInterval: 10_000,
  });

  const errorQuery = useQuery({
    queryKey: ["operations", "overview", "error-commands", scope],
    queryFn: ({ signal }) => listErrorCommands(1, 1, signal),
    enabled: Boolean(scope),
    refetchInterval: 10_000,
  });

  const alertsQuery = useQuery({
    queryKey: ["operations", "overview", "alerts", scope],
    queryFn: ({ signal }) => listAlertRecords({ pageNo: 1, pageSize: 5 }, signal),
    enabled: Boolean(scope),
    refetchInterval: 15_000,
  });

  const refresh = async () => {
    await Promise.all([
      workflowQuery.refetch(),
      taskQuery.refetch(),
      pendingQuery.refetch(),
      errorQuery.refetch(),
      alertsQuery.refetch(),
    ]);
  };

  const hasError =
    workflowQuery.isError ||
    taskQuery.isError ||
    pendingQuery.isError ||
    errorQuery.isError ||
    alertsQuery.isError;

  const isRefreshing =
    workflowQuery.isFetching ||
    taskQuery.isFetching ||
    pendingQuery.isFetching ||
    errorQuery.isFetching ||
    alertsQuery.isFetching;

  return (
    <OperationsPageShell
      title="运维概览"
      extra={
        <div className="flex items-center gap-2">
          <div className="w-[150px]">
            <Select<RangeKey>
              size="small"
              items={RANGE_ITEMS}
              value={range}
              onValueChange={(value) => setRange((value ?? "7d") as RangeKey)}
            >
              <SelectTrigger variant="outlined">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(RANGE_ITEMS).map(([value, label]) => (
                  <SelectItem key={value} value={value as RangeKey}>
                    <SelectItemText>{label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button size="sm" variant="secondary" onClick={() => void refresh()}>
            <RefreshCw
              size={14}
              aria-hidden="true"
              className={isRefreshing ? "animate-spin" : undefined}
            />
            刷新
          </Button>
        </div>
      }
    >
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto">
        {hasError ? (
          <div className="rounded-md border border-[var(--yak-components-panel-border)] px-4 py-3 text-sm text-[var(--yak-components-muted-text)]">
            部分运维统计加载失败，当前页面会保留已经成功返回的数据。
          </div>
        ) : null}

        <OverviewMetrics
          workflow={workflowQuery.data}
          task={taskQuery.data}
          workflowLoading={workflowQuery.isPending}
          taskLoading={taskQuery.isPending}
        />

        <div className="grid gap-4 xl:grid-cols-2">
          <StatusOverviewCard
            title="工作流实例状态"
            records={workflowQuery.data?.workflowInstanceStatusCounts ?? []}
            total={workflowQuery.data?.totalCount ?? 0}
            label={workflowStateText}
            loading={workflowQuery.isPending}
            fetching={workflowQuery.isFetching && !workflowQuery.isPending}
            error={workflowQuery.isError}
          />
          <StatusOverviewCard
            title="任务实例状态"
            records={taskQuery.data?.taskInstanceStatusCounts ?? []}
            total={taskQuery.data?.totalCount ?? 0}
            label={instanceStatusText}
            loading={taskQuery.isPending}
            fetching={taskQuery.isFetching && !taskQuery.isPending}
            error={taskQuery.isError}
          />
        </div>

        <RecentAlerts
          records={alertsQuery.data?.totalList ?? []}
          total={alertsQuery.data?.total}
          loading={alertsQuery.isPending}
          error={alertsQuery.isError}
        />

        <SchedulingIssues
          pending={pendingQuery.data?.total}
          errors={errorQuery.data?.total}
          pendingLoading={pendingQuery.isPending}
          errorsLoading={errorQuery.isPending}
        />
      </div>
    </OperationsPageShell>
  );
}
