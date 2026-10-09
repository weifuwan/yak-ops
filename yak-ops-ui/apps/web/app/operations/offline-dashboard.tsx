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
import { useCallback, useEffect, useMemo, useState } from "react";
import type { EChartsCoreOption } from "echarts/core";

import {
  getDataSyncOperationsDashboard,
  type DataSyncInstanceStatus,
  type DataSyncOperationsDashboard,
  type DataSyncOperationsRange,
  type DataSyncOperationsSummary,
} from "@/service/data-sync";

import { EChart } from "./EChart";

const RANGE_ITEMS: Record<DataSyncOperationsRange, string> = {
  TODAY: "今日",
  LAST_7_DAYS: "近 7 天",
  LAST_30_DAYS: "近 30 天",
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: "等待",
  RUNNING: "运行中",
  RETRY_WAITING: "等待重试",
  SUCCEEDED: "成功",
  FAILED: "失败",
  CANCELED: "已取消",
  LOST: "已丢失",
};

const numberFormatter = new Intl.NumberFormat("zh-CN", {
  maximumFractionDigits: 1,
  notation: "compact",
});

const integerFormatter = new Intl.NumberFormat("zh-CN");

const formatNumber = (value?: number) => numberFormatter.format(value || 0);

const formatInteger = (value?: number) => integerFormatter.format(value || 0);

const formatDuration = (millis?: number) => {
  const value = Math.max(0, millis || 0);
  if (value < 1000) return `${value}ms`;
  const seconds = Math.round(value / 100) / 10;
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = Math.round(seconds % 60);
  return `${minutes}m ${remainingSeconds}s`;
};

const formatBucket = (value: string, range: DataSyncOperationsRange) => {
  const normalized = value.replace("T", " ");
  return range === "TODAY" ? normalized.slice(11, 16) : normalized.slice(5, 10);
};

const statusLabel = (status: DataSyncInstanceStatus) => STATUS_LABELS[status] || status;

interface SummaryCardProps {
  label: string;
  value: string;
  description: string;
}

function SummaryCard({ description, label, value }: SummaryCardProps) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="text-xs text-[#98a2b3]">{label}</div>
        <div className="mt-2 text-2xl font-semibold tracking-tight text-[#252832]">{value}</div>
        <div className="mt-2 text-xs leading-5 text-[#667085]">{description}</div>
      </CardContent>
    </Card>
  );
}

const chartTextStyle = {
  color: "#667085",
  fontFamily: "inherit",
  fontSize: 11,
};

const chartAxisLine = {
  lineStyle: {
    color: "#e7e9ed",
  },
};

const chartSplitLine = {
  lineStyle: {
    color: "#f0f1f3",
  },
};

