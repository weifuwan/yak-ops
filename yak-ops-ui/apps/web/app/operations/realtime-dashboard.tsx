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
import { useCallback, useEffect, useMemo, useState } from "react";

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
  CANCELED: "已停止",
  LOST: "已丢失",
};

const integerFormatter = new Intl.NumberFormat("zh-CN");

const formatInteger = (value?: number) => integerFormatter.format(value || 0);

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

export function RealtimeOperationsDashboard() {
  const [range, setRange] = useState<DataSyncOperationsRange>("TODAY");
  const [dashboard, setDashboard] = useState<DataSyncOperationsDashboard>();
  const [loading, setLoading] = useState(true);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    try {
      setDashboard(
        await getDataSyncOperationsDashboard({
          syncType: "REALTIME",
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

  const abnormalExecutionCount = summary.failedCount + summary.lostCount;
  const hasExecutionData = summary.executionCount > 0;
  const hasStatusData = (dashboard?.statusDistribution || []).some((item) => item.count > 0);
  const hasRecoveryData = summary.autoRecoveryCount > 0;
  const hasFailureData = (dashboard?.failureRanking || []).length > 0;

  const executionTrendOption = useMemo<EChartsCoreOption>(() => {
    const trend = dashboard?.trend || [];
    return {
      animationDuration: 300,
      tooltip: { trigger: "axis" },
      grid: {
        left: 8,
        right: 8,
        top: 16,
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
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: chartTextStyle,
        splitLine: chartSplitLine,
      },
      series: [
        {
          name: "Execution",
          type: "line",
          smooth: true,
          showSymbol: false,
          data: trend.map((item) => item.executionCount),
          lineStyle: { width: 2 },
          areaStyle: { opacity: 0.06 },
          color: "#0033ff",
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
          name: "运行状态",
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
          color: ["#0033ff", "#12b76a", "#f79009", "#98a2b3", "#f04438", "#667085", "#7f56d9"],
        },
      ],
    };
  }, [dashboard?.statusDistribution]);

  const autoRecoveryTrendOption = useMemo<EChartsCoreOption>(() => {
    const trend = dashboard?.trend || [];
    return {
      animationDuration: 300,
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      grid: {
        left: 8,
        right: 8,
        top: 16,
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
          name: "自动恢复",
          type: "bar",
          barMaxWidth: 24,
          data: trend.map((item) => item.autoRecoveryCount),
          color: "#7f56d9",
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
      ],
    };
  }, [dashboard?.trend, range]);

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
        title="实时同步"
        description="查看实时同步整体运行状态、启动与自动恢复趋势"
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
            label="当前活跃任务"
            value={formatInteger(summary.currentActiveTaskCount)}
            description="PENDING / RUNNING / RETRY_WAITING 的去重 Task"
          />
          <SummaryCard
            label="异常任务"
            value={formatInteger(summary.abnormalTaskCount)}
            description="当前时间范围内出现 FAILED / LOST 的去重 Task"
          />
          <SummaryCard
            label="自动恢复"
            value={formatInteger(summary.autoRecoveryCount)}
            description={`当前时间范围内共创建 ${formatInteger(summary.executionCount)} 个 Execution`}
          />
          <SummaryCard
            label="异常 Execution"
            value={formatInteger(abnormalExecutionCount)}
            description={`失败 ${formatInteger(summary.failedCount)} · 丢失 ${formatInteger(summary.lostCount)}`}
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
                title="Execution 启动趋势"
                description="按 Execution 创建时间观察实时同步启动数量"
              />
              <CardContent className="p-4">
                {hasExecutionData ? (
                  <EChart
                    option={executionTrendOption}
                    loading={loading}
                    ariaLabel="实时同步 Execution 启动趋势"
                  />
                ) : (
                  <Empty
                    title="暂无启动记录"
                    description="当前时间范围内还没有实时同步 Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader
                title="运行状态分布"
                description="展示当前时间范围内 Execution 的状态分布"
              />
              <CardContent className="p-4">
                {hasStatusData ? (
                  <EChart
                    option={statusDistributionOption}
                    loading={loading}
                    ariaLabel="实时同步运行状态分布"
                  />
                ) : (
                  <Empty
                    title="暂无状态数据"
                    description="当前时间范围内还没有实时同步 Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader
                title="自动恢复趋势"
                description="只统计 triggerType = AUTO_RECOVERY 的 Execution"
              />
              <CardContent className="p-4">
                {hasRecoveryData ? (
                  <EChart
                    option={autoRecoveryTrendOption}
                    loading={loading}
                    ariaLabel="实时同步自动恢复趋势"
                  />
                ) : (
                  <Empty
                    title="暂无自动恢复"
                    description="当前时间范围内没有 AUTO_RECOVERY Execution"
                    className="min-h-72"
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader
                title="异常任务 TOP 5"
                description="按 FAILED + LOST Execution 数量排序"
              />
              <CardContent className="p-4">
                {hasFailureData ? (
                  <EChart
                    option={failureRankingOption}
                    loading={loading}
                    ariaLabel="实时同步异常任务排名"
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
