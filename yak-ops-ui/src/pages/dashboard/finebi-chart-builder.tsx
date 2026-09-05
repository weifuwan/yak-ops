import {
  calculatedFieldKey,
  isCalculatedFieldKey,
} from '@/components/analysis/calculated-field';
import {
  ANALYSIS_ENCODING_RULES,
  applyAnalysisEncoding,
  changeAnalysisEncodingType,
  rebindAnalysisEncoding,
  resolveAnalysisEncoding,
} from '@/components/analysis/encoding';
import type {
  AnalysisEncoding,
  AnalysisEncodingBinding,
  DatasetFieldRole,
} from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { Button, Collapse, Select } from 'antd';
import {
  Calculator,
  ChevronDown,
  SlidersHorizontal,
  X,
} from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { ChartAnalysisConfig } from './config-analysis';
import { QueryControls } from './config-query';
import { readChartFieldDragPayload } from './chart-field-drag';
import { CHART_META, FIELD_DRAG_MIME, findDataset } from './helpers';
import type {
  AnalysisAsset,
  ChartType,
  DashboardInlineAnalysisSpec,
  DashboardWidget,
  PublishedDataset,
  SortDirection,
} from './model';

interface FieldOption {
  label: string;
  value: string;
  role: DatasetFieldRole;
}

const chartGlyphColors = {
  blue: '#5d78f6',
  cyan: '#20a5df',
  orange: '#f4a11a',
  green: '#64bea1',
  purple: '#7a61e8',
  slate: '#74859a',
};

