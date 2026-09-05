import {
  calculatedFieldKey,
  isCalculatedFieldKey,
} from '@/components/analysis/calculated-field';
import {
  analysisEncodingFieldKeys,
  applyAnalysisEncoding,
  resolveAnalysisEncoding,
} from '@/components/analysis/encoding';
import type {
  AnalysisCalculatedField,
  AnalysisSpec,
} from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { Button, Input, Popconfirm } from 'antd';
import {
  Braces,
  Database,
  GripVertical,
  Hash,
  Pencil,
  Plus,
  Search,
  Trash2,
  Type,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { CalculatedFieldEditor } from './calculated-field-editor';
import { writeChartFieldDragPayload } from './chart-field-drag';
import type { DatasetField, PublishedDataset } from './model';

const FIELD_TYPE_MESSAGE_IDS: Record<DatasetField['dataType'], string> = {
  string: 'pages.dashboard.editor.fields.type.string',
  number: 'pages.dashboard.editor.fields.type.number',
  date: 'pages.dashboard.editor.fields.type.date',
  datetime: 'pages.dashboard.editor.fields.type.datetime',
  boolean: 'pages.dashboard.editor.fields.type.boolean',
  unknown: 'pages.dashboard.editor.fields.type.unknown',
};

export function ChartFieldPanel({
  dataset,
  spec,
  editable,
  onSpecPatch,
}: {
  dataset?: PublishedDataset;
  spec?: AnalysisSpec;
  editable: boolean;
  onSpecPatch?: (patch: Partial<AnalysisSpec>) => void;
}) {
  const intl = useIntl();
  const [keyword, setKeyword] = useState('');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingField, setEditingField] = useState<AnalysisCalculatedField>();
  const normalizedKeyword = keyword.trim().toLowerCase();
  const fields = useMemo(() => {
    if (!dataset) return [];
    if (!normalizedKeyword) return dataset.fields;
    return dataset.fields.filter((field) => (
      field.label.toLowerCase().includes(normalizedKeyword)
      || field.key.toLowerCase().includes(normalizedKeyword)
      || field.physicalName.toLowerCase().includes(normalizedKeyword)
    ));
  }, [dataset, normalizedKeyword]);
  const dimensions = fields.filter((field) => field.role === 'dimension');
  const metrics = fields.filter((field) => field.role === 'metric');
  const calculatedFields = spec?.analysis?.calculatedFields ?? [];
  const visibleCalculatedFields = normalizedKeyword
    ? calculatedFields.filter((field) => (
      field.name.toLowerCase().includes(normalizedKeyword)
      || field.expression.toLowerCase().includes(normalizedKeyword)
    ))
    : calculatedFields;
  const encodedFields = useMemo(
    () => spec ? analysisEncodingFieldKeys(spec) : new Set<string>(),
    [spec],
  );

  const saveCalculatedField = (field: AnalysisCalculatedField) => {
    if (!spec || !onSpecPatch) return;
    const current = spec.analysis?.calculatedFields ?? [];
    const exists = current.some((item) => item.id === field.id);
    const next = exists
      ? current.map((item) => item.id === field.id ? field : item)
      : [...current, field];
    onSpecPatch({
      analysis: {
        ...spec.analysis,
        version: 1,
        calculatedFields: next,
      },
    });
    setEditorOpen(false);
    setEditingField(undefined);
  };

  const deleteCalculatedField = (field: AnalysisCalculatedField) => {
    if (!spec || !onSpecPatch) return;
    const key = calculatedFieldKey(field);
    const encoding = resolveAnalysisEncoding(spec);
    const cleanedEncoding = {
      ...encoding,
      category: encoding.category.filter((item) => item.field !== key),
      value: encoding.value.filter((item) => item.field !== key),
      color: encoding.color.filter((item) => item.field !== key),
      size: encoding.size.filter((item) => item.field !== key),
      label: encoding.label.filter((item) => item.field !== key),
      detail: encoding.detail.filter((item) => item.field !== key),
      tooltip: encoding.tooltip.filter((item) => item.field !== key),
    };
    const metricConfig = { ...(spec.analysis?.metrics ?? {}) };
    delete metricConfig[key];
    const calculated = calculatedFields.filter((item) => item.id !== field.id);
    const provisionalAnalysis = {
      ...spec.analysis,
      version: 1 as const,
      metrics: metricConfig,
      calculatedFields: calculated,
    };
    const rebound = applyAnalysisEncoding({ ...spec, analysis: provisionalAnalysis }, cleanedEncoding);
    const topN = provisionalAnalysis.topN;
    const physicalFallback = rebound.metrics.find((metric) => !isCalculatedFieldKey(rebound, metric.field));
    const nextTopN = topN?.metricField === key
      ? physicalFallback
        ? { ...topN, metricField: physicalFallback.field }
        : { ...topN, enabled: false }
      : topN;
    onSpecPatch({
      encoding: rebound.encoding,
      dimensions: rebound.dimensions,
      metrics: rebound.metrics,
      sort: spec.sort?.field === key ? undefined : spec.sort,
      analysis: { ...provisionalAnalysis, topN: nextTopN },
    });
  };

  return (
    <section className="flex w-[244px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white">
      <div className="flex h-14 shrink-0 items-center border-b border-[#eceef1] px-3.5">
        <div className="min-w-0">
          <div className="text-[13px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.fields.title' })}
          </div>
          <div className="mt-0.5 flex items-center gap-1 text-[10px] font-medium text-[#7a818c]">
            <Database size={10} className="shrink-0" />
            <span className="truncate">
              {dataset?.name ?? intl.formatMessage({ id: 'pages.dashboard.editor.fields.sourceUnavailable' })}
            </span>
          </div>
        </div>
      </div>

      <div className="shrink-0 border-b border-[#f0f1f3] p-3">
        <Input
          allowClear
          size="small"
          value={keyword}
          prefix={<Search size={13} className="text-[#7a818c]" />}
          placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.fields.search' })}
          className="!h-9 !rounded-[7px] !text-[11px]"
          onChange={(event) => setKeyword(event.target.value)}
        />
        <div className="mt-2 flex items-center justify-between gap-2">
          <div className="text-[10px] leading-4 text-[#8b929c]">
            {intl.formatMessage({
              id: editable
                ? 'pages.dashboard.editor.fields.dragHint'
                : 'pages.dashboard.editor.fields.copyHint',
            })}
          </div>
          {editable && dataset && spec && onSpecPatch ? (
            <Button
              type="text"
              size="small"
              className="!h-7 !px-1.5 !text-[10px]"
              icon={<Plus size={11} />}
              onClick={() => {
                setEditingField(undefined);
                setEditorOpen(true);
              }}
            >
              {intl.formatMessage({ id: 'pages.dashboard.editor.fields.calculated' })}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2.5 py-3.5">
        {!dataset ? (
          <div className="px-2 py-6 text-center text-[11px] text-[#8b929c]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.fields.empty' })}
          </div>
        ) : (
          <div className="space-y-4">
            <FieldGroup
              title={intl.formatMessage({ id: 'pages.dashboard.editor.fields.dimension' })}
              role="dimension"
              fields={dimensions}
              encodedFields={encodedFields}
              editable={editable}
            />
            <FieldGroup
              title={intl.formatMessage({ id: 'pages.dashboard.editor.fields.metric' })}
              role="metric"
              fields={metrics}
              encodedFields={encodedFields}
              editable={editable}
            />
            {calculatedFields.length ? (
              <CalculatedFieldGroup
                fields={visibleCalculatedFields}
                total={calculatedFields.length}
                encodedFields={encodedFields}
                editable={editable}
                onEdit={(field) => {
                  setEditingField(field);
                  setEditorOpen(true);
                }}
                onDelete={deleteCalculatedField}
              />
            ) : null}
          </div>
        )}
      </div>

      {dataset && spec ? (
        <CalculatedFieldEditor
          open={editorOpen}
          field={editingField}
          dataset={dataset}
          existingFields={calculatedFields}
          onCancel={() => {
            setEditorOpen(false);
            setEditingField(undefined);
          }}
          onSave={saveCalculatedField}
        />
      ) : null}
    </section>
  );
}

function FieldGroup({
  title,
  role,
  fields,
  encodedFields,
  editable,
}: {
  title: string;
  role: DatasetField['role'];
  fields: DatasetField[];
  encodedFields: Set<string>;
  editable: boolean;
}) {
  const intl = useIntl();
  const dimension = role === 'dimension';

  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between px-1.5">
        <div className="flex items-center gap-1.5">
          <span
            className={[
              'flex h-5 w-5 items-center justify-center rounded-[5px] border',
              dimension
                ? 'border-[#dce6ff] bg-[#eef3ff] text-[#5674e8]'
                : 'border-[#d5eeee] bg-[#edf9f9] text-[#169c9c]',
            ].join(' ')}
          >
            {dimension ? <Type size={10} /> : <Hash size={10} />}
          </span>
          <span className="text-[11px] font-semibold text-[#344054]">{title}</span>
        </div>
        <span className="rounded-[4px] bg-[#f2f4f7] px-1.5 py-0.5 text-[9px] font-medium tabular-nums text-[#667085]">
          {fields.length}
        </span>
      </div>
      <div className="space-y-0.5">
        {fields.map((field) => {
          const selected = encodedFields.has(field.key);
          const isDimension = field.role === 'dimension';
          return (
            <div
              key={field.key}
              draggable={editable}
              title={editable
                ? intl.formatMessage(
                  { id: 'pages.dashboard.editor.fields.dragField' },
                  { field: field.label },
                )
                : field.label}
              className={[
                'group flex h-9 items-center gap-2 rounded-[6px] px-1.5 text-[11px] transition-colors',
                editable ? 'cursor-grab active:cursor-grabbing' : 'cursor-default',
                selected
                  ? isDimension
                    ? 'bg-[#f4f7ff] font-medium text-[#263244]'
                    : 'bg-[#f0fafa] font-medium text-[#263244]'
                  : 'text-[#344054] hover:bg-[#f7f8fa]',
              ].join(' ')}
              onDragStart={(event) => {
                if (!editable) return;
                writeChartFieldDragPayload(event, { field: field.key, role: field.role });
              }}
            >
              <GripVertical
                size={12}
                className={editable ? 'shrink-0 text-[#c2c6cc] group-hover:text-[#8b929c]' : 'shrink-0 text-[#d9dde2]'}
              />
              <span
                className={[
                  'flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-[5px] border',
                  isDimension
                    ? 'border-[#dce6ff] bg-[#eef3ff] text-[#5674e8]'
                    : 'border-[#d5eeee] bg-[#edf9f9] text-[#169c9c]',
                ].join(' ')}
              >
                {isDimension ? <Type size={11} /> : <Hash size={11} />}
              </span>
              <span className="min-w-0 flex-1 truncate font-medium">{field.label}</span>
              <span className="shrink-0 text-[9px] text-[#8e95a0]">
                {intl.formatMessage({ id: FIELD_TYPE_MESSAGE_IDS[field.dataType] })}
              </span>
            </div>
          );
        })}
        {!fields.length ? (
          <div className="px-1.5 py-2.5 text-[10px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.fields.noMatch' })}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function CalculatedFieldGroup({
  fields,
  total,
  encodedFields,
  editable,
  onEdit,
  onDelete,
}: {
  fields: AnalysisCalculatedField[];
  total: number;
  encodedFields: Set<string>;
  editable: boolean;
  onEdit: (field: AnalysisCalculatedField) => void;
  onDelete: (field: AnalysisCalculatedField) => void;
}) {
  const intl = useIntl();
  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between px-1.5">
        <div className="flex items-center gap-1.5">
          <span className="flex h-5 w-5 items-center justify-center rounded-[5px] border border-[#fee1c7] bg-[#fff6ed] text-[#f79009]">
            <Braces size={10} />
          </span>
          <span className="text-[11px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.fields.calculated' })}
          </span>
        </div>
        <span className="rounded-[4px] bg-[#f2f4f7] px-1.5 py-0.5 text-[9px] font-medium tabular-nums text-[#667085]">
          {total}
        </span>
      </div>
      <div className="space-y-0.5">
        {fields.map((field) => {
          const key = calculatedFieldKey(field);
          const selected = encodedFields.has(key);
          return (
            <div
              key={field.id}
              draggable={editable}
              title={`${field.name} · ${field.expression}`}
              className={[
                'group flex min-h-9 items-center gap-2 rounded-[6px] px-1.5 text-[11px] transition-colors',
                editable ? 'cursor-grab active:cursor-grabbing' : 'cursor-default',
                selected
                  ? 'bg-[#fff8f0] font-medium text-[#263244]'
                  : 'text-[#344054] hover:bg-[#f7f8fa]',
              ].join(' ')}
              onDragStart={(event) => {
                if (!editable) return;
                writeChartFieldDragPayload(event, { field: key, role: 'metric' });
              }}
            >
              <GripVertical size={12} className="shrink-0 text-[#c2c6cc] group-hover:text-[#8b929c]" />
              <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-[5px] border border-[#fee1c7] bg-[#fff6ed] text-[#f79009]">
                <Braces size={11} />
              </span>
              <span className="min-w-0 flex-1 truncate font-medium">{field.name}</span>
              <span className="shrink-0 text-[9px] text-[#8e95a0]">
                {intl.formatMessage({ id: 'pages.dashboard.editor.fields.calculatedBadge' })}
              </span>
              {editable ? (
                <div className="hidden shrink-0 items-center gap-0.5 group-hover:flex">
                  <button
                    type="button"
                    className="flex h-5 w-5 items-center justify-center rounded-[4px] text-[#8e95a0] hover:bg-[#e9ebef] hover:text-[#475467]"
                    aria-label={intl.formatMessage(
                      { id: 'pages.dashboard.editor.fields.editCalculatedAria' },
                      { field: field.name },
                    )}
                    onClick={() => onEdit(field)}
                  >
                    <Pencil size={9} />
                  </button>
                  <Popconfirm
                    title={intl.formatMessage({ id: 'pages.dashboard.editor.fields.deleteCalculatedTitle' })}
                    description={intl.formatMessage({ id: 'pages.dashboard.editor.fields.deleteCalculatedDescription' })}
                    okText={intl.formatMessage({ id: 'pages.dashboard.editor.common.delete' })}
                    cancelText={intl.formatMessage({ id: 'pages.dashboard.editor.common.cancel' })}
                    onConfirm={() => onDelete(field)}
                  >
                    <button
                      type="button"
                      className="flex h-5 w-5 items-center justify-center rounded-[4px] text-[#8e95a0] hover:bg-[#e9ebef] hover:text-[#b42318]"
                      aria-label={intl.formatMessage(
                        { id: 'pages.dashboard.editor.fields.deleteCalculatedAria' },
                        { field: field.name },
                      )}
                    >
                      <Trash2 size={9} />
                    </button>
                  </Popconfirm>
                </div>
              ) : null}
            </div>
          );
        })}
        {!fields.length ? (
          <div className="px-1.5 py-2.5 text-[10px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.fields.noCalculatedMatch' })}
          </div>
        ) : null}
      </div>
    </div>
  );
}
