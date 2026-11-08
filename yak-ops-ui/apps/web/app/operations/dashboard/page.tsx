import {
  Button,
  Card,
  CardContent,
  Empty,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
} from "@yak-ops/yak-ui";
import { useSearchParams } from "react-router-dom";
import type { DataSyncType } from "@/service/data-sync";
import type { DataSyncOperationsRange } from "@/service/data-sync/operations";
import { OperationsCharts } from "./dashboard-charts";
import { OPERATIONS_RANGES, resolveOperationsRange } from "./presentation";
import { OperationsRecentFailures } from "./recent-failures";
import { OperationsSummaryCards } from "./summary-cards";
import { useOperationsDashboard } from "./use-dashboard";

export function OperationsDashboardPage({ syncType }: { syncType: DataSyncType }) {
  const [params, setParams] = useSearchParams();
  const range = resolveOperationsRange(params.get("range"), syncType);
  const query = useOperationsDashboard(syncType, range);
  const snapshot = query.data;

  const selectRange = (value: DataSyncOperationsRange) => {
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.set("range", value);
        return next;
      },
      { replace: true },
    );
  };

  return (
    <div className="min-h-full bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title={syncType === "REALTIME" ? "实时同步运维" : "离线同步运维"}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Select<DataSyncOperationsRange>
            size="small"
            items={OPERATIONS_RANGES}
            value={range}
            onValueChange={(value) => {
              if (value) selectRange(value);
            }}
          >
            <SelectTrigger variant="outlined" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(OPERATIONS_RANGES).map(([value, label]) => (
                <SelectItem key={value} value={value as DataSyncOperationsRange}>
                  <SelectItemText>{label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
      />
      <div className="space-y-4 px-6 pb-8 pt-5 max-md:px-4">
        {query.isError ? (
          <div
            role="alert"
            className="flex items-center gap-3 text-sm text-[var(--yak-color-danger)]"
          >
            {snapshot ? "统计刷新失败" : "统计加载失败"}
            <Button variant="secondary" size="sm" onClick={() => void query.refetch()} isDisabled={query.isFetching}>
              重试
            </Button>
          </div>
        ) : null}
        {query.isPending && !query.isError ? (
          <div className="flex min-h-72 items-center justify-center">
            <Spinner size="large" label="加载运行统计" />
          </div>
        ) : snapshot ? (
          <>
            <OperationsSummaryCards summary={snapshot.summary} syncType={syncType} />
            <OperationsCharts
              summary={snapshot.summary}
              trend={snapshot.trend}
              range={range}
              syncType={syncType}
              refreshing={query.isFetching}
            />
            <OperationsRecentFailures failures={snapshot.recentFailures} />
          </>
        ) : (
          <Card>
            <CardContent>
              <Empty title="暂无运行统计" />
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}

export function OfflineTaskOperationsPage() {
  return <OperationsDashboardPage syncType="OFFLINE" />;
}

export function RealtimeTaskOperationsPage() {
  return <OperationsDashboardPage syncType="REALTIME" />;
}