export function OfflineOperationsDashboard() {
  const [range, setRange] = useState<DataSyncOperationsRange>("LAST_7_DAYS");
  const [dashboard, setDashboard] = useState<DataSyncOperationsDashboard>();
  const [loading, setLoading] = useState(true);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    try {
      setDashboard(
        await getDataSyncOperationsDashboard({
          syncType: "OFFLINE",
          range,
        }),
      );
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const summary: DataSyncOperationsSummary = dashboard?.summary || {
    executionCount: 0,
    succeededCount: 0,
    failedCount: 0,
    lostCount: 0,
    abnormalTaskCount: 0,
    currentActiveTaskCount: 0,
    autoRecoveryCount: 0,
    readRows: 0,
    writeRows: 0,
    averageDurationMillis: 0,
  };

  const successRate =
    summary.executionCount > 0
      ? Math.round((summary.succeededCount / summary.executionCount) * 1000) / 10
      : 0;
  const hasTrendData = summary.executionCount > 0;
  const hasStatusData = (dashboard?.statusDistribution || []).some((item) => item.count > 0);
  const hasFailureData = (dashboard?.failureRanking || []).length > 0;

  const volumeTrendOption = useMemo<EChartsCoreOption>(() => {
    const trend = dashboard?.trend || [];
    return {
      animationDuration: 300,
      tooltip: { trigger: "axis" },
      legend: {
        top: 0,
        right: 0,
        textStyle: chartTextStyle,
      },
      grid: {
        left: 8,
        right: 8,
        top: 42,
        bottom: 4,
        containLabel: true,
      },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: trend.map((item) => formatBucket(item.bucketStart, range)),
        axisLine: chartAxisLine,
        axisTick: { show: false },
        axisLabel: chartTextStyle,
      },
      yAxis: {
        type: "value",
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          ...chartTextStyle,
          formatter: (value: number) => formatNumber(value),
        },
        splitLine: chartSplitLine,
      },
      series: [
        {
          name: "读取",
          type: "line",
          smooth: true,
          showSymbol: false,
          data: trend.map((item) => item.readRows),
          lineStyle: { width: 2 },
          areaStyle: { opacity: 0.04 },
          color: "#667085",
        },
        {
          name: "写入",
          type: "line",
          smooth: true,
          showSymbol: false,
          data: trend.map((item) => item.writeRows),
          lineStyle: { width: 2 },
          areaStyle: { opacity: 0.06 },
          color: "#0033ff",
        },
      ],
    };
  }, [dashboard?.trend, range]);

  const executionTrendOption = useMemo<EChartsCoreOption>(() => {
    const trend = dashboard?.trend || [];
    return {
      animationDuration: 300,
      tooltip: { trigger: "axis" },
      legend: {
        top: 0,
        right: 0,
        textStyle: chartTextStyle,
      },
      grid: {
        left: 8,
        right: 8,
        top: 42,
        bottom: 4,
        containLabel: true,
      },
      xAxis: {
        type: "category",
        data: trend.map((item) => formatBucket(item.bucketStart, range)),
        axisLine: chartAxisLine,
        axisTick: { show: false },
        axisLabel: chartTextStyle,
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: chartTextStyle,
        splitLine: chartSplitLine,
      },
      series: [
        {
          name: "成功",
          type: "bar",
          stack: "result",
          barMaxWidth: 24,
          data: trend.map((item) => item.succeededCount),
          color: "#12b76a",
        },
        {
          name: "失败",
          type: "bar",
          stack: "result",
          barMaxWidth: 24,
          data: trend.map((item) => item.failedCount),
          color: "#f04438",
        },
        {
          name: "丢失",
          type: "bar",
          stack: "result",
          barMaxWidth: 24,
          data: trend.map((item) => item.lostCount),
          color: "#f79009",
        },
      ],
    };
  }, [dashboard?.trend, range]);

  const statusDistributionOption = useMemo<EChartsCoreOption>(() => {
    const data = (dashboard?.statusDistribution || [])
      .filter((item) => item.count > 0)
      .map((item) => ({
        name: statusLabel(item.status),
        value: item.count,
      }));

    return {
      animationDuration: 300,
      tooltip: { trigger: "item" },
      legend: {
        bottom: 0,
        left: "center",
        textStyle: chartTextStyle,
      },
      series: [
        {
          name: "执行状态",
          type: "pie",
          radius: ["52%", "72%"],
          center: ["50%", "43%"],
          avoidLabelOverlap: true,
          label: {
            color: "#667085",
            formatter: "{b} {c}",
          },
          itemStyle: {
            borderColor: "#ffffff",
            borderWidth: 2,
          },
          data,
          color: ["#12b76a", "#0033ff", "#98a2b3", "#f79009", "#f04438", "#667085", "#7f56d9"],
        },
      ],
    };
  }, [dashboard?.statusDistribution]);

  const failureRankingOption = useMemo<EChartsCoreOption>(() => {
    const rows = [...(dashboard?.failureRanking || [])].reverse();
    return {
      animationDuration: 300,
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      grid: {
        left: 8,
        right: 12,
        top: 8,
        bottom: 4,
        containLabel: true,
      },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: chartTextStyle,
        splitLine: chartSplitLine,
      },
      yAxis: {
        type: "category",
        data: rows.map((item) => item.taskName || item.taskId),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          ...chartTextStyle,
          width: 120,
          overflow: "truncate",
        },
      },
      series: [
        {
          name: "异常次数",
          type: "bar",
          barMaxWidth: 18,
          data: rows.map((item) => item.abnormalCount),
          color: "#f04438",
          itemStyle: { borderRadius: [0, 4, 4, 0] },
        },
      ],
    };
  }, [dashboard?.failureRanking]);

  return (
    <div className="flex min-h-full flex-col bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title="离线同步"
        description="查看离线同步整体执行趋势与运行质量"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Select<DataSyncOperationsRange>
            value={range}
            items={RANGE_ITEMS}
            onValueChange={(value) => {
              if (value) setRange(value);
            }}
            size="small"
          >
            <SelectTrigger className="w-28">
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

      <div className="space-y-4 px-6 pb-6 pt-5 max-md:px-4">
        <div className="grid grid-cols-4 gap-4 max-xl:grid-cols-2 max-md:grid-cols-1">
          <SummaryCard
            label="执行次数"
            value={formatInteger(summary.executionCount)}
            description={`成功率 ${successRate}% · 当前活跃任务 ${formatInteger(summary.currentActiveTaskCount)}`}
          />
          <SummaryCard
            label="成功次数"
            value={formatInteger(summary.succeededCount)}
            description={`失败 ${formatInteger(summary.failedCount)} · 丢失 ${formatInteger(summary.lostCount)}`}
          />
          <SummaryCard
            label="写入数据量"
            value={formatNumber(summary.writeRows)}
            description={`读取 ${formatNumber(summary.readRows)} · Execution 当前/最终 Attempt 镜像`}
          />
          <SummaryCard
            label="平均耗时"
            value={formatDuration(summary.averageDurationMillis)}
            description={`异常任务 ${formatInteger(summary.abnormalTaskCount)} · 仅统计已完成 Execution`}
          />
        </div>

        {loading && !dashboard ? (
          <Card>
            <CardContent className="flex min-h-72 items-center justify-center">
              <Spinner size="xlarge" label="加载运维指标" />
            </CardContent>
          </Card>
        ) : (
          <div className="grid grid-cols-2 gap-4 max-xl:grid-cols-1">
            <Card>
              <CardHeader
                title="同步数据量趋势"
                description="读取 / 写入计数来自 Execution 当前或最终 Attempt 镜像"
              />
              <CardContent className="p-4">
                {hasTrendData ? (
                  <EChart
                    option={volumeTrendOption}
                    loading={loading}
                    ariaLabel="离线同步读取与写入数据量趋势"
                  />
                ) : (
                  <Empty
                    title="暂无同步数据"
                    description="当前时间范围内还没有离线同步 Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader
                title="执行结果趋势"
                description="按 Execution 创建时间聚合成功、失败与丢失状态"
              />
              <CardContent className="p-4">
                {hasTrendData ? (
                  <EChart
                    option={executionTrendOption}
                    loading={loading}
                    ariaLabel="离线同步执行结果趋势"
                  />
                ) : (
                  <Empty
                    title="暂无执行数据"
                    description="当前时间范围内还没有离线同步 Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader
                title="执行状态分布"
                description="展示当前时间范围内 Execution 的状态分布"
              />
              <CardContent className="p-4">
                {hasStatusData ? (
                  <EChart
                    option={statusDistributionOption}
                    loading={loading}
                    ariaLabel="离线同步执行状态分布"
                  />
                ) : (
                  <Empty
                    title="暂无状态数据"
                    description="当前时间范围内还没有离线同步 Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader
                title="失败任务 TOP 5"
                description="按 FAILED + LOST Execution 数量排序"
              />
              <CardContent className="p-4">
                {hasFailureData ? (
                  <EChart
                    option={failureRankingOption}
                    loading={loading}
                    ariaLabel="离线同步失败任务排名"
                  />
                ) : (
                  <Empty
                    title="暂无异常任务"
                    description="当前时间范围内没有 FAILED / LOST Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </div>
  );
}
