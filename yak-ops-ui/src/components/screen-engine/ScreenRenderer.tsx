import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type {
  ScreenBarComponent,
  ScreenComponent,
  ScreenDataOverrides,
  ScreenLineComponent,
  ScreenPieComponent,
  ScreenTableComponent,
  ScreenTemplate,
  ScreenTheme,
} from './model';

type ScreenChartComponent = ScreenLineComponent | ScreenBarComponent | ScreenPieComponent;

interface ComponentInteraction {
  selected?: boolean;
  onSelect?: () => void;
}

const CHART_COLORS = ['#38bdf8', '#22c55e', '#f59e0b', '#a78bfa', '#fb7185', '#2dd4bf'];

const formatValue = (value: string | number) => {
  if (typeof value !== 'number') return value;
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: Number.isInteger(value) ? 0 : 2 }).format(value);
};

const chartOptionFor = (component: ScreenChartComponent, theme: ScreenTheme): EChartsOption => {
  const axisText = theme.mutedTextColor;
  const axisLine = theme.panelBorderColor;
  const splitLine = `${theme.panelBorderColor}80`;
  const colors = [component.style?.accentColor ?? theme.primaryColor, ...CHART_COLORS];

  if (component.type === 'pie') {
    const data = component.data?.items ?? [];
    return {
      color: colors,
      tooltip: { trigger: 'item' },
      legend: component.options?.showLegend === false
        ? { show: false }
        : {
            right: 8,
            top: 'middle',
            orient: 'vertical',
            textStyle: { color: axisText, fontSize: 12 },
          },
      series: [{
        type: 'pie',
        radius: ['46%', '72%'],
        center: [component.options?.showLegend === false ? '50%' : '38%', '52%'],
        label: {
          show: component.options?.showLabels ?? false,
          color: theme.textColor,
          formatter: '{b}  {d}%',
        },
        itemStyle: { borderColor: theme.panelBackground, borderWidth: 2 },
        data,
      }],
    };
  }

  const data = component.data ?? { categories: [], series: [] };
  const isLine = component.type === 'line';
  return {
    color: colors,
    tooltip: { trigger: 'axis' },
    legend: component.options?.showLegend === false
      ? { show: false }
      : { top: 0, right: 4, textStyle: { color: axisText, fontSize: 12 } },
    grid: {
      left: 16,
      right: 14,
      top: component.options?.showLegend === false ? 14 : 38,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: !isLine,
      data: data.categories,
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: axisText, fontSize: 11 },
      splitLine: {
        show: component.options?.showGrid ?? true,
        lineStyle: { color: splitLine, type: 'dashed' },
      },
    },
    series: data.series.map((series) => ({
      name: series.name,
      type: component.type,
      data: series.values,
      smooth: isLine && (component.options?.smooth ?? true),
      symbol: isLine ? 'circle' : undefined,
      symbolSize: isLine ? 6 : undefined,
      barMaxWidth: component.type === 'bar' ? 34 : undefined,
      label: {
        show: component.options?.showLabels ?? false,
        position: 'top',
        color: theme.textColor,
      },
      lineStyle: isLine ? { width: 3 } : undefined,
      areaStyle: isLine ? { opacity: 0.08 } : undefined,
    })),
  };
};

function ScreenChart({ component, theme }: { component: ScreenChartComponent; theme: ScreenTheme }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const option = useMemo(() => chartOptionFor(component, theme), [component, theme]);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const chart = echarts.init(containerRef.current);
    chart.setOption(option, true);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
    };
  }, [option]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}

