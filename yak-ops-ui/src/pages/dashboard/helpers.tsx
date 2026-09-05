import {
  applyAnalysisEncoding,
  legacyAnalysisEncoding,
  rebindAnalysisEncoding,
} from '@/components/analysis/encoding';
import type { AnalysisSpec } from '@/components/analysis/model';
import { createAnalysisVisualConfig } from '@/components/analysis/style';
import { getIntl } from '@umijs/max';
import { BarChart3, ChartLine, ChartPie, Sigma, Table2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { DEFAULT_DASHBOARD } from './defaults';
import type {
  Aggregation,
  ChartType,
  DashboardDocument,
  DashboardWidget,
  FilterOperator,
  PublishedDataset,
} from './model';

/** Legacy key is read only for one-time migration into the server-side Dashboard domain. */
export const STORAGE_KEY = 'yak-dashboard-designer.v2';
export const GRID_COLUMNS = 24;
export const GRID_ROW_HEIGHT = 28;
export const FIELD_DRAG_MIME = 'application/x-yak-dashboard-field';

export const CHART_META: Record<ChartType, {
  labelId: string;
  descriptionId: string;
  icon: ReactNode;
}> = {
  metric: {
    labelId: 'pages.dashboard.editor.chart.metric',
    descriptionId: 'pages.dashboard.editor.chart.metric.description',
    icon: <Sigma size={15} />,
  },
  bar: {
    labelId: 'pages.dashboard.editor.chart.bar',
    descriptionId: 'pages.dashboard.editor.chart.bar.description',
    icon: <BarChart3 size={15} />,
  },
  stackedBar: {
    labelId: 'pages.dashboard.editor.chart.stackedBar',
    descriptionId: 'pages.dashboard.editor.chart.stackedBar.description',
    icon: <BarChart3 size={15} />,
  },
  line: {
    labelId: 'pages.dashboard.editor.chart.line',
    descriptionId: 'pages.dashboard.editor.chart.line.description',
    icon: <ChartLine size={15} />,
  },
  area: {
    labelId: 'pages.dashboard.editor.chart.area',
    descriptionId: 'pages.dashboard.editor.chart.area.description',
    icon: <ChartLine size={15} />,
  },
  pie: {
    labelId: 'pages.dashboard.editor.chart.pie',
    descriptionId: 'pages.dashboard.editor.chart.pie.description',
    icon: <ChartPie size={15} />,
  },
  scatter: {
    labelId: 'pages.dashboard.editor.chart.scatter',
    descriptionId: 'pages.dashboard.editor.chart.scatter.description',
    icon: <Sigma size={15} />,
  },
  radar: {
    labelId: 'pages.dashboard.editor.chart.radar',
    descriptionId: 'pages.dashboard.editor.chart.radar.description',
    icon: <ChartPie size={15} />,
  },
  funnel: {
    labelId: 'pages.dashboard.editor.chart.funnel',
    descriptionId: 'pages.dashboard.editor.chart.funnel.description',
    icon: <BarChart3 size={15} />,
  },
  treemap: {
    labelId: 'pages.dashboard.editor.chart.treemap',
    descriptionId: 'pages.dashboard.editor.chart.treemap.description',
    icon: <Table2 size={15} />,
  },
  table: {
    labelId: 'pages.dashboard.editor.chart.table',
    descriptionId: 'pages.dashboard.editor.chart.table.description',
    icon: <Table2 size={15} />,
  },
};

export const AGGREGATION_OPTIONS: Array<{ messageId: string; value: Aggregation }> = [
  { messageId: 'pages.dashboard.editor.aggregation.sum', value: 'SUM' },
  { messageId: 'pages.dashboard.editor.aggregation.avg', value: 'AVG' },
  { messageId: 'pages.dashboard.editor.aggregation.count', value: 'COUNT' },
  { messageId: 'pages.dashboard.editor.aggregation.countDistinct', value: 'COUNT_DISTINCT' },
  { messageId: 'pages.dashboard.editor.aggregation.max', value: 'MAX' },
  { messageId: 'pages.dashboard.editor.aggregation.min', value: 'MIN' },
];

export const FILTER_OPERATOR_OPTIONS: Array<{ messageId: string; value: FilterOperator }> = [
  { messageId: 'pages.dashboard.editor.operator.eq', value: 'eq' },
  { messageId: 'pages.dashboard.editor.operator.neq', value: 'neq' },
  { messageId: 'pages.dashboard.editor.operator.contains', value: 'contains' },
  { messageId: 'pages.dashboard.editor.operator.gt', value: 'gt' },
  { messageId: 'pages.dashboard.editor.operator.gte', value: 'gte' },
  { messageId: 'pages.dashboard.editor.operator.lt', value: 'lt' },
  { messageId: 'pages.dashboard.editor.operator.lte', value: 'lte' },
];

export const cloneDashboard = (dashboard: DashboardDocument): DashboardDocument =>
  JSON.parse(JSON.stringify(dashboard)) as DashboardDocument;

const legacyWidgetToCurrent = (value: any): DashboardWidget => {
  if (value?.inlineAnalysis || (value?.analysisId && !value?.type)) return value as DashboardWidget;
  if (!value?.type || !value?.datasetId) return value as DashboardWidget;
  const inlineAnalysis: AnalysisSpec = {
    type: value.type,
    datasetId: String(value.datasetId),
    dimensions: Array.isArray(value.dimensions) ? value.dimensions : [],
    metrics: Array.isArray(value.metrics) ? value.metrics : [],
    filters: Array.isArray(value.filters) ? value.filters : [],
    sort: value.sort,
    style: value.style ?? createAnalysisVisualConfig(value.type),
    limit: value.limit,
    timeoutSeconds: value.timeoutSeconds,
  };
  return {
    id: value.id,
    title: value.title || getIntl().formatMessage({ id: 'pages.dashboard.editor.unnamedChart' }),
    inlineAnalysis,
    x: Number(value.x ?? 0),
    y: Number(value.y ?? 0),
    w: Number(value.w ?? 10),
    h: Number(value.h ?? 7),
    minW: value.minW,
    minH: value.minH,
  };
};

export const loadDashboard = (): DashboardDocument => {
  if (typeof window === 'undefined') return cloneDashboard(DEFAULT_DASHBOARD);
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) return cloneDashboard(DEFAULT_DASHBOARD);
    const parsed = JSON.parse(stored) as DashboardDocument;
    if (parsed?.version !== 1 || !Array.isArray(parsed.widgets)) return cloneDashboard(DEFAULT_DASHBOARD);
    return {
      ...cloneDashboard(DEFAULT_DASHBOARD),
      ...parsed,
      id: 'dashboard-new',
      currentVersionId: undefined,
      currentVersionNo: undefined,
      widgets: parsed.widgets.map(legacyWidgetToCurrent),
      globalFilters: Array.isArray(parsed.globalFilters) ? parsed.globalFilters : [],
      interactions: Array.isArray(parsed.interactions) ? parsed.interactions : [],
    };
  } catch {
    return cloneDashboard(DEFAULT_DASHBOARD);
  }
};