function ChartTypeGlyph({ type }: { type: ChartType }) {
  const { blue, cyan, orange, green, purple, slate } = chartGlyphColors;
  const common = {
    width: 30,
    height: 24,
    viewBox: '0 0 30 24',
    className: 'block overflow-visible',
    'aria-hidden': true,
  } as const;

  if (type === 'metric') {
    return (
      <svg {...common}>
        <text x="3" y="16" fill={blue} fontSize="12" fontWeight="650">123</text>
        <rect x="3" y="20" width="22" height="2" rx="1" fill={green} />
      </svg>
    );
  }
  if (type === 'bar') {
    return (
      <svg {...common}>
        <rect x="3" y="11" width="5" height="10" rx="1" fill={blue} />
        <rect x="11" y="6" width="5" height="15" rx="1" fill={cyan} />
        <rect x="19" y="2" width="5" height="19" rx="1" fill={blue} />
      </svg>
    );
  }
  if (type === 'stackedBar') {
    return (
      <svg {...common}>
        <rect x="3" y="12" width="5" height="9" rx="1" fill={blue} />
        <rect x="3" y="7" width="5" height="4" rx="1" fill={cyan} />
        <rect x="11" y="8" width="5" height="13" rx="1" fill={blue} />
        <rect x="11" y="3" width="5" height="4" rx="1" fill={orange} />
        <rect x="19" y="10" width="5" height="11" rx="1" fill={cyan} />
        <rect x="19" y="4" width="5" height="5" rx="1" fill={green} />
      </svg>
    );
  }
  if (type === 'line') {
    return (
      <svg {...common}>
        <polyline points="2,18 8,12 13,15 20,6 27,10" fill="none" stroke={blue} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="8" cy="12" r="1.6" fill={cyan} />
        <circle cx="20" cy="6" r="1.6" fill={orange} />
      </svg>
    );
  }
  if (type === 'area') {
    return (
      <svg {...common}>
        <path d="M2 19 L7 13 L12 15 L18 7 L24 10 L28 5 L28 21 L2 21 Z" fill={green} opacity="0.3" />
        <polyline points="2,19 7,13 12,15 18,7 24,10 28,5" fill="none" stroke={cyan} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (type === 'pie') {
    return (
      <svg {...common}>
        <circle cx="15" cy="12" r="9" fill={blue} opacity="0.92" />
        <path d="M15 12 L15 3 A9 9 0 0 1 23.2 15.7 Z" fill={orange} />
        <path d="M15 12 L23.2 15.7 A9 9 0 0 1 10.5 19.8 Z" fill={green} />
      </svg>
    );
  }
  if (type === 'scatter') {
    return (
      <svg {...common}>
        <circle cx="5" cy="17" r="2" fill={blue} />
        <circle cx="10" cy="11" r="2.4" fill={cyan} />
        <circle cx="15" cy="15" r="1.7" fill={orange} />
        <circle cx="19" cy="7" r="2.2" fill={purple} />
        <circle cx="25" cy="4" r="1.7" fill={green} />
        <circle cx="24" cy="15" r="2.4" fill={blue} opacity="0.78" />
      </svg>
    );
  }
  if (type === 'radar') {
    return (
      <svg {...common}>
        <polygon points="15,2 25,9 21,21 9,21 5,9" fill="none" stroke={slate} strokeWidth="1" opacity="0.55" />
        <polygon points="15,5 22,10 19,18 11,19 8,10" fill={purple} opacity="0.24" stroke={purple} strokeWidth="1.6" />
        <circle cx="15" cy="5" r="1.3" fill={orange} />
      </svg>
    );
  }
  if (type === 'funnel') {
    return (
      <svg {...common}>
        <path d="M3 3 H27 L23 8 H7 Z" fill={blue} />
        <path d="M7 9 H23 L20 14 H10 Z" fill={cyan} />
        <path d="M10 15 H20 L17 21 H13 Z" fill={orange} />
      </svg>
    );
  }
  if (type === 'treemap') {
    return (
      <svg {...common}>
        <rect x="2" y="2" width="13" height="9" rx="1" fill={blue} />
        <rect x="16" y="2" width="12" height="9" rx="1" fill={orange} />
        <rect x="2" y="12" width="8" height="10" rx="1" fill={green} />
        <rect x="11" y="12" width="10" height="10" rx="1" fill={purple} />
        <rect x="22" y="12" width="6" height="10" rx="1" fill={cyan} />
      </svg>
    );
  }
  return (
    <svg {...common}>
      <rect x="2" y="3" width="26" height="18" rx="1.5" fill="none" stroke={slate} strokeWidth="1" opacity="0.55" />
      <rect x="3" y="4" width="8" height="5" fill={blue} opacity="0.9" />
      <rect x="12" y="4" width="7" height="5" fill={cyan} opacity="0.85" />
      <rect x="20" y="4" width="7" height="5" fill={green} opacity="0.9" />
      <path d="M3 11 H27 M3 16 H27 M11.5 4 V21 M19.5 4 V21" stroke={slate} strokeWidth="0.8" opacity="0.55" />
    </svg>
  );
}

export function FineBiChartBuilderPanel({
  widget,
  datasets,
  analyses,
  updateInlineAnalysis,
  detachAnalysis,
}: {
  widget: DashboardWidget;
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  updateInlineAnalysis: (patch: Partial<DashboardInlineAnalysisSpec>) => void;
  detachAnalysis: () => void;
}) {
  const intl = useIntl();
  if (widget.analysisId) {
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    const dataset = analysis
      ? datasets.find((item) => item.id === analysis.datasetId)
      : undefined;

    return (
      <section className="flex w-[272px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white 2xl:w-[288px]">
        <div className="p-3.5">
          <div className="rounded-[7px] bg-[#f6f7f8] p-3">
            <div className="truncate text-[11px] font-semibold text-[#344054]">
              {analysis?.name ?? intl.formatMessage({ id: 'pages.dashboard.editor.historicalChart' })}
            </div>
            <div className="mt-1 truncate text-[9px] text-[#98a2b3]">
              {dataset?.name ?? intl.formatMessage({ id: 'pages.dashboard.editor.builder.dataUnavailable' })}
            </div>
          </div>
          <div className="mt-3 text-[10px] leading-5 text-[#667085]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.builder.sharedReadonly' })}
          </div>
          <Button
            block
            size="small"
            className="mt-4 !h-8 !rounded-[7px]"
            disabled={!analysis}
            onClick={detachAnalysis}
          >
            {intl.formatMessage({ id: 'pages.dashboard.editor.builder.copyEditable' })}
          </Button>
        </div>
      </section>
    );
  }

  const spec = widget.inlineAnalysis;
  if (!spec) return null;
  const dataset = findDataset(datasets, spec.datasetId);
  if (!dataset) return null;

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
    })),
  ];
  const filterOptions = dataset.fields.map((field) => ({ label: field.label, value: field.key }));
  const selectedFields = new Set([
    ...spec.dimensions,
    ...spec.metrics.map((metric) => metric.field),
  ]);
  const sortOptions = dataset.fields
    .filter((field) => selectedFields.has(field.key))
    .map((field) => ({ label: field.label, value: field.key }));
  const encoding = resolveAnalysisEncoding(spec);
  const secondaryRules = ANALYSIS_ENCODING_RULES[spec.type]
    .filter((rule) => rule.channel !== 'category' && rule.channel !== 'value');

  const changeType = (type: ChartType) => {
    const changed = changeAnalysisEncodingType(spec, type);
    const next = rebindAnalysisEncoding(changed, dataset);
    updateInlineAnalysis({
      type,
      encoding: next.encoding,
      dimensions: next.dimensions,
      metrics: next.metrics,
      sort: undefined,
      style: { ...spec.style, version: 1 },
      analysis: spec.analysis,
      limit: type === 'table' ? 200 : 500,
    });
  };

  const changeEncoding = (nextEncoding: AnalysisEncoding) => {
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

    updateInlineAnalysis({
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

  return (
    <section className="flex w-[272px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white 2xl:w-[288px]">
      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3.5">
        <div className="text-[11px] font-semibold text-[#161823]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.builder.chartType' })}
        </div>
        <div className="mt-2.5 grid grid-cols-5 gap-x-1 gap-y-1.5">
          {(Object.keys(CHART_META) as ChartType[]).map((type) => {
            const active = spec.type === type;
            const meta = CHART_META[type];
            return (
              <button
                key={type}
                type="button"
                aria-pressed={active}
                title={`${meta.label} · ${meta.description}`}
                onClick={() => changeType(type)}
                className={[
                  'group flex min-w-0 flex-col items-center justify-center gap-1.5 rounded-[6px] border px-0.5 py-2.5 transition-[background-color,border-color,transform]',
                  active
                    ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color-soft)]'
                    : 'border-transparent bg-white hover:border-[#e6e9ee] hover:bg-[#f7f8fa]',
                ].join(' ')}
              >
                <span className="flex h-6 items-center justify-center transition-transform group-hover:-translate-y-0.5">
                  <ChartTypeGlyph type={type} />
                </span>
                <span
                  className={[
                    'w-full truncate text-center text-[10px] leading-4 text-[#161823]',
                    active ? 'font-semibold' : 'font-medium',
                  ].join(' ')}
                >
                  {meta.label}
                </span>
              </button>
            );
          })}
        </div>

        {secondaryRules.length ? (
          <div className="mt-4 border-t border-[#eceef1] pt-3.5">
            <div className="mb-2 text-[10px] font-semibold text-[#667085]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.builder.visualProperties' })}
            </div>
            <div className="space-y-1.5">
              {secondaryRules.map((rule) => (
                <SecondaryEncodingSlot
                  key={rule.channel}
                  label={rule.label}
                  max={rule.max}
                  roles={rule.roles}
                  bindings={encoding[rule.channel]}
                  options={fieldOptions}
                  addFieldLabel={intl.formatMessage({ id: 'pages.dashboard.editor.builder.addField' })}
                  dropFieldLabel={intl.formatMessage({ id: 'pages.dashboard.editor.builder.dropField' })}
                  removeAria={(field) => intl.formatMessage(
                    { id: 'pages.dashboard.editor.builder.removeField' },
                    { field },
                  )}
                  onChange={(bindings) => changeEncoding({
                    ...encoding,
                    [rule.channel]: bindings,
                  })}
                />
              ))}
            </div>
          </div>
        ) : null}

        <Collapse
          ghost
          className="chart-editor-more mt-4 border-t border-[#eceef1]"
          expandIconPosition="end"
          expandIcon={({ isActive }) => (
            <ChevronDown
              size={13}
              className={isActive ? 'rotate-180 text-[#667085]' : 'text-[#a0a6af]'}
            />
          )}
          items={[
            {
              key: 'analysis',
              label: (
                <span className="flex items-center gap-1.5 text-[10px] font-medium text-[#667085]">
                  <Calculator size={11} />
                  {intl.formatMessage({ id: 'pages.dashboard.editor.builder.analysisSettings' })}
                </span>
              ),
              children: (
                <ChartAnalysisConfig
                  spec={spec}
                  dataset={dataset}
                  onChange={(analysis) => updateInlineAnalysis({ analysis })}
                />
              ),
            },
            {
              key: 'query',
              label: (
                <span className="flex items-center gap-1.5 text-[10px] font-medium text-[#667085]">
                  <SlidersHorizontal size={11} />
                  {intl.formatMessage({ id: 'pages.dashboard.editor.builder.sortFilter' })}
                </span>
              ),
              children: (
                <QueryControls
                  sortOptions={sortOptions}
                  filterOptions={filterOptions}
                  sortField={spec.sort?.field}
                  sortDirection={spec.sort?.direction ?? 'asc'}
                  filters={spec.filters}
                  onSortField={(field?: string) => updateInlineAnalysis({
                    sort: field
                      ? { field, direction: spec.sort?.direction ?? 'asc' }
                      : undefined,
                  })}
                  onSortDirection={(direction: SortDirection) =>
                    spec.sort && updateInlineAnalysis({ sort: { ...spec.sort, direction } })}
                  onFiltersChange={(filters) => updateInlineAnalysis({ filters })}
                />
              ),
            },
          ]}
        />
      </div>
    </section>
  );
}

