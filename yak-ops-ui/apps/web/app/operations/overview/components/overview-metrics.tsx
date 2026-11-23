import {
  Activity,
  CheckCircle2,
  CircleAlert,
  ListChecks,
  LoaderCircle,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { Card, CardContent, Skeleton } from "@yak-ops/yak-ui";

import type {
  TaskInstanceCount,
  WorkflowInstanceCount,
} from "@/service/operations/analysis";
import { countStatus, formatPercent, workflowSuccessRate } from "../utils/statistics";

function number(value: number) {
  return value.toLocaleString("zh-CN");
}

function MetricCard({
  label,
  value,
  icon: Icon,
  loading = false,
  iconClassName,
}: {
  label: string;
  value?: number | string;
  icon: LucideIcon;
  loading?: boolean;
  iconClassName?: string;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3">
          <div className="truncate text-xs text-[var(--yak-components-muted-text)]">{label}</div>
          <Icon
            size={18}
            aria-hidden="true"
            className={iconClassName ?? "text-[var(--yak-components-muted-text)]"}
          />
        </div>
        <div className="mt-3 min-h-9 text-3xl font-semibold tabular-nums">
          {loading ? (
            <Skeleton width={72} height={30} />
          ) : typeof value === "number" ? (
            number(value)
          ) : (
            value ?? "—"
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export function OverviewMetrics({
  workflow,
  task,
  workflowLoading,
  taskLoading,
}: {
  workflow?: WorkflowInstanceCount;
  task?: TaskInstanceCount;
  workflowLoading: boolean;
  taskLoading: boolean;
}) {
  const workflowRecords = workflow?.workflowInstanceStatusCounts ?? [];
  const taskRecords = task?.taskInstanceStatusCounts ?? [];

  const failedWorkflows = workflow ? countStatus(workflowRecords, "FAILURE") : undefined;
  const runningWorkflows = workflow
    ? countStatus(workflowRecords, "RUNNING_EXECUTION")
    : undefined;
  const failedTasks = task ? countStatus(taskRecords, "FAILURE") : undefined;
  const successRate = workflow ? workflowSuccessRate(workflowRecords) : undefined;

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
      <MetricCard
        label="工作流实例"
        value={workflow?.totalCount}
        icon={Activity}
        loading={workflowLoading}
      />
      <MetricCard
        label="工作流成功率"
        value={formatPercent(successRate)}
        icon={CheckCircle2}
        loading={workflowLoading}
        iconClassName="text-[#16a34a]"
      />
      <MetricCard
        label="失败工作流"
        value={failedWorkflows}
        icon={XCircle}
        loading={workflowLoading}
        iconClassName="text-[#ef4444]"
      />
      <MetricCard
        label="运行中工作流"
        value={runningWorkflows}
        icon={LoaderCircle}
        loading={workflowLoading}
        iconClassName="text-[#2563eb]"
      />
      <MetricCard
        label="任务实例"
        value={task?.totalCount}
        icon={ListChecks}
        loading={taskLoading}
      />
      <MetricCard
        label="失败任务"
        value={failedTasks}
        icon={CircleAlert}
        loading={taskLoading}
        iconClassName="text-[#ef4444]"
      />
    </div>
  );
}
