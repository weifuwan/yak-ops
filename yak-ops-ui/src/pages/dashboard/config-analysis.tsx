import {
  metricComputationFor,
  NUMBER_FORMAT_OPTIONS,
  patchMetricComputation,
  QUICK_CALCULATION_OPTIONS,
} from '@/components/analysis/analysis';
import {
  calculatedFieldFor,
  isCalculatedFieldKey,
} from '@/components/analysis/calculated-field';
import { resolveAnalysisEncoding } from '@/components/analysis/encoding';
import type {
  AnalysisComputationConfig,
  AnalysisMetricComputation,
  AnalysisNumberFormat,
  AnalysisQuickCalculation,
  AnalysisSpec,
  AnalysisTopNDirection,
  PublishedDataset,
} from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { InputNumber, Select, Switch } from 'antd';
import { AGGREGATION_OPTIONS } from './helpers';

const QUICK_CALCULATION_MESSAGE_IDS: Record<AnalysisQuickCalculation, string> = {
  none: 'pages.dashboard.editor.analysis.quick.none',
  percent_of_total: 'pages.dashboard.editor.analysis.quick.percentOfTotal',
  running_total: 'pages.dashboard.editor.analysis.quick.runningTotal',
  rank: 'pages.dashboard.editor.analysis.quick.rank',
  previous_change: 'pages.dashboard.editor.analysis.quick.previousChange',
};

const NUMBER_FORMAT_MESSAGE_IDS: Record<AnalysisNumberFormat, string> = {
  auto: 'pages.dashboard.editor.analysis.number.auto',
  number: 'pages.dashboard.editor.analysis.number.number',
  percent: 'pages.dashboard.editor.analysis.number.percent',
};

