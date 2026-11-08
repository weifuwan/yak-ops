import {
  AlertTriangle,
  ArrowLeftRight,
  CheckCircle2,
  Database,
  RefreshCw,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import {
  Alert,
  Button,
  Card,
  CardContent,
  PageHeader,
  Skeleton,
} from "@yak-ops/yak-ui";

import { ExecutionMetricsSection } from "./execution-metrics-section";
import { useDataIntegrationOverview } from "./use-overview";

const number = (value: number) => value.toLocaleString("zh-CN");

function OverviewSkeleton() {
  return (
    <div role="status" aria-label="正在加载概览" className="grid grid-cols-2 gap-4 max-md:grid-cols-1 xl:grid-cols-4">
      {Array.from({ length: 4 }, (_, index) => (
        <Card key={index}>
          <CardContent className="space-y-4 p-5">
            <Skeleton width={96} height={14} />
            <Skeleton width={75} height={30} />
          </CardContent>
        </Card>
      ))}
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
          <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1 xl:grid-cols-4">
            <MetricCard label="数据源总数" value={overview.dataSourceCount} icon={Database} />
            <MetricCard label="同步任务总数" value={overview.taskCount} icon={ArrowLeftRight} />
            <MetricCard label="运行中" value={overview.runningCount} icon={CheckCircle2} />
            <MetricCard label="异常任务" value={overview.failedCount} icon={AlertTriangle} />
          </div>
        ) : query.isError ? null : (
          <OverviewSkeleton />
        )}
        <ExecutionMetricsSection />
      </div>
    </div>
  );
}

export default DataIntegrationOverviewPage;
