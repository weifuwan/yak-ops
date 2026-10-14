import {
  Card,
  CardContent,
  CardHeader,
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
import type { EChartsCoreOption } from "echarts/core";
import { ArrowUpRight, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import type { DataSyncType } from "@/service/data-sync";
import {
  getDataSyncOperations,
  type DataSyncOperationsRange,
  type DataSyncOperationsResult,
} from "@/service/data-sync/operations";

import { EChart } from "./EChart";

const RANGE_ITEMS: Record<DataSyncOperationsRange, string> = {
  TODAY: "今天",
  LAST_7_DAYS: "最近 7 天",
  LAST_30_DAYS: "最近 30 天",
};

const numberText = (value: number) => value.toLocaleString("zh-CN");

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="text-xs text-[#667085]">{label}</div>
        <div className="mt-3 text-2xl font-semibold text-[#242731]">{numberText(value)}</div>
      </CardContent>
    </Card>
  );
}

interface Props {
  syncType: DataSyncType;
}

export function DataSyncOperationsDashboard({ syncType }: Props) {
  const realtime = syncType === "REALTIME";
  const [range, setRange] = useState<DataSyncOperationsRange>(realtime ? "TODAY" : "LAST_7_DAYS");
  const [dashboard, setDashboard] = useState<DataSyncOperationsResult>();
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let mounted = true;
    let loadingRequest = false;

    setLoading(true);
    setDashboard(undefined);
    setFailed(false);

    const refresh = async () => {
      if (loadingRequest) return;
      loadingRequest = true;
      try {
        const result = await getDataSyncOperations(syncType, range);
        if (!mounted) return;
        setDashboard(result);
        setFailed(false);
      } catch {
        if (mounted) setFailed(true);
      } finally {
        loadingRequest = false;
        if (mounted) setLoading(false);
      }
    };

    void refresh();
    const timer = window.setInterval(() => void refresh(), 30000);
    return () => {
      mounted = false;
      window.clearInterval(timer);
    };
  }, [range, syncType]);

  const summary = dashboard?.summary;
  const trend = dashboard?.trend || [];
  const hasExecutions = Boolean(summary && summary.executionCount > 0);
  const pendingCount = summary
    ? Math.max(
        0,
        summary.executionCount -
          summary.succeededCount -
          summary.failedCount -
          summary.canceledCount,
      )
    : 0;
  const statuses = summary
    ? [
        { name: "成功", value: summary.succeededCount },
        { name: "失败", value: summary.failedCount },
        { name: realtime ? "已停止" : "已取消", value: summary.canceledCount },
        { name: "处理中", value: pendingCount },
      ].filter((item) => item.value > 0)
    : [];

  const trendOption: EChartsCoreOption = {
    tooltip: { trigger: "axis" },
    legend: { bottom: 0 },
    grid: { top: 20, left: 12, right: 14, bottom: 48, containLabel: true },
    xAxis: {
      type: "category",
      data: trend.map((item) =>
        range === "TODAY" ? item.bucket.slice(11, 16) : item.bucket.slice(5),
      ),
      axisTick: { show: false },
      axisLabel: { color: "#98a2b3" },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: "#98a2b3" },
      splitLine: { lineStyle: { color: "#f0f1f3" } },
    },
    series: realtime
      ? [
          {
            name: "启动次数",
            type: "line",
            smooth: true,
            data: trend.map((item) => item.executionCount),
          },
          {
            name: "失败",
            type: "bar",
            data: trend.map((item) => item.failedCount),
          },
        ]
      : [
          {
            name: "成功",
            type: "bar",
            stack: "outcome",
            data: trend.map((item) => item.succeededCount),
          },
          {
            name: "失败",
            type: "bar",
            stack: "outcome",
            data: trend.map((item) => item.failedCount),
          },
          {
            name: "取消",
            type: "bar",
            stack: "outcome",
            data: trend.map((item) => item.canceledCount),
          },
        ],
  };

  const distributionOption: EChartsCoreOption = {
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [
      {
        name: "状态分布",
        type: "pie",
        radius: ["48%", "70%"],
        center: ["50%", "43%"],
        label: { show: false },
        data: statuses,
      },
    ],
  };

  const basePath = "/data-sync";

  return (
    <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={realtime ? "实时同步运维" : "离线同步运维"}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Select<DataSyncOperationsRange>
            size="small"
            items={RANGE_ITEMS}
            value={range}
            onValueChange={(value) => {
              if (value) setRange(value);
            }}
          >
            <SelectTrigger variant="outlined" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(RANGE_ITEMS).map(([value, label]) => (
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
        {loading && !dashboard ? (
          <div className="flex min-h-72 items-center justify-center">
            <Spinner size="large" label="加载运行统计" />
          </div>
        ) : failed && !dashboard ? (
          <Card>
            <CardContent>
              <Empty title="统计加载失败" />
            </CardContent>
          </Card>
        ) : summary ? (
          <>
            <div className="grid grid-cols-4 gap-4 max-xl:grid-cols-2 max-md:grid-cols-1">
              {realtime ? (
                <>
                  <SummaryCard label="启动次数" value={summary.executionCount} />
                  <SummaryCard label="正在运行" value={summary.runningCount} />
                  <SummaryCard label="失败次数" value={summary.failedCount} />
                  <SummaryCard label="已停止" value={summary.canceledCount} />
                </>
              ) : (
                <>
                  <SummaryCard label="执行总数" value={summary.executionCount} />
                  <SummaryCard label="成功次数" value={summary.succeededCount} />
                  <SummaryCard label="失败次数" value={summary.failedCount} />
                  <SummaryCard label="正在运行" value={summary.runningCount} />
                </>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4 max-lg:grid-cols-1">
              <Card>
                <CardHeader
                  title={realtime ? "启动与失败趋势" : "执行结果趋势"}
                  extra={loading ? <RefreshCw size={14} className="animate-spin" /> : null}
                />
                <CardContent>
                  {hasExecutions ? (
                    <EChart option={trendOption} ariaLabel="执行结果趋势" />
                  ) : (
                    <Empty title="暂无执行数据" className="min-h-64" />
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader title="运行状态分布" />
                <CardContent>
                  {hasExecutions ? (
                    <EChart option={distributionOption} ariaLabel="运行状态分布" />
                  ) : (
                    <Empty title="暂无执行数据" className="min-h-64" />
                  )}
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader title="最近失败任务" />
              <CardContent className="p-0">
                {dashboard.recentFailures.length === 0 ? (
                  <Empty title="暂无失败任务" />
                ) : (
                  <div className="divide-y divide-[#f0f1f3]">
                    {dashboard.recentFailures.map((failure) => (
                      <Link
                        key={failure.instanceId}
                        to={`${basePath}/${encodeURIComponent(failure.definitionId)}/detail?executionId=${encodeURIComponent(failure.instanceId)}`}
                        className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-[#fafafa]"
                      >
                        <div className="min-w-0">
                          <div className="truncate text-sm font-medium text-[#344054]">
                            {failure.taskName || "历史任务"}
                          </div>
                          <div className="mt-1 truncate text-xs text-[#98a2b3]">
                            {failure.createTime.replace("T", " ")} ·{" "}
                            {failure.failureCode || "FAILED"}
                          </div>
                        </div>
                        <ArrowUpRight size={16} className="shrink-0 text-[#98a2b3]" />
                      </Link>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </>
        ) : null}
      </div>
    </div>
  );
}
