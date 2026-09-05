import type { AnalysisSpec } from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { Button, Select, Tooltip } from 'antd';
import { Link2, Plus, Trash2 } from 'lucide-react';
import type {
  DashboardGlobalFilter,
  DashboardInteraction,
  DashboardWidget,
  PublishedDataset,
} from './model';

const createInteractionId = () => `interaction-${Date.now()}-${Math.round(Math.random() * 1000)}`;

export function DashboardInteractionEditor({
  widget,
  spec,
  dataset,
  filters,
  interactions,
  onChange,
}: {
  widget: DashboardWidget;
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  filters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  onChange: (interactions: DashboardInteraction[]) => void;
}) {
  const intl = useIntl();
  const rules = interactions.filter((item) => item.sourceWidgetId === widget.id);
  const sourceOptions = spec.dimensions.slice(0, 1).map((fieldId) => {
    const field = dataset.fields.find((item) => item.key === fieldId);
    return {
      label: field?.label || fieldId,
      value: fieldId,
    };
  });
  const targetOptions = filters
    .filter((filter) => filter.bindings.length > 0)
    .map((filter) => ({
      label: intl.formatMessage(
        { id: 'pages.dashboard.editor.interaction.filterTarget' },
        { name: filter.name, count: filter.bindings.length },
      ),
      value: filter.id,
    }));
  const nextPair = sourceOptions.flatMap((source) =>
    targetOptions.map((target) => ({
      sourceField: source.value,
      targetFilterId: target.value,
    })))
    .find((pair) => !rules.some((rule) =>
      rule.sourceField === pair.sourceField
      && rule.targetFilterId === pair.targetFilterId));

  const updateRule = (id: string, patch: Partial<DashboardInteraction>) => {
    onChange(interactions.map((item) => item.id === id ? { ...item, ...patch } : item));
  };

  const removeRule = (id: string) => {
    onChange(interactions.filter((item) => item.id !== id));
  };

  const addRule = () => {
    if (!nextPair) return;
    onChange([
      ...interactions,
      {
        id: createInteractionId(),
        event: 'select',
        sourceWidgetId: widget.id,
        sourceField: nextPair.sourceField,
        targetFilterId: nextPair.targetFilterId,
      },
    ]);
  };

  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
            <Link2 size={12} />
            {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.title' })}
          </div>
          <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.hint' })}
          </div>
        </div>
        <Button
          size="small"
          type="text"
          icon={<Plus size={12} />}
          disabled={!nextPair}
          onClick={addRule}
        >
          {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.add' })}
        </Button>
      </div>

      {!filters.length ? (
        <div className="mt-2 rounded-[5px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.createFilterFirst' })}
        </div>
      ) : !sourceOptions.length ? (
        <div className="mt-2 rounded-[5px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.noSourceDimension' })}
        </div>
      ) : !targetOptions.length ? (
        <div className="mt-2 rounded-[5px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.noTargetBinding' })}
        </div>
      ) : rules.length ? (
        <div className="mt-2 space-y-2">
          {rules.map((rule) => (
            <div key={rule.id} className="rounded-[6px] border border-[#edf0f3] bg-[#fafbfc] p-2">
              <div className="grid grid-cols-[1fr_1fr_28px] items-end gap-1.5">
                <div>
                  <div className="mb-1 text-[9px] text-[#98a2b3]">
                    {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.sourceDimension' })}
                  </div>
                  <Select
                    size="small"
                    className="w-full"
                    value={rule.sourceField}
                    options={sourceOptions}
                    onChange={(sourceField) => updateRule(rule.id, { sourceField })}
                  />
                </div>
                <div>
                  <div className="mb-1 text-[9px] text-[#98a2b3]">
                    {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.targetFilter' })}
                  </div>
                  <Select
                    size="small"
                    className="w-full"
                    value={rule.targetFilterId}
                    options={targetOptions}
                    onChange={(targetFilterId) => updateRule(rule.id, { targetFilterId })}
                  />
                </div>
                <Tooltip title={intl.formatMessage({ id: 'pages.dashboard.editor.interaction.delete' })}>
                  <Button
                    size="small"
                    type="text"
                    danger
                    className="w-7 px-0"
                    icon={<Trash2 size={12} />}
                    onClick={() => removeRule(rule.id)}
                  />
                </Tooltip>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-2 rounded-[5px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.interaction.empty' })}
        </div>
      )}
    </div>
  );
}