export function ChartAnalysisConfig({
  spec,
  dataset,
  onChange,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  onChange: (analysis: AnalysisComputationConfig) => void;
}) {
  const intl = useIntl();
  const colorActive = Boolean(
    resolveAnalysisEncoding(spec).color.find((item) => item.role === 'dimension')?.field,
  );
  const supportsSequentialCalculation = spec.type !== 'metric' && spec.dimensions.length > 0;
  const topN = spec.analysis?.topN;
  const physicalMetrics = spec.metrics.filter((metric) => !isCalculatedFieldKey(spec, metric.field));
  const supportsTopN = spec.type !== 'metric' && spec.dimensions.length > 0 && physicalMetrics.length > 0;
  const topNMetricActive = !topN || physicalMetrics.some((metric) => metric.field === topN.metricField);
  const aggregationLabels = Object.fromEntries(
    AGGREGATION_OPTIONS.map((item) => [
      item.value,
      intl.formatMessage({ id: item.messageId }),
    ]),
  );
  const quickCalculationOptions = QUICK_CALCULATION_OPTIONS.map((item) => ({
    value: item.value,
    label: intl.formatMessage({ id: QUICK_CALCULATION_MESSAGE_IDS[item.value] }),
  }));
  const numberFormatOptions = NUMBER_FORMAT_OPTIONS.map((item) => ({
    value: item.value,
    label: intl.formatMessage({ id: NUMBER_FORMAT_MESSAGE_IDS[item.value] }),
  }));

  const patchMetric = (field: string, patch: Partial<AnalysisMetricComputation>) => {
    onChange(patchMetricComputation(spec, field, patch));
  };

  const patchTopN = (patch: Partial<NonNullable<AnalysisComputationConfig['topN']>>) => {
    const fallbackMetric = physicalMetrics[0];
    if (!fallbackMetric) return;
    const currentMetric = topN && physicalMetrics.some((metric) => metric.field === topN.metricField)
      ? topN.metricField
      : fallbackMetric.field;
    onChange({
      ...spec.analysis,
      version: 1,
      topN: {
        enabled: topN?.enabled ?? false,
        metricField: currentMetric,
        count: topN?.count ?? 10,
        direction: topN?.direction ?? 'top',
        ...patch,
      },
    });
  };

  return (
    <div className="space-y-4 pb-1 text-[11px] text-[#475467]">
      <div>
        <div className="mb-2.5 text-[10px] font-semibold text-[#667085]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.metricCalculation' })}
        </div>
        <div className="space-y-2.5">
          {spec.metrics.map((metric) => {
            const field = dataset.fields.find((item) => item.key === metric.field);
            const calculated = calculatedFieldFor(spec, metric.field);
            const config = metricComputationFor(spec, metric.field);
            const stored = spec.analysis?.metrics?.[metric.field];
            return (
              <div key={metric.field} className="rounded-[8px] border border-[#e8eaee] bg-[#fafbfc] p-2.5">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-[10px] font-medium text-[#344054]">
                      {calculated?.name ?? field?.label ?? metric.field}
                    </div>
                    <div className="mt-0.5 text-[9px] text-[#98a2b3]">
                      {calculated
                        ? intl.formatMessage({ id: 'pages.dashboard.editor.analysis.calculatedField' })
                        : aggregationLabels[metric.aggregation] ?? metric.aggregation}
                    </div>
                  </div>
                  {supportsSequentialCalculation ? (
                    <Select
                      size="small"
                      className="w-[126px]"
                      value={config.quickCalculation}
                      options={quickCalculationOptions}
                      onChange={(quickCalculation: AnalysisQuickCalculation) =>
                        patchMetric(metric.field, { quickCalculation })}
                    />
                  ) : (
                    <span className="text-[9px] text-[#98a2b3]">
                      {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.singleValue' })}
                    </span>
                  )}
                </div>

                <div className="mt-2.5 grid grid-cols-[1fr_78px] gap-2">
                  <Select
                    size="small"
                    value={config.numberFormat}
                    options={numberFormatOptions}
                    onChange={(numberFormat: AnalysisNumberFormat) =>
                      patchMetric(metric.field, { numberFormat })}
                  />
                  <Select
                    size="small"
                    value={stored?.decimalPlaces ?? 'auto'}
                    options={[
                      {
                        label: intl.formatMessage({ id: 'pages.dashboard.editor.analysis.auto' }),
                        value: 'auto',
                      },
                      ...([0, 1, 2, 3, 4] as const).map((value) => ({
                        label: intl.formatMessage(
                          { id: 'pages.dashboard.editor.analysis.decimalPlaces' },
                          { count: value },
                        ),
                        value,
                      })),
                    ]}
                    onChange={(decimalPlaces: 'auto' | 0 | 1 | 2 | 3 | 4) =>
                      patchMetric(metric.field, {
                        decimalPlaces: decimalPlaces === 'auto' ? undefined : decimalPlaces,
                      })}
                  />
                </div>
                <label className="mt-2.5 flex items-center justify-between">
                  <span className="text-[10px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.grouping' })}
                  </span>
                  <Switch
                    size="small"
                    checked={config.useGrouping}
                    onChange={(useGrouping) => patchMetric(metric.field, { useGrouping })}
                  />
                </label>
                {calculated ? (
                  <div className="mt-2 truncate rounded-[5px] bg-white px-2 py-1 font-mono text-[8px] text-[#98a2b3]" title={calculated.expression}>
                    {calculated.expression}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        <div className="mt-2 text-[9px] leading-4 text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.calculationHint' })}
        </div>
      </div>

      {supportsTopN ? (
        <div className="border-t border-[#f0f1f3] pt-4">
          <div className="mb-2.5 flex items-center justify-between">
            <div>
              <div className="text-[10px] font-semibold text-[#667085]">Top / Bottom N</div>
              <div className="mt-0.5 text-[9px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.topNPhysicalOnly' })}
              </div>
            </div>
            <Switch
              size="small"
              checked={Boolean(topN?.enabled)}
              disabled={colorActive && !topN?.enabled}
              onChange={(enabled) => patchTopN({ enabled })}
            />
          </div>

          {topN?.enabled ? (
            <div className="space-y-2">
              <Select
                size="small"
                className="w-full"
                placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.analysis.topNMetricPlaceholder' })}
                value={topNMetricActive ? topN.metricField : undefined}
                options={physicalMetrics.map((metric) => ({
                  label: `${dataset.fields.find((item) => item.key === metric.field)?.label ?? metric.field} · ${aggregationLabels[metric.aggregation] ?? metric.aggregation}`,
                  value: metric.field,
                }))}
                onChange={(metricField: string) => patchTopN({ metricField })}
              />
              <div className="grid grid-cols-[1fr_92px] gap-2">
                <Select
                  size="small"
                  value={topN.direction}
                  options={[
                    { label: 'Top N', value: 'top' },
                    { label: 'Bottom N', value: 'bottom' },
                  ]}
                  onChange={(direction: AnalysisTopNDirection) => patchTopN({ direction })}
                />
                <InputNumber
                  size="small"
                  className="w-full"
                  min={1}
                  max={100}
                  value={topN.count}
                  onChange={(count) => {
                    if (typeof count === 'number') patchTopN({ count });
                  }}
                />
              </div>
              {!topNMetricActive ? (
                <div className="text-[9px] leading-4 text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.topNMetricInvalid' })}
                </div>
              ) : null}
            </div>
          ) : null}

          {colorActive ? (
            <div className="mt-2 rounded-[6px] bg-[#f7f8fa] px-2 py-1.5 text-[9px] leading-4 text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.analysis.topNColorHint' })}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
