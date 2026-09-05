import {
  calculatedFieldKey,
  isCalculatedFieldKey,
} from '@/components/analysis/calculated-field';
import {
  ANALYSIS_ENCODING_RULES,
  applyAnalysisEncoding,
  resolveAnalysisEncoding,
  updateEncodingMetricAggregation,
} from '@/components/analysis/encoding';
import type {
  AnalysisEncoding,
  AnalysisEncodingBinding,
  AnalysisSpec,
  DatasetFieldRole,
} from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { Dropdown, Select } from 'antd';
import { Plus, X } from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { readChartFieldDragPayload } from './chart-field-drag';
import { AGGREGATION_OPTIONS, FIELD_DRAG_MIME } from './helpers';
import type { Aggregation, PublishedDataset } from './model';

interface FieldOption {
  label: string;
  value: string;
  role: DatasetFieldRole;
  calculated?: boolean;
}

const AXIS_TYPES = new Set(['bar', 'stackedBar', 'line', 'area', 'scatter']);

const shelfMessageId = (spec: AnalysisSpec, channel: string) => {
  if (channel === 'category') {
    return AXIS_TYPES.has(spec.type)
      ? 'pages.dashboard.editor.encoding.xAxis'
      : 'pages.dashboard.editor.encoding.dimension';
  }
  if (channel === 'value') {
    return AXIS_TYPES.has(spec.type)
      ? 'pages.dashboard.editor.encoding.yAxis'
      : 'pages.dashboard.editor.encoding.metric';
  }
  return undefined;
};

