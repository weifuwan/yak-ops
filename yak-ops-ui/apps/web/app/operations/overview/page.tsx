import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  BellRing,
  CircleAlert,
  Clock3,
  GitBranch,
  ListChecks,
  RefreshCw,
  type LucideIcon,
} from "lucide-react";
import {
  Button,
  Card,
  CardContent,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Skeleton,
} from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import { listAlertRecords } from "@/service/alert";
import {
  getTaskInstanceStateCount,
  getWorkflowDefinitionCount,
  getWorkflowInstanceStateCount,
  listErrorCommands,
  listPendingCommands,
  type StatusCountRecord,
} from "@/service/operations/analysis";
import { OperationsPageShell } from "../components/operations-page-shell";
import { instanceStatusText, workflowStateText } from "../presentation";

type RangeKey = "1d" | "7d" | "30d";

const RANGE_ITEMS: Record<RangeKey, string> = {
  "1d": "最近 24 小时",
  "7d": "最近 7 天",
  "30d": "最近 30 天",
};

const number = (value: number) => value.toLocaleString("zh-CN");
const ALERT_TYPE_LABELS: Record<string, string> = {
  WORKFLOW_INSTANCE_FAILURE: "工作流失败",
  WORKFLOW_INSTANCE_SUCCESS: "工作流成功",
  WORKFLOW_INSTANCE_BLOCKED: "工作流阻塞",
  WORKFLOW_INSTANCE_TIMEOUT: "工作流超时",
  FAULT_TOLERANCE_WARNING: "服务异常",
  TASK_FAILURE: "任务失败",
  TASK_SUCCESS: "任务成功",
  TASK_TIMEOUT: "任务超时",
  TASK_RESULT: "任务结果",
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

function MetricCard({
  label,
  value,
  icon: Icon,
  hint,
}: {
  label: string;
  value: number;
  icon: LucideIcon;
  hint?: string;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3">
          <div className="text-xs text-[var(--yak-components-muted-text)]">{label}</div>
          <Icon size={18} aria-hidden="true" className="text-[var(--yak-components-muted-text)]" />
        </div>
        <div className="mt-3 text-3xl font-semibold tabular-nums">{number(value)}</div>
        {hint ? (
          <div className="mt-2 text-xs text-[var(--yak-components-muted-text)]">{hint}</div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function MetricsSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
      {Array.from({ length: 5 }, (_, index) => (
        <Card key={index}>
          <CardContent className="space-y-4 p-5">
            <Skeleton width={90} height={14} />
            <Skeleton width={70} height={30} />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function StatusPanel({
  title,
  records,
  total,
  label,
}: {
  title: string;
  records: StatusCountRecord[];
  total: number;
  label: (state: string) => string;
}) {
  const visible = useMemo(
    () =>
      [...records]
        .filter((item) => item.count > 0)
        .sort((a, b) => b.count - a.count),
    [records],
  );

  return (
    <Card>
      <CardContent className="p-5">
        <div className="mb-5 flex items-center justify-between">
          <div className="text-sm font-medium">{title}</div>
          <div className="text-xs text-[var(--yak-components-muted-text)]">
            总数 {number(total)}
          </div>
        </div>
        {visible.length ? (
          <div className="space-y-4">
            {visible.map((item) => {
              const percent = total > 0 ? Math.round((item.count / total) * 100) : 0;
              return (
                <div key={item.state}>
                  <div className="mb-1.5 flex items-center justify-between gap-4 text-xs">
                    <span className="truncate">{label(item.state)}</span>
                    <span className="shrink-0 tabular-nums text-[var(--yak-components-muted-text)]">
                      {number(item.count)} · {percent}%
                    </span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-[var(--yak-color-hover)]">
                    <div
                      className="h-full rounded-full bg-[var(--yak-color-primary)]"
                      style={{ width: Math.max(percent, item.count > 0 ? 2 : 0) + "%" }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="py-10 text-center text-sm text-[var(--yak-components-muted-text)]">
            暂无运行数据
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export function OperationsOverviewPage() {
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [range, setRange] = useState<RangeKey>("7d");
  const dates = useMemo(() => rangeDates(range), [range]);

  const workflowQuery = useQuery({
    queryKey: ["operations", "overview", "workflow-state", scope, range],
    queryFn: ({ signal }) =>
      getWorkflowInstanceStateCount(dates.startDate, dates.endDate, signal),
    enabled: Boolean(scope),
  });
  const taskQuery = useQuery({
    queryKey: ["operations", "overview", "task-state", scope, range],
    queryFn: ({ signal }) => getTaskInstanceStateCount(dates.startDate, dates.endDate, signal),
    enabled: Boolean(scope),
  });
  const definitionQuery = useQuery({
    queryKey: ["operations", "overview", "workflow-definitions", scope],
    queryFn: ({ signal }) => getWorkflowDefinitionCount(signal),
    enabled: Boolean(scope),
    staleTime: 30_000,
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

  const loading =
    workflowQuery.isPending ||
    taskQuery.isPending ||
    definitionQuery.isPending ||
    pendingQuery.isPending ||
    errorQuery.isPending;

  const refresh = async () => {
    await Promise.all([
      workflowQuery.refetch(),
      taskQuery.refetch(),
      definitionQuery.refetch(),
      pendingQuery.refetch(),
      errorQuery.refetch(),
      alertsQuery.refetch(),
    ]);
  };

  const hasError =
    workflowQuery.isError ||
    taskQuery.isError ||
    definitionQuery.isError ||
    pendingQuery.isError ||
    errorQuery.isError;

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
              className={
                workflowQuery.isFetching || taskQuery.isFetching ? "animate-spin" : undefined
              }
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

        {loading ? (
          <MetricsSkeleton />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <MetricCard
              label="工作流定义"
              value={definitionQuery.data?.count ?? 0}
              icon={GitBranch}
              hint="当前项目"
            />
            <MetricCard
              label="工作流实例"
              value={workflowQuery.data?.totalCount ?? 0}
              icon={Activity}
              hint={RANGE_ITEMS[range]}
            />
            <MetricCard
              label="任务实例"
              value={taskQuery.data?.totalCount ?? 0}
              icon={ListChecks}
              hint={RANGE_ITEMS[range]}
            />
            <MetricCard
              label="待处理命令"
              value={pendingQuery.data?.total ?? 0}
              icon={Clock3}
              hint="当前项目"
            />
            <MetricCard
              label="异常命令"
              value={errorQuery.data?.total ?? 0}
              icon={CircleAlert}
              hint="当前项目"
            />
          </div>
        )}

        <div className="grid gap-4 xl:grid-cols-2">
          <StatusPanel
            title="工作流实例状态"
            records={workflowQuery.data?.workflowInstanceStatusCounts ?? []}
            total={workflowQuery.data?.totalCount ?? 0}
            label={workflowStateText}
          />
          <StatusPanel
            title="任务实例状态"
            records={taskQuery.data?.taskInstanceStatusCounts ?? []}
            total={taskQuery.data?.totalCount ?? 0}
            label={instanceStatusText}
          />
        </div>

        <Card>
          <CardContent className="p-5">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-medium">
                <BellRing size={16} aria-hidden="true" />
                最近告警
              </div>
              <div className="text-xs text-[var(--yak-components-muted-text)]">
                {alertsQuery.data?.total ?? 0} 条记录
              </div>
            </div>
            {alertsQuery.isPending ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }, (_, index) => (
                  <Skeleton key={index} width="100%" height={34} />
                ))}
              </div>
            ) : (alertsQuery.data?.totalList ?? []).length ? (
              <div className="divide-y divide-[var(--yak-components-panel-border)]">
                {(alertsQuery.data?.totalList ?? []).map((record) => (
                  <div key={record.id} className="flex items-center gap-4 py-3 text-xs">
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium">{record.title || "告警"}</div>
                      <div className="mt-0.5 truncate text-[var(--yak-components-muted-text)]">
                        {record.alertType ? ALERT_TYPE_LABELS[record.alertType] ?? record.alertType : "-"}
                      </div>
                    </div>
                    <div className="shrink-0 text-[var(--yak-components-muted-text)]">
                      {record.createTime || "-"}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="py-10 text-center text-sm text-[var(--yak-components-muted-text)]">
                暂无告警记录
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </OperationsPageShell>
  );
}
