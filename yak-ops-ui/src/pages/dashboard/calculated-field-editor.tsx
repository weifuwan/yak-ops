import { parseCalculatedFieldExpression } from '@/components/analysis/calculated-field';
import type {
  Aggregation,
  AnalysisCalculatedField,
  PublishedDataset,
} from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { Button, Input, Modal, Select } from 'antd';
import { Braces, Plus } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { AGGREGATION_OPTIONS } from './helpers';

const FUNCTION_SNIPPETS = [
  { label: 'ABS', value: 'ABS()' },
  { label: 'ROUND', value: 'ROUND(, 2)' },
  { label: 'COALESCE', value: 'COALESCE(, 0)' },
] as const;

export function CalculatedFieldEditor({
  open,
  field,
  dataset,
  existingFields,
  onCancel,
  onSave,
}: {
  open: boolean;
  field?: AnalysisCalculatedField;
  dataset: PublishedDataset;
  existingFields: AnalysisCalculatedField[];
  onCancel: () => void;
  onSave: (field: AnalysisCalculatedField) => void;
}) {
  const intl = useIntl();
  const [name, setName] = useState('');
  const [expression, setExpression] = useState('');
  const [aggregation, setAggregation] = useState<Aggregation>('SUM');
  const [sourceField, setSourceField] = useState<string>();
  const aggregationOptions = AGGREGATION_OPTIONS.map((item) => ({
    value: item.value,
    label: intl.formatMessage({ id: item.messageId }),
  }));

  useEffect(() => {
    if (!open) return;
    setName(field?.name ?? '');
    setExpression(field?.expression ?? '');
    setAggregation('SUM');
    setSourceField(dataset.fields.find((item) => item.dataType === 'number')?.key ?? dataset.fields[0]?.key);
  }, [dataset.fields, field, open]);

  const parsed = useMemo(() => {
    if (!expression.trim()) {
      return { error: intl.formatMessage({ id: 'pages.dashboard.editor.calculated.expressionRequired' }) } as const;
    }
    try {
      return { value: parseCalculatedFieldExpression(expression, dataset) } as const;
    } catch (error) {
      return {
        error: error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.dashboard.editor.calculated.invalidExpression' }),
      } as const;
    }
  }, [dataset, expression, intl]);

  const trimmedName = name.trim();
  const duplicateName = existingFields.some((item) => (
    item.id !== field?.id && item.name.trim().toLowerCase() === trimmedName.toLowerCase()
  ));
  const nameError = !trimmedName
    ? intl.formatMessage({ id: 'pages.dashboard.editor.calculated.nameRequired' })
    : duplicateName
      ? intl.formatMessage({ id: 'pages.dashboard.editor.calculated.nameDuplicate' })
      : '';
  const canSave = !nameError && 'value' in parsed;

  const eligibleFields = useMemo(() => dataset.fields.filter((item) => (
    aggregation === 'COUNT' || aggregation === 'COUNT_DISTINCT' || item.dataType === 'number'
  )), [aggregation, dataset.fields]);

  useEffect(() => {
    if (sourceField && eligibleFields.some((item) => item.key === sourceField)) return;
    setSourceField(eligibleFields[0]?.key);
  }, [eligibleFields, sourceField]);

  const append = (snippet: string) => {
    setExpression((current) => `${current}${current && !/\s$/.test(current) ? ' ' : ''}${snippet}`);
  };

  const insertAggregate = () => {
    if (!sourceField) return;
    append(`${aggregation}([${sourceField}])`);
  };

  return (
    <Modal
      open={open}
      width={620}
      title={intl.formatMessage({
        id: field
          ? 'pages.dashboard.editor.calculated.editTitle'
          : 'pages.dashboard.editor.calculated.createTitle',
      })}
      okText={intl.formatMessage({ id: 'pages.dashboard.editor.calculated.save' })}
      cancelText={intl.formatMessage({ id: 'pages.dashboard.editor.common.cancel' })}
      okButtonProps={{ disabled: !canSave }}
      onCancel={onCancel}
      onOk={() => {
        if (!canSave || !('value' in parsed)) return;
        onSave({
          id: field?.id ?? `cf-${Date.now()}-${Math.round(Math.random() * 10000)}`,
          name: trimmedName,
          expression: expression.trim(),
          ast: parsed.value.ast,
        });
      }}
    >
      <div className="space-y-4 pt-2">
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#475467]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.name' })}
          </div>
          <Input
            value={name}
            maxLength={40}
            placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.calculated.namePlaceholder' })}
            status={nameError ? 'error' : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          {nameError ? <div className="mt-1 text-[10px] text-[#b42318]">{nameError}</div> : null}
        </div>

        <div>
          <div className="mb-1.5 flex items-center justify-between gap-3">
            <span className="text-[11px] font-medium text-[#475467]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.expression' })}
            </span>
            <span className="text-[9px] text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.safeAst' })}
            </span>
          </div>
          <Input.TextArea
            value={expression}
            rows={6}
            maxLength={1000}
            spellCheck={false}
            className="font-mono text-[12px]"
            placeholder="SUM([sales]) / COUNT_DISTINCT([order_id])"
            status={'error' in parsed ? 'error' : undefined}
            onChange={(event) => setExpression(event.target.value)}
          />
          <div className={`mt-1 text-[10px] ${'error' in parsed ? 'text-[#b42318]' : 'text-[#667085]'}`}>
            {'error' in parsed
              ? parsed.error
              : intl.formatMessage(
                { id: 'pages.dashboard.editor.calculated.dependencies' },
                { count: parsed.value.dependencies.length },
              )}
          </div>
        </div>

        <div className="rounded-[9px] border border-[#e8eaee] bg-[#fafbfc] p-3">
          <div className="mb-2 flex items-center gap-1.5 text-[10px] font-semibold text-[#667085]">
            <Braces size={12} />
            {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.insertAggregateField' })}
          </div>
          <div className="grid grid-cols-[126px_1fr_auto] gap-2">
            <Select
              size="small"
              value={aggregation}
              options={aggregationOptions}
              onChange={(value: Aggregation) => setAggregation(value)}
            />
            <Select
              showSearch
              size="small"
              value={sourceField}
              optionFilterProp="label"
              options={eligibleFields.map((item) => ({ label: item.label, value: item.key }))}
              onChange={setSourceField}
            />
            <Button size="small" icon={<Plus size={12} />} disabled={!sourceField} onClick={insertAggregate}>
              {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.insert' })}
            </Button>
          </div>

          <div className="mt-3 flex flex-wrap gap-1.5">
            {[' + ', ' - ', ' * ', ' / ', '(', ')'].map((value) => (
              <Button key={value} size="small" onClick={() => append(value)}>{value.trim() || value}</Button>
            ))}
            {FUNCTION_SNIPPETS.map((item) => (
              <Button key={item.label} size="small" onClick={() => append(item.value)}>{item.label}</Button>
            ))}
          </div>
          <div className="mt-2 text-[9px] leading-4 text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.supportHint' })}
          </div>
        </div>

        <div className="rounded-[7px] bg-[#f7f8fa] px-3 py-2 text-[10px] leading-5 text-[#667085]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.calculated.example' })}
        </div>
      </div>
    </Modal>
  );
}