function SecondaryEncodingSlot({
  label,
  max,
  roles,
  bindings,
  options,
  addFieldLabel,
  dropFieldLabel,
  removeAria,
  onChange,
}: {
  label: string;
  max: number;
  roles: DatasetFieldRole[];
  bindings: AnalysisEncodingBinding[];
  options: FieldOption[];
  addFieldLabel: string;
  dropFieldLabel: string;
  removeAria: (field: string) => string;
  onChange: (bindings: AnalysisEncodingBinding[]) => void;
}) {
  const [dragOver, setDragOver] = useState(false);
  const activeBindings = bindings.filter((binding) => roles.includes(binding.role)).slice(0, max);
  const fieldLabel = new Map(options.map((option) => [option.value, option.label]));
  const active = new Set(activeBindings.map((binding) => binding.field));
  const allBound = new Set(bindings.map((binding) => binding.field));
  const available = options.filter((option) => roles.includes(option.role) && (
    max === 1 ? !active.has(option.value) : !allBound.has(option.value)
  ));
  const full = activeBindings.length >= max;

  const addField = (field: string, role: DatasetFieldRole) => {
    const nextBinding: AnalysisEncodingBinding = {
      field,
      role,
      aggregation: role === 'metric' ? 'SUM' : undefined,
    };
    const existingIndex = bindings.findIndex((binding) => binding.field === field);
    const existing = existingIndex >= 0 ? bindings[existingIndex] : undefined;
    if (max === 1) {
      onChange([
        existing ?? nextBinding,
        ...bindings.filter((_, index) => index !== existingIndex),
      ]);
      return;
    }
    if (existing) {
      onChange([existing, ...bindings.filter((_, index) => index !== existingIndex)]);
      return;
    }
    if (activeBindings.length < max) onChange([...bindings, nextBinding]);
  };

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
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
        'flex min-h-9 items-center gap-2 rounded-[6px] px-2 transition-colors',
        dragOver ? 'bg-[var(--yak-brand-color-soft)]' : 'bg-[#f7f8fa]',
      ].join(' ')}
      onDragEnter={handleDragOver}
      onDragOver={handleDragOver}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragOver(false);
      }}
      onDrop={(event) => {
        event.preventDefault();
        setDragOver(false);
        const payload = readChartFieldDragPayload(event);
        if (!payload || !roles.includes(payload.role)) return;
        if (!options.some((option) => option.value === payload.field && option.role === payload.role)) return;
        addField(payload.field, payload.role);
      }}
    >
      <span className="w-10 shrink-0 text-[9px] text-[#667085]">{label}</span>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1 py-1">
        {activeBindings.map((binding) => {
          const labelText = fieldLabel.get(binding.field) ?? binding.field;
          return (
            <span
              key={binding.field}
              className="flex h-6 max-w-[150px] items-center gap-1 rounded-[4px] bg-white px-1.5 text-[9px] text-[#475467]"
            >
              <span className="min-w-0 truncate">{labelText}</span>
              <button
                type="button"
                className="flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] text-[#a0a6af] hover:bg-[#f0f1f3] hover:text-[#667085]"
                onClick={() => onChange(bindings.filter((item) => item.field !== binding.field))}
                aria-label={removeAria(labelText)}
              >
                <X size={8} />
              </button>
            </span>
          );
        })}
        {(max === 1 || !full) ? (
          <Select
            showSearch
            size="small"
            variant="borderless"
            value={undefined}
            className="min-w-[106px] flex-1"
            optionFilterProp="label"
            placeholder={activeBindings.length ? addFieldLabel : dropFieldLabel}
            options={available.map((option) => ({ label: option.label, value: option.value }))}
            onChange={(field) => {
              const option = available.find((item) => item.value === field);
              if (option) addField(option.value, option.role);
            }}
          />
        ) : null}
      </div>
    </div>
  );
}
