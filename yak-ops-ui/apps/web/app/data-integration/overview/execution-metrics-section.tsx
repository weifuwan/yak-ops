import { useMemo, useState } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { Download } from "lucide-react";
import {
  Button,
  DateField,
  DateI18nProvider,
  DateRangePicker,
  Empty,
  RangeCalendar,
  Segmented,
  Skeleton,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  parseDate,
} from "@yak-ops/yak-ui";

import type { DataSyncExecutionStats } from "@/service/data-sync/overview";
import { EChart } from "@/app/operations/dashboard/echart";
import {
  EXECUTION_METRICS,
  activePeriod,
  executionMetricValue,
  executionTrend,
  rangeForPeriod,
  sumExecutionCounts,
  validRange,
  type ExecutionMetricKey,
  type ExecutionPeriod,
  type ExecutionRange,
  type ExecutionTab,
} from "./metrics";
import { useDataSyncExecutionMetrics } from "./use-overview";

const PERIODS: Array<{ value: ExecutionPeriod; label: string }> = [
  { value: "yesterday", label: "昨天" },
  { value: "7d", label: "近7天" },
  { value: "30d", label: "近30天" },
];

const TABS: Array<{ value: ExecutionTab; label: string }> = [
  { value: "ALL", label: "全部" },
  { value: "OFFLINE", label: "离线" },
  { value: "REALTIME", label: "实时" },
];

const formatCount = (value: number) => value.toLocaleString("zh-CN");

function ExecutionDateRangePicker({
  range,
  onChange,
}: {
  range: ExecutionRange;
  onChange: (next: ExecutionRange) => void;
}) {
  const latest = rangeForPeriod("yesterday").endDate;
  const value = useMemo(() => ({
    start: parseDate(range.startDate),
    end: parseDate(range.endDate),
  }), [range.startDate, range.endDate]);
  const maxValue = useMemo(() => parseDate(latest), [latest]);

  return (
    <DateI18nProvider locale="zh-CN">
      <DateRangePicker
        aria-label="统计日期范围"
        value={value}
        maxValue={maxValue}
        onChange={(nextValue) => {
          if (!nextValue) return;
          const next = {
            startDate: nextValue.start.toString(),
            endDate: nextValue.end.toString(),
          };
          if (validRange(next)) onChange(next);
        }}
        className="w-[236px]"
      >
        <DateField.Group fullWidth>
          <DateField.Input slot="start" className="min-w-0 px-2">
            {(segment) => <DateField.Segment segment={segment} />}
          </DateField.Input>
          <DateRangePicker.RangeSeparator />
          <DateField.Input slot="end" className="min-w-0 px-2">
            {(segment) => <DateField.Segment segment={segment} />}
          </DateField.Input>
          <DateField.Suffix>
            <DateRangePicker.Trigger aria-label="打开日期范围选择器">
              <DateRangePicker.TriggerIndicator />
            </DateRangePicker.Trigger>
          </DateField.Suffix>
        </DateField.Group>

        <DateRangePicker.Popover className="w-auto">
          <RangeCalendar
            aria-label="统计日期范围"
            maxValue={maxValue}
            firstDayOfWeek="mon"
          >
            <RangeCalendar.Header>
              <RangeCalendar.NavButton slot="previous" aria-label="上个月" />
              <RangeCalendar.Heading />
              <RangeCalendar.NavButton slot="next" aria-label="下个月" />
            </RangeCalendar.Header>
            <RangeCalendar.Grid>
              <RangeCalendar.GridHeader>
                {(day) => <RangeCalendar.HeaderCell>{day}</RangeCalendar.HeaderCell>}
              </RangeCalendar.GridHeader>
              <RangeCalendar.GridBody>
                {(date) => <RangeCalendar.Cell date={date} />}
              </RangeCalendar.GridBody>
            </RangeCalendar.Grid>
          </RangeCalendar>
        </DateRangePicker.Popover>
      </DateRangePicker>
    </DateI18nProvider>
  );
}