function ComponentFrame({
  component,
  theme,
  children,
  selected = false,
  onSelect,
}: {
  component: ScreenComponent;
  theme: ScreenTheme;
  children: ReactNode;
} & ComponentInteraction) {
  const style = component.style;
  const transparent = component.type === 'text';
  return (
    <div
      data-screen-component={component.id}
      data-screen-selected={selected || undefined}
      onClick={onSelect}
      className={[
        'absolute box-border flex min-h-0 flex-col overflow-hidden transition-[outline,filter] duration-150',
        onSelect ? 'cursor-pointer hover:brightness-[1.04]' : '',
      ].join(' ')}
      style={{
        left: component.x,
        top: component.y,
        width: component.width,
        height: component.height,
        padding: style?.padding ?? (transparent ? 0 : 22),
        background: style?.background ?? (transparent ? 'transparent' : theme.panelBackground),
        border: transparent ? undefined : `1px solid ${style?.borderColor ?? theme.panelBorderColor}`,
        borderRadius: style?.borderRadius ?? (transparent ? 0 : 12),
        boxShadow: style?.shadow,
        color: style?.color ?? theme.textColor,
        outline: selected ? '3px solid rgba(254, 44, 85, 0.92)' : undefined,
        outlineOffset: selected ? -3 : undefined,
      }}
    >
      {component.title ? (
        <div className="mb-3 shrink-0">
          <div
            className="text-[18px] font-semibold leading-6"
            style={{ color: style?.titleColor ?? theme.textColor }}
          >
            {component.title}
          </div>
          {component.subtitle ? (
            <div className="mt-1 text-[12px]" style={{ color: style?.subtitleColor ?? theme.mutedTextColor }}>
              {component.subtitle}
            </div>
          ) : null}
        </div>
      ) : null}
      <div className="min-h-0 flex-1">{children}</div>
    </div>
  );
}

function MetricComponent({
  component,
  theme,
  ...interaction
}: {
  component: Extract<ScreenComponent, { type: 'metric' }>;
  theme: ScreenTheme;
} & ComponentInteraction) {
  const data = component.data;
  const direction = data?.trendDirection ?? 'flat';
  const trendColor = direction === 'up' ? '#22c55e' : direction === 'down' ? '#f43f5e' : theme.mutedTextColor;
  const trendPrefix = direction === 'up' ? '↑' : direction === 'down' ? '↓' : '→';

  return (
    <ComponentFrame component={component} theme={theme} {...interaction}>
      <div className="flex h-full min-h-0 flex-col justify-center">
        <div className="flex items-end gap-2">
          <span
            className="text-[38px] font-semibold leading-none tracking-[-0.03em]"
            style={{ color: component.style?.valueColor ?? theme.textColor }}
          >
            {formatValue(data?.value ?? '--')}
          </span>
          {data?.unit ? <span className="pb-1 text-[13px]" style={{ color: theme.mutedTextColor }}>{data.unit}</span> : null}
        </div>
        {typeof data?.trend === 'number' || data?.trendLabel ? (
          <div className="mt-4 flex items-center gap-2 text-[12px]">
            {typeof data?.trend === 'number' ? (
              <span className="font-medium" style={{ color: trendColor }}>
                {trendPrefix} {Math.abs(data.trend)}%
              </span>
            ) : null}
            {data?.trendLabel ? <span style={{ color: theme.mutedTextColor }}>{data.trendLabel}</span> : null}
          </div>
        ) : null}
      </div>
    </ComponentFrame>
  );
}

