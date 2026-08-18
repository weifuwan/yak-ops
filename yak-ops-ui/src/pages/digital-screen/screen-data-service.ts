import type {
  ScreenComponent,
  ScreenComponentData,
} from '@/components/screen-engine';
import {
  fetchAnalysisDatasets,
  queryAnalysisDataset,
} from '@/components/analysis/dataset-service';
import type {
  Aggregation,
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
  Scalar,
} from '@/components/analysis/model';
import type { DigitalScreenComponentBinding } from './model';

export const fetchDigitalScreenDatasets = fetchAnalysisDatasets;

export const SCREEN_AGGREGATION_LABELS: Record<Aggregation, string> = {
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  COUNT_DISTINCT: '去重计数',
  MAX: '最大值',
  MIN: '最小值',
};

export const isBindableScreenComponent = (component?: ScreenComponent) => Boolean(
  component && component.type !== 'text',
);

export const canQueryScreenComponent = (
  component: ScreenComponent,
  binding?: DigitalScreenComponentBinding,
) => {
  if (!binding?.datasetId) return false;
  if (component.type === 'text') return false;
  if (component.type === 'metric') return binding.metrics.length === 1;
  if (component.type === 'table') return binding.dimensions.length > 0 || binding.metrics.length > 0;
  return binding.dimensions.length > 0 && binding.metrics.length > 0;
};

export const buildScreenDatasetQueryPayload = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
): DatasetQueryPayload => ({
  dimensions: component.type === 'metric' ? [] : binding.dimensions,
  metrics: binding.metrics.map((metric) => ({
    fieldId: metric.field,
    aggregation: metric.aggregation,
  })),
  filters: [],
  sorts: [],
  limit: component.type === 'table' ? 100 : 200,
  timeoutSeconds: 30,
});

const bindingIndex = (
  result: DatasetQueryResult,
  fieldId: string,
  aggregation?: Aggregation,
) => result.bindings.findIndex((item) => (
  item.fieldId === fieldId
  && (aggregation ? item.aggregation === aggregation : !item.aggregation)
));

const cell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation?: Aggregation,
) => {
  const index = bindingIndex(result, fieldId, aggregation);
  return index >= 0 ? row[index] : null;
};

const numericCell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation: Aggregation,
) => {
  const value = Number(cell(result, row, fieldId, aggregation) ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const fieldLabel = (dataset: PublishedDataset, fieldId: string) => (
  dataset.fields.find((field) => field.key === fieldId)?.label || fieldId
);

const metricLabel = (
  dataset: PublishedDataset,
  metric: DigitalScreenComponentBinding['metrics'][number],
) => `${fieldLabel(dataset, metric.field)} · ${SCREEN_AGGREGATION_LABELS[metric.aggregation]}`;

const rowLabel = (
  dataset: PublishedDataset,
  result: DatasetQueryResult,
  row: Scalar[],
  dimensions: string[],
) => dimensions.map((fieldId) => {
  const value = cell(result, row, fieldId);
  return value == null ? fieldLabel(dataset, fieldId) : String(value);
}).join(' / ');

export const toScreenComponentData = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
): ScreenComponentData | undefined => {
  if (component.type === 'metric') {
    const metric = binding.metrics[0];
    if (!metric) return undefined;
    const row = result.rows[0];
    return {
      value: row ? numericCell(result, row, metric.field, metric.aggregation) : 0,
      trendLabel: `${dataset.name} · DV${result.datasetVersionNo}`,
    };
  }

  if (component.type === 'line' || component.type === 'bar') {
    return {
      categories: result.rows.map((row) => rowLabel(dataset, result, row, binding.dimensions)),
      series: binding.metrics.map((metric) => ({
        name: metricLabel(dataset, metric),
        values: result.rows.map((row) => numericCell(
          result,
          row,
          metric.field,
          metric.aggregation,
        )),
      })),
    };
  }

  if (component.type === 'pie') {
    const metric = binding.metrics[0];
    if (!metric) return undefined;
    return {
      items: result.rows.map((row) => ({
        name: rowLabel(dataset, result, row, binding.dimensions),
        value: numericCell(result, row, metric.field, metric.aggregation),
      })),
    };
  }

  if (component.type === 'table') {
    const dimensionColumns = binding.dimensions.map((fieldId) => ({
      key: `dimension:${fieldId}`,
      title: fieldLabel(dataset, fieldId),
      align: 'left' as const,
    }));
    const metricColumns = binding.metrics.map((metric) => ({
      key: `metric:${metric.field}:${metric.aggregation}`,
      title: metricLabel(dataset, metric),
      align: 'right' as const,
    }));
    return {
      columns: [...dimensionColumns, ...metricColumns],
      rows: result.rows.map((row) => {
        const record: Record<string, Scalar> = {};
        binding.dimensions.forEach((fieldId) => {
          record[`dimension:${fieldId}`] = cell(result, row, fieldId);
        });
        binding.metrics.forEach((metric) => {
          record[`metric:${metric.field}:${metric.aggregation}`] = cell(
            result,
            row,
            metric.field,
            metric.aggregation,
          );
        });
        return record;
      }),
    };
  }

  return undefined;
};

export const queryScreenComponentData = async (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
) => {
  if (!canQueryScreenComponent(component, binding)) return undefined;
  const result = await queryAnalysisDataset(
    dataset.id,
    buildScreenDatasetQueryPayload(component, binding),
  );
  return toScreenComponentData(component, binding, dataset, result);
};
