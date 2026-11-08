import {
  AlertTriangle,
  ArrowLeftRight,
  CheckCircle2,
  Database,
  RefreshCw,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  Empty,
  PageHeader,
  Skeleton,
} from "@yak-ops/yak-ui";

import type {
  DataIntegrationOverview,
  DataIntegrationOverviewScope,
} from "@/service/data-sync/overview";
import { useDataIntegrationOverview } from "./use-overview";

const SCOPE_LABELS: Record<DataIntegrationOverviewScope["scope"], string> = {
  DATABASE: "整库同步",
  MULTI_TABLE: "多表同步",
  SINGLE_TABLE: "单表同步",
};

const number = (value: number) => value.toLocaleString("zh-CN");

function OverviewSkeleton() {
  return (
    <div role="status" aria-label="正在加载概览" className="space-y-4">
      <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1 xl:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <Card key={index}>
            <CardContent className="space-y-4 p-5">
              <Skeleton width={96} height={14} />
              <Skeleton width={75} height={30} />
            </CardContent>
          </Card>
        ))}
      </div>
      <Card>
        <CardHeader title="任务状态" />
        <CardContent className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8">
          {Array.from({ length: 8 }, (_, index) => (
            <div key={index} className="space-y-3 rounded border border-[var(--yak-components-panel-border)] p-3">
              <Skeleton width="72%" height={12} />
              <Skeleton width={36} height={24} />
            </div>
          ))}
        </CardContent>
      </Card>
      <div className="grid grid-cols-2 gap-4 max-lg:grid-cols-1">
        {Array.from({ length: 2 }, (_, index) => (
          <Card key={index}>
            <CardHeader title={index === 0 ? "同步任务分布" : "最近失败任务"} />
            <CardContent className="space-y-4">
              {Array.from({ length: 3 }, (_, row) => (
                <Skeleton key={row} width="100%" height={32} />
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function MetricCard({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: number;
  icon: LucideIcon;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-2 text-xs text-[var(--yak-components-muted-text)]">
          <span>{label}</span>
          <Icon aria-hidden="true" size={18} />
        </div>
        <div className="mt-3 text-3xl font-semibold tabular-nums">{number(value)}</div>
      </CardContent>
    </Card>
  );
}

function StatusOverview({ overview }: { overview: DataIntegrationOverview }) {
  const statuses = [
    { label: "已上线", value: overview.onlineCount },
    { label: "已下线", value: overview.offlineCount },
    { label: "排队中", value: overview.queuedCount },
    { label: "运行中", value: overview.runningCount },
    { label: "已完成", value: overview.succeededCount },
    { label: "运行失败", value: overview.failedCount },
    { label: "已停止", value: overview.stoppedCount },
    { label: "已暂停", value: overview.pausedCount },
  ];

  return (
    <Card>
      <CardHeader title="任务状态" description="上线状态来自任务定义；运行状态来自每个任务最近一次工作流实例" />
      <CardContent className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8">
        {statuses.map((status) => (
          <div key={status.label} className="rounded border border-[var(--yak-components-panel-border)] p-3">
            <div className="text-xs text-[var(--yak-components-muted-text)]">{status.label}</div>
            <div className="mt-2 text-2xl font-semibold tabular-nums">{number(status.value)}</div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function TaskDistribution({ overview }: { overview: DataIntegrationOverview }) {
  return (
    <Card>
      <CardHeader title="同步任务分布" description="按当前任务配置统计，不按执行次数重复计数" />
      <CardContent className="overflow-x-auto p-0">
        <table className="w-full min-w-[420px] text-sm">
          <thead className="bg-[var(--yak-color-hover)] text-xs text-[var(--yak-components-muted-text)]">
            <tr>
              <th scope="col" className="px-4 py-3 text-left font-medium">任务类型</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">离线</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">实时</th>
              <th scope="col" className="px-4 py-3 text-right font-medium">总数</th>
            </tr>
          </thead>
          <tbody>
            {overview.taskTypes.map((item) => (
              <tr key={item.scope} className="border-t border-[var(--yak-components-panel-border)]">
                <th scope="row" className="px-4 py-3 text-left font-normal">
                  {SCOPE_LABELS[item.scope]}
                </th>
                <td className="px-4 py-3 text-right tabular-nums">{number(item.offline)}</td>
                <td className="px-4 py-3 text-right tabular-nums">{number(item.realtime)}</td>
                <td className="px-4 py-3 text-right font-medium tabular-nums">{number(item.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {overview.unreadableCount > 0 ? (
          <p className="border-t border-[var(--yak-components-panel-border)] px-4 py-3 text-xs text-[var(--yak-components-muted-text)]">
            {number(overview.unreadableCount)} 个任务配置无法解析，未计入类型分布。
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function RecentFailures({ overview }: { overview: DataIntegrationOverview }) {
  return (
    <Card>
      <CardHeader title="最近失败任务" description="最近 5 条失败执行记录" />
      <CardContent className="p-0">
        {overview.recentFailures.length === 0 ? (
          <Empty title="暂无失败记录" className="min-h-52" />
        ) : (
          <ul className="divide-y divide-[var(--yak-components-panel-border)]">
            {overview.recentFailures.map((item) => (
              <li key={item.instanceId} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <Link
                    className="block truncate text-sm font-medium text-[var(--yak-color-primary)] hover:underline"
                    to={`/data-sync/${encodeURIComponent(item.taskId)}/detail?instanceId=${encodeURIComponent(item.instanceId)}`}
                  >
                    {item.taskName || item.taskId}
                  </Link>
                  <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                    {item.failedAt ? new Date(item.failedAt).toLocaleString("zh-CN") : "时间未知"}
                  </div>
                </div>
                <span className="shrink-0 text-xs text-[var(--yak-components-danger-text)]">失败</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

export function DataIntegrationOverviewPage() {
  const query = useDataIntegrationOverview();
  const overview = query.data;

  return (
    <div className="min-h-full bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title="概览"
        bordered
        className="bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
        extra={
          <Button
            size="sm"
            variant="secondary"
            isDisabled={query.isFetching}
            onClick={() => void query.refetch()}
          >
            <RefreshCw size={14} aria-hidden="true" className={query.isFetching ? "animate-spin" : ""} />
            刷新
          </Button>
        }
      />
      <div className="space-y-4 px-6 pb-8 pt-5 max-md:px-4">
        {query.isError ? (
          <Alert>
            {overview ? "概览刷新失败，当前显示上一次统计结果。" : "概览加载失败。"}
            <Button size="sm" variant="secondary" onClick={() => void query.refetch()}>
              重试
            </Button>
          </Alert>
        ) : null}
        {overview ? (
          <>
            <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1 xl:grid-cols-4">
              <MetricCard label="数据源总数" value={overview.dataSourceCount} icon={Database} />
              <MetricCard label="同步任务总数" value={overview.taskCount} icon={ArrowLeftRight} />
              <MetricCard label="运行中" value={overview.runningCount} icon={CheckCircle2} />
              <MetricCard label="异常任务" value={overview.failedCount} icon={AlertTriangle} />
            </div>
            <StatusOverview overview={overview} />
            <div className="grid grid-cols-2 items-start gap-4 max-lg:grid-cols-1">
              <TaskDistribution overview={overview} />
              <RecentFailures overview={overview} />
            </div>
          </>
        ) : query.isError ? null : (
          <OverviewSkeleton />
        )}
      </div>
    </div>
  );
}

export default DataIntegrationOverviewPage;