export function ChartEncodingShelf({
  spec,
  dataset,
  editable,
  onSpecPatch,
}: {
  spec?: AnalysisSpec;
  dataset?: PublishedDataset;
  editable: boolean;
  onSpecPatch?: (patch: Partial<AnalysisSpec>) => void;
}) {
  const intl = useIntl();
  if (!spec || !dataset) {
    return (
      <div className="shrink-0 border-b border-[#e4e7ec] bg-white px-4 py-2 text-[10px] text-[#98a2b3]">
        {intl.formatMessage({ id: 'pages.dashboard.editor.encoding.selectDataset' })}
      </div>
    );
  }

  const calculatedFields = spec.analysis?.calculatedFields ?? [];
  const fieldOptions: FieldOption[] = [
    ...dataset.fields.map((field) => ({
      label: field.label,
      value: field.key,
      role: field.role,
    })),
    ...calculatedFields.map((field) => ({
      label: field.name,
      value: calculatedFieldKey(field),
      role: 'metric' as const,
      calculated: true,
    })),
  ];
  const fieldLabel = new Map(fieldOptions.map((option) => [option.value, option.label]));
  const calculated = new Set(fieldOptions.filter((option) => option.calculated).map((option) => option.value));
  const aggregationOptions = AGGREGATION_OPTIONS.map((option) => ({
    value: option.value,
    label: intl.formatMessage({ id: option.messageId }),
  }));
  const aggregationLabel = new Map(aggregationOptions.map((option) => [option.value, option.label]));
  const encoding = resolveAnalysisEncoding(spec);
  const primaryRules = ANALYSIS_ENCODING_RULES[spec.type]
    .filter((rule) => rule.channel === 'category' || rule.channel === 'value');

  const changeEncoding = (nextEncoding: AnalysisEncoding) => {
    if (!onSpecPatch) return;
    const next = applyAnalysisEncoding(spec, nextEncoding);
    const nextSort = spec.sort
      && !next.dimensions.includes(spec.sort.field)
      && !next.metrics.some((metric) => metric.field === spec.sort?.field)
      ? undefined
      : spec.sort;
    const currentTopN = spec.analysis?.topN;
    const topNMetricStillActive = currentTopN
      ? next.metrics.some((metric) => metric.field === currentTopN.metricField)
      : true;
    const topNFallback = next.metrics.find((metric) => !isCalculatedFieldKey(next, metric.field));
    const nextAnalysis = currentTopN && !topNMetricStillActive
      ? {
        ...spec.analysis,
        version: 1 as const,
        topN: topNFallback
          ? { ...currentTopN, metricField: topNFallback.field }
          : { ...currentTopN, enabled: false },
      }
      : spec.analysis;
    const hadColor = Boolean(spec.encoding?.color?.length);
    const hasColor = Boolean(next.encoding.color.length);
    const shouldRevealLegend = !hadColor
      && hasColor
      && ['bar', 'stackedBar', 'line', 'area', 'scatter'].includes(spec.type);

    onSpecPatch({
      encoding: next.encoding,
      dimensions: next.dimensions,
      metrics: next.metrics,
      sort: nextSort,
      analysis: nextAnalysis,
      ...(shouldRevealLegend
        ? { style: { ...spec.style, showLegend: true, version: 1 as const } }
        : {}),
    });
  };

  const changeAggregation = (field: string, aggregation: Aggregation) => {
    if (!onSpecPatch) return;
    const next = updateEncodingMetricAggregation(spec, field, aggregation);
    onSpecPatch({ encoding: next.encoding, metrics: next.metrics });
  };

  return (
    <div className="shrink-0 border-b border-[#e3e6ea] bg-white">
      {primaryRules.map((rule) => {
        const bindings = encoding[rule.channel]
          .filter((binding) => rule.roles.includes(binding.role))
          .slice(0, rule.max);
        const options = fieldOptions.filter((option) => rule.roles.includes(option.role));
        const labelId = shelfMessageId(spec, rule.channel);
        return (
          <ShelfRow
            key={rule.channel}
            label={labelId ? intl.formatMessage({ id: labelId }) : rule.label}
            bindings={bindings}
            allBindings={encoding[rule.channel]}
            max={rule.max}
            options={options}
            editable={editable}
            fieldLabel={fieldLabel}
            calculated={calculated}
            aggregationLabel={aggregationLabel}
            aggregationOptions={aggregationOptions}
            calculatedLabel={intl.formatMessage({ id: 'pages.dashboard.editor.encoding.calculated' })}
            defaultAggregationLabel={intl.formatMessage({ id: 'pages.dashboard.editor.aggregation.sum' })}
            addFieldLabel={intl.formatMessage({ id: 'pages.dashboard.editor.encoding.addField' })}
            emptyLabel={intl.formatMessage({ id: 'pages.dashboard.editor.encoding.dropOrSelect' })}
            removeAria={(field) => intl.formatMessage(
              { id: 'pages.dashboard.editor.encoding.removeField' },
              { field },
            )}
            onAdd={(field, role) => {
              const nextBinding: AnalysisEncodingBinding = {
                field,
                role,
                aggregation: role === 'metric' ? 'SUM' : undefined,
              };
              const current = encoding[rule.channel];
              const existingIndex = current.findIndex((binding) => binding.field === field);
              const existing = existingIndex >= 0 ? current[existingIndex] : undefined;
              let nextChannel: AnalysisEncodingBinding[];

              if (rule.max === 1) {
                nextChannel = [
                  existing ?? nextBinding,
                  ...current.filter((_, index) => index !== existingIndex),
                ];
              } else if (existing) {
                nextChannel = [
                  existing,
                  ...current.filter((_, index) => index !== existingIndex),
                ];
              } else {
                const activeCount = current.filter((binding) => rule.roles.includes(binding.role)).length;
                if (activeCount >= rule.max) return;
                nextChannel = [...current, nextBinding];
              }

              changeEncoding({ ...encoding, [rule.channel]: nextChannel });
            }}
            onRemove={(field) => changeEncoding({
              ...encoding,
              [rule.channel]: encoding[rule.channel].filter((binding) => binding.field !== field),
            })}
            onAggregationChange={changeAggregation}
          />
        );
      })}
    </div>
  );
}

