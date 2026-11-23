import { useMemo } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { Card, CardContent, Skeleton } from "@yak-ops/yak-ui";

import { EChart } from "@/app/operations/dashboard/echart";
import type { StatusCountRecord } from "@/service/operations/analysis";
import { overviewStatusColor } from "../constants";
import { percentOf } from "../utils/statistics";

function number(value: number) {
  return value.toLocaleString("zh-CN");
}

function StatusCardSkeleton({ title }: { title: string }) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="mb-5 flex items-center justify-between">
          <div className="text-sm font-medium">{title}</div>
          <Skeleton width={70} height={14} />
        </div>
        <div className="grid items-center gap-6 lg:grid-cols-[minmax(180px,0.8fr)_minmax(0,1.2fr)]">
          <Skeleton width="100%" height={224} />
          <div className="space-y-3">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} width="100%" height={28} />
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function StatusOverviewCard({
  title,
  records,
  total,
  label,
  loading = false,
  fetching = false,
  error = false,
}: {
  title: string;
  records: StatusCountRecord[];
  total: number;
  label: (state: string) => string;
  loading?: boolean;
  fetching?: boolean;
  error?: boolean;
}) {
  const visible = useMemo(
    () =>
      [...records]
        .filter((item) => item.count > 0)
        .sort((a, b) => b.count - a.count),
    [records],
  );

  const option = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 300,
      tooltip: {
        trigger: "item",
        formatter: "{b}<br/>{c} ({d}%)",
      },
      series: [
        {
          type: "pie",
          radius: ["58%", "78%"],
          center: ["50%", "50%"],
          avoidLabelOverlap: true,
          label: { show: false },
          labelLine: { show: false },
          emphasis: { scale: true, scaleSize: 4 },
          itemStyle: {
            borderColor: "#ffffff",
            borderWidth: 2,
          },
          data: visible.map((item) => ({
            name: label(item.state),
            value: item.count,
            itemStyle: { color: overviewStatusColor(item.state) },
          })),
        },
      ],
    }),
    [label, visible],
  );

  if (loading) return <StatusCardSkeleton title={title} />;

  return (
    <Card>
      <CardContent className="p-5">
        <div className="mb-5 flex items-center justify-between gap-4">
          <div className="text-sm font-medium">{title}</div>
          <div className="shrink-0 text-xs tabular-nums text-[var(--yak-components-muted-text)]">
            总数 {number(total)}
          </div>
        </div>

        {error && visible.length === 0 ? (
          <div className="py-16 text-center text-sm text-[var(--yak-components-muted-text)]">
            状态统计加载失败
          </div>
        ) : visible.length === 0 ? (
          <div className="py-16 text-center text-sm text-[var(--yak-components-muted-text)]">
            暂无运行数据
          </div>
        ) : (
          <div className="grid items-center gap-6 lg:grid-cols-[minmax(180px,0.8fr)_minmax(0,1.2fr)]">
            <div className="relative mx-auto h-56 w-full max-w-[260px]">
              <EChart
                option={option}
                className="h-56 w-full"
                loading={fetching}
                ariaLabel={`${title}环形图`}
              />
              <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                <div className="text-2xl font-semibold tabular-nums">{number(total)}</div>
                <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">实例总数</div>
              </div>
            </div>

            <div className="min-w-0 divide-y divide-[var(--yak-components-panel-border)]">
              {visible.map((item) => (
                <div key={item.state} className="flex items-center gap-3 py-2.5 text-xs">
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: overviewStatusColor(item.state) }}
                  />
                  <span className="min-w-0 flex-1 truncate">{label(item.state)}</span>
                  <span className="w-16 shrink-0 text-right font-medium tabular-nums">
                    {number(item.count)}
                  </span>
                  <span className="w-14 shrink-0 text-right tabular-nums text-[var(--yak-components-muted-text)]">
                    {percentOf(item.count, total).toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