export const isPersistedDashboard = (value?: string) => Boolean(value && /^\d+$/.test(value));

export const findDataset = (datasets: PublishedDataset[], id?: string) =>
  datasets.find((dataset) => dataset.id === id) ?? datasets[0];

export const defaultBindings = (dataset: PublishedDataset) => ({
  dimensions: dataset.fields.filter((field) => field.role === 'dimension').slice(0, 1).map((field) => field.key),
  metrics: dataset.fields.filter((field) => field.role === 'metric').slice(0, 1).map((field) => ({
    field: field.key,
    aggregation: 'SUM' as Aggregation,
  })),
});

export const createInlineAnalysis = (type: ChartType, dataset: PublishedDataset): AnalysisSpec => {
  const bindings = defaultBindings(dataset);
  const base: AnalysisSpec = {
    type,
    datasetId: dataset.id,
    dimensions: bindings.dimensions,
    metrics: bindings.metrics,
    filters: [],
    style: createAnalysisVisualConfig(type),
    limit: type === 'table' ? 200 : 500,
    timeoutSeconds: 30,
  };
  const encoded = applyAnalysisEncoding(base, legacyAnalysisEncoding(base));
  return rebindAnalysisEncoding(encoded, dataset);
};

const widgetShapeFor = (type: ChartType) => {
  if (type === 'metric') return { w: 6, h: 4, minW: 4, minH: 3 };
  if (type === 'table') return { w: 16, h: 8, minW: 8, minH: 6 };
  if (type === 'radar' || type === 'treemap') return { w: 12, h: 8, minW: 7, minH: 6 };
  return { w: 10, h: 7, minW: 6, minH: 5 };
};

export const createWidget = (type: ChartType, dataset: PublishedDataset, y: number): DashboardWidget => {
  const intl = getIntl();
  const typeLabel = intl.formatMessage({ id: CHART_META[type].labelId });
  return {
    id: `widget-${Date.now()}-${Math.round(Math.random() * 1000)}`,
    title: intl.formatMessage(
      { id: 'pages.dashboard.editor.chart.newTitle' },
      { type: typeLabel },
    ),
    inlineAnalysis: createInlineAnalysis(type, dataset),
    x: 0,
    y,
    ...widgetShapeFor(type),
  };
};

const rebindInlineAnalysis = (spec: AnalysisSpec, dataset: PublishedDataset): AnalysisSpec => {
  const sameDataset = spec.datasetId === dataset.id;
  const filters = sameDataset
    ? spec.filters.filter((filter) => dataset.fields.some((field) => field.key === filter.field))
    : [];
  const sort = sameDataset && spec.sort && dataset.fields.some((field) => field.key === spec.sort?.field)
    ? spec.sort
    : undefined;

  if (!sameDataset) {
    const fresh = createInlineAnalysis(spec.type, dataset);
    return {
      ...fresh,
      style: { ...spec.style },
      filters,
      sort,
      limit: spec.type === 'table' ? 200 : spec.limit,
      timeoutSeconds: spec.timeoutSeconds,
    };
  }

  const rebound = rebindAnalysisEncoding(spec, dataset);
  return { ...rebound, filters, sort };
};

export const reconcileDashboard = (
  dashboard: DashboardDocument,
  datasets: PublishedDataset[],
): DashboardDocument => {
  if (!datasets.length) return dashboard;
  const activeDataset = findDataset(datasets, dashboard.activeDatasetId) ?? datasets[0];
  return {
    ...dashboard,
    activeDatasetId: activeDataset.id,
    widgets: dashboard.widgets.map((item) => {
      if (item.analysisId || !item.inlineAnalysis) return item;
      const widgetDataset = datasets.find((dataset) => dataset.id === item.inlineAnalysis?.datasetId) ?? activeDataset;
      return { ...item, inlineAnalysis: rebindInlineAnalysis(item.inlineAnalysis, widgetDataset) };
    }),
  };
};