function ShelfRow({
  label,
  bindings,
  allBindings,
  max,
  options,
  editable,
  fieldLabel,
  calculated,
  aggregationLabel,
  aggregationOptions,
  calculatedLabel,
  defaultAggregationLabel,
  addFieldLabel,
  emptyLabel,
  removeAria,
  onAdd,
  onRemove,
  onAggregationChange,
}: {
  label: string;
  bindings: AnalysisEncodingBinding[];
  allBindings: AnalysisEncodingBinding[];
  max: number;
  options: FieldOption[];
  editable: boolean;
  fieldLabel: Map<string, string>;
  calculated: Set<string>;
  aggregationLabel: Map<string, string>;
  aggregationOptions: Array<{ value: Aggregation; label: string }>;
  calculatedLabel: string;
  defaultAggregationLabel: string;
  addFieldLabel: string;
  emptyLabel: string;
  removeAria: (field: string) => string;
  onAdd: (field: string, role: DatasetFieldRole) => void;
  onRemove: (field: string) => void;
  onAggregationChange: (field: string, aggregation: Aggregation) => void;
}) {
  const [dragOver, setDragOver] = useState(false);
  const visible = new Set(bindings.map((binding) => binding.field));
  const allBound = new Set(allBindings.map((binding) => binding.field));
  const available = options.filter((option) => (
    max === 1 ? !visible.has(option.value) : !allBound.has(option.value)
  ));
  const full = bindings.length >= max;

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
    if (!editable) return;
    if (
      !event.dataTransfer.types.includes(FIELD_DRAG_MIME)
      && !event.dataTransfer.types.includes('text/plain')
    ) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
    setDragOver(true);
  };

  return (
    <div
      className={[
        'flex min-h-10 items-center gap-2 border-b border-[#f0f1f3] px-3 last:border-b-0 transition-colors',
        dragOver ? 'bg-[var(--yak-brand-color-soft)]' : 'bg-white',
      ].join(' ')}
      onDragEnter={handleDragOver}
      onDragOver={handleDragOver}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragOver(false);
      }}
      onDrop={(event) => {
        event.preventDefault();
        setDragOver(false);
        if (!editable) return;
        const payload = readChartFieldDragPayload(event);
        if (!payload || !options.some((option) => option.value === payload.field && option.role === payload.role)) return;
        onAdd(payload.field, payload.role);
      }}
    >
      <div className="w-12 shrink-0 text-[10px] font-medium text-[#667085]">{label}</div>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1.5 py-1">
        {bindings.map((binding) => {
          const metric = binding.role === 'metric';
          const isCalculated = calculated.has(binding.field);
          return (
            <div
              key={binding.field}
              className={[
                'flex h-7 max-w-[220px] items-center gap-1 rounded-[5px] px-2 text-[10px]',
                metric
                  ? 'bg-[#e4f5f0] text-[#2f7568]'
                  : 'bg-[#e8f0fd] text-[#486b9d]',
              ].join(' ')}
            >
              <span className="min-w-0 truncate font-medium">
                {fieldLabel.get(binding.field) ?? binding.field}
              </span>
              {metric ? (
                isCalculated ? (
                  <span className="shrink-0 text-[8px] opacity-65">{calculatedLabel}</span>
                ) : (
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: aggregationOptions.map((option) => ({
                        key: option.value,
                        label: option.label,
                      })),
                      onClick: ({ key, domEvent }) => {
                        domEvent.stopPropagation();
                        onAggregationChange(binding.field, key as Aggregation);
                      },
                    }}
                  >
                    <button
                      type="button"
                      disabled={!editable}
                      className="shrink-0 border-0 bg-transparent p-0 text-[8px] text-current opacity-65 hover:opacity-100 disabled:cursor-default"
                    >
                      {aggregationLabel.get(binding.aggregation ?? 'SUM') ?? defaultAggregationLabel}
                    </button>
                  </Dropdown>
                )
              ) : null}
              {editable ? (
                <button
                  type="button"
                  aria-label={removeAria(fieldLabel.get(binding.field) ?? binding.field)}
                  className="flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] text-current opacity-45 hover:bg-white/60 hover:opacity-90"
                  onClick={() => onRemove(binding.field)}
                >
                  <X size={9} />
                </button>
              ) : null}
            </div>
          );
        })}

        {editable && (max === 1 || !full) ? (
          <Select
            showSearch
            size="small"
            variant="borderless"
            value={undefined}
            className="min-w-[138px] max-w-[220px]"
            optionFilterProp="label"
            placeholder={bindings.length ? addFieldLabel : emptyLabel}
            options={available.map((option) => ({ label: option.label, value: option.value }))}
            suffixIcon={<Plus size={10} className="text-[#9aa1ab]" />}
            onChange={(field) => {
              const option = available.find((item) => item.value === field);
              if (option) onAdd(option.value, option.role);
            }}
          />
        ) : null}
      </div>
    </div>
  );
}
