import { BarChart, LineChart, PieChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { init, use as useECharts, type ECharts, type EChartsCoreOption } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { useEffect, useRef } from "react";
import { Spinner } from "@yak-ops/yak-ui";

useECharts([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

export interface EChartProps {
  option: EChartsCoreOption;
  className?: string;
  loading?: boolean;
  ariaLabel?: string;
}

export function EChart({ ariaLabel, className = "h-72", loading = false, option }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ECharts>();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = init(container);
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(container);

    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = undefined;
    };
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, { notMerge: true });
  }, [option]);

  return (
    <div className="relative">
      <div
        ref={containerRef}
        role={ariaLabel ? "img" : undefined}
        aria-label={ariaLabel}
        className={className}
      />
      {loading ? (
        <div className="absolute inset-0 flex items-center justify-center bg-white/70">
          <Spinner size="large" label="加载图表" />
        </div>
      ) : null}
    </div>
  );
}