function exportExecutionCsv(stats: DataSyncExecutionStats, tab: ExecutionTab) {
  const labels = ["日期", "执行次数", "成功次数", "失败次数", "停止次数", "运行中", "排队中", "已暂停", "成功率(%)"];
  const lines = executionTrend(stats, tab).map(({ date, counts }) => [
    date,
    counts.executionCount,
    counts.succeededCount,
    counts.failedCount,
    counts.stoppedCount,
    counts.runningCount,
    counts.queuedCount,
    counts.pausedCount,
    executionMetricValue(counts, "successRate"),
  ]);
  const csv = "\uFEFF" + [labels, ...lines]
    .map((values) => values.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(","))
    .join("\r\n");
  const link = document.createElement("a");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  link.href = url;
  link.download = `数据同步-${tab}-${stats.startDate}-${stats.endDate}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function MetricContent({
  stats,
  tab,
  metric,
  onMetricChange,
}: {
  stats: DataSyncExecutionStats;
  tab: ExecutionTab;
  metric: ExecutionMetricKey;
  onMetricChange: (value: ExecutionMetricKey) => void;
}) {
  const totals = useMemo(() => sumExecutionCounts(stats.days, tab), [stats.days, tab]);
  const points = useMemo(() => executionTrend(stats, tab), [stats, tab]);
  const label = EXECUTION_METRICS.find((item) => item.key === metric)?.label || "执行次数";
  const option: EChartsCoreOption = useMemo(() => ({
    grid: { left: 16, top: 24, right: 24, bottom: 24, containLabel: true },
    tooltip: { trigger: "axis" },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: points.map((item) => item.date.slice(5)),
      axisTick: { show: false },
      axisLabel: { color: "#98a2b3" },
    },
    yAxis: {
      type: "value",
      min: 0,
      max: metric === "successRate" ? 100 : undefined,
      minInterval: metric === "successRate" ? undefined : 1,
      axisLabel: { color: "#98a2b3", formatter: metric === "successRate" ? "{value}%" : "{value}" },
      splitLine: { lineStyle: { color: "#eff0f2" } },
    },
    series: [{
      name: label,
      type: "line",
      smooth: true,
      showSymbol: points.length <= 7,
      data: points.map((item) => executionMetricValue(item.counts, metric)),
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.06 },
    }],
  }), [label, metric, points]);

  return (
    <div className="pt-4">
      <div className="overflow-x-auto rounded-t-lg border border-[var(--yak-components-panel-border)]">
        <div
          className="grid min-w-[960px]"
          style={{ gridTemplateColumns: `repeat(${EXECUTION_METRICS.length}, minmax(120px, 1fr))` }}
        >
          {EXECUTION_METRICS.map((item, index) => (
            <button
              key={item.key}
              type="button"
              aria-pressed={metric === item.key}
              onClick={() => onMetricChange(item.key)}
              className={[
                "min-h-[92px] border-b-2 bg-[var(--yak-color-hover)] px-4 py-3 text-left transition-colors",
                index ? "border-l border-[var(--yak-components-panel-border)]" : "",
                metric === item.key
                  ? "border-b-[var(--yak-components-tab-active)] bg-[var(--yak-components-panel-bg)]"
                  : "border-b-transparent hover:bg-[var(--yak-components-panel-bg)]",
              ].join(" ")}
            >
              <div className="whitespace-nowrap text-xs font-medium text-[var(--yak-components-muted-text)]">
                {item.label}
              </div>
              <div className="mt-2 text-xl font-semibold tabular-nums">
                {formatCount(executionMetricValue(totals, item.key))}{item.key === "successRate" ? "%" : ""}
              </div>
            </button>
          ))}
        </div>
      </div>
      <div className="border-x border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
        {totals.executionCount ? (
          <EChart option={option} ariaLabel={`${label}趋势`} className="h-[300px] w-full" />
        ) : (
          <Empty title="暂无执行数据" className="min-h-[300px]" />
        )}
      </div>
    </div>
  );
}

function ExecutionMetricsSkeleton() {
  return (
    <div className="pt-4" role="status" aria-label="正在加载执行统计">
      <div className="overflow-x-auto rounded-t-lg border border-[var(--yak-components-panel-border)]">
        <div
          className="grid min-w-[960px]"
          style={{ gridTemplateColumns: `repeat(${EXECUTION_METRICS.length}, minmax(120px, 1fr))` }}
        >
          {EXECUTION_METRICS.map((item, index) => (
            <div
              key={item.key}
              className={[
                "min-h-[92px] border-b-2 border-b-transparent bg-[var(--yak-color-hover)] px-4 py-3",
                index ? "border-l border-[var(--yak-components-panel-border)]" : "",
              ].join(" ")}
            >
              <Skeleton width={58 + (index % 3) * 10} height={12} />
              <Skeleton width={34 + (index % 2) * 12} height={24} className="mt-2" />
            </div>
          ))}
        </div>
      </div>

      <div className="border-x border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
        <div className="relative h-[300px] w-full overflow-hidden" aria-hidden="true">
          <div className="absolute bottom-7 left-0 top-4 flex w-10 flex-col justify-between">
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} width={28} height={10} />
            ))}
          </div>

          <div className="absolute bottom-7 left-12 right-2 top-4 flex flex-col justify-between">
            {Array.from({ length: 6 }, (_, index) => (
              <div
                key={index}
                className="border-t border-[var(--yak-components-panel-border)]"
              />
            ))}
          </div>

          <div className="absolute bottom-1 left-12 right-2 flex justify-between">
            {Array.from({ length: 8 }, (_, index) => (
              <Skeleton key={index} width={28} height={10} />
            ))}
          </div>

          <div className="absolute bottom-[52px] left-12 right-2">
            <Skeleton height={2} />
          </div>
        </div>
      </div>
    </div>
  );
}

export function ExecutionMetricsSection() {
  const [range, setRange] = useState<ExecutionRange>(() => rangeForPeriod("30d"));
  const [tab, setTab] = useState<ExecutionTab>("ALL");
  const [metric, setMetric] = useState<ExecutionMetricKey>("executionCount");
  const query = useDataSyncExecutionMetrics(range);
  const selectedPreset = activePeriod(range);

  return (
    <section className="rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-5 pb-5 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h2 className="text-[16px] font-semibold">同步数据</h2>
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            统计周期：{range.startDate} 至 {range.endDate}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Segmented
            aria-label="统计周期"
            value={selectedPreset}
            options={PERIODS}
            onValueChange={(period) => setRange(rangeForPeriod(period))}
          />
          <ExecutionDateRangePicker range={range} onChange={setRange} />
          <Button
            variant="secondary"
            size="sm"
            isDisabled={!query.data || query.isFetching}
            onClick={() => {
              if (query.data) exportExecutionCsv(query.data, tab);
            }}
          >
            <Download size={14} aria-hidden="true" />
            导出数据
          </Button>
        </div>
      </div>
      <Tabs value={tab} onValueChange={(value) => setTab(value as ExecutionTab)}>
        <TabsList className="mt-4">
          {TABS.map((item) => (
            <TabsTab key={item.value} value={item.value}>{item.label}</TabsTab>
          ))}
        </TabsList>
        {TABS.map((item) => (
          <TabsPanel key={item.value} value={item.value}>
            {item.value !== tab ? null : query.isError ? (
              <div className="flex min-h-48 items-center justify-center gap-3 text-sm">
                统计加载失败
                <Button size="sm" variant="secondary" onClick={() => void query.refetch()}>重试</Button>
              </div>
            ) : query.data ? (
              <MetricContent
                stats={query.data}
                tab={item.value}
                metric={metric}
                onMetricChange={setMetric}
              />
            ) : (
              <ExecutionMetricsSkeleton />
            )}
          </TabsPanel>
        ))}
      </Tabs>
    </section>
  );
}