function TableComponent({
  component,
  theme,
  ...interaction
}: {
  component: ScreenTableComponent;
  theme: ScreenTheme;
} & ComponentInteraction) {
  const data = component.data;
  return (
    <ComponentFrame component={component} theme={theme} {...interaction}>
      {!data ? (
        <div className="flex h-full items-center justify-center text-[13px]" style={{ color: theme.mutedTextColor }}>
          暂无预览数据
        </div>
      ) : (
        <div className="h-full overflow-hidden">
          <table className="w-full table-fixed border-collapse text-[12px]">
            <thead>
              <tr style={{ color: theme.mutedTextColor }}>
                {data.columns.map((column) => (
                  <th
                    key={column.key}
                    className="border-b px-3 py-2.5 font-medium"
                    style={{
                      width: column.width,
                      textAlign: column.align ?? 'left',
                      borderColor: theme.panelBorderColor,
                    }}
                  >
                    {column.title}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {data.columns.map((column) => (
                    <td
                      key={column.key}
                      className="truncate border-b px-3 py-3"
                      style={{
                        textAlign: column.align ?? 'left',
                        borderColor: `${theme.panelBorderColor}80`,
                        color: theme.textColor,
                      }}
                    >
                      {String(row[column.key] ?? '')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ComponentFrame>
  );
}

function renderScreenComponent(
  component: ScreenComponent,
  theme: ScreenTheme,
  interaction: ComponentInteraction,
) {
  switch (component.type) {
    case 'metric':
      return <MetricComponent component={component} theme={theme} {...interaction} />;
    case 'line':
    case 'bar':
    case 'pie':
      return (
        <ComponentFrame component={component} theme={theme} {...interaction}>
          <ScreenChart component={component} theme={theme} />
        </ComponentFrame>
      );
    case 'table':
      return <TableComponent component={component} theme={theme} {...interaction} />;
    case 'text':
      return (
        <ComponentFrame component={component} theme={theme} {...interaction}>
          <div
            className="flex h-full items-center"
            style={{
              color: component.style?.color ?? theme.textColor,
              fontSize: component.style?.fontSize ?? 16,
              fontWeight: component.style?.fontWeight,
              letterSpacing: component.style?.letterSpacing,
              justifyContent: component.style?.textAlign === 'center'
                ? 'center'
                : component.style?.textAlign === 'right'
                  ? 'flex-end'
                  : 'flex-start',
              textAlign: component.style?.textAlign,
              whiteSpace: 'pre-wrap',
            }}
          >
            {component.data?.content ?? ''}
          </div>
        </ComponentFrame>
      );
    default:
      return null;
  }
}

const withRuntimeData = (component: ScreenComponent, overrides?: ScreenDataOverrides): ScreenComponent => {
  const data = overrides?.[component.id];
  return data ? ({ ...component, data } as ScreenComponent) : component;
};

function RuntimeScreenComponent({
  component,
  theme,
  selected,
  onSelect,
}: {
  component: ScreenComponent;
  theme: ScreenTheme;
} & ComponentInteraction) {
  return renderScreenComponent(component, theme, { selected, onSelect });
}

export interface ScreenRendererProps {
  template: ScreenTemplate;
  data?: ScreenDataOverrides;
  className?: string;
  selectedComponentId?: string;
  onComponentClick?: (component: ScreenComponent) => void;
}

/**
 * Renders the fixed design canvas and scales it to the available width. Layout,
 * visual style and preview data all come from the template document.
 */
export function ScreenRenderer({
  template,
  data,
  className = '',
  selectedComponentId,
  onComponentClick,
}: ScreenRendererProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const updateScale = () => {
      const width = containerRef.current?.clientWidth ?? template.width;
      setScale(width > 0 ? width / template.width : 1);
    };
    updateScale();
    const observer = new ResizeObserver(updateScale);
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [template.width]);

  return (
    <div
      ref={containerRef}
      className={`relative w-full overflow-hidden ${className}`}
      style={{
        aspectRatio: `${template.width} / ${template.height}`,
        background: template.theme.background,
      }}
    >
      <div
        className="absolute left-0 top-0 overflow-hidden"
        style={{
          width: template.width,
          height: template.height,
          transform: `scale(${scale})`,
          transformOrigin: 'left top',
          background: template.theme.background,
          color: template.theme.textColor,
          fontFamily: template.theme.fontFamily,
        }}
      >
        {template.components.map((component) => {
          const runtimeComponent = withRuntimeData(component, data);
          return (
            <RuntimeScreenComponent
              key={component.id}
              component={runtimeComponent}
              theme={template.theme}
              selected={component.id === selectedComponentId}
              onSelect={onComponentClick ? () => onComponentClick(runtimeComponent) : undefined}
            />
          );
        })}
      </div>
    </div>
  );
}
