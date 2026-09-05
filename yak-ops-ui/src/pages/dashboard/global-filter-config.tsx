import { useIntl } from '@umijs/max';
import { Button, DatePicker, Drawer, Empty, Input, Select, Switch, Tooltip, message } from 'antd';
import dayjs from 'dayjs';
import { CalendarDays, Plus, SlidersHorizontal, Trash2 } from 'lucide-react';
import { isDateFieldType, isDateFilter, resolveWidgetDataset } from './filter-utils';
import type {
  AnalysisAsset,
  DashboardGlobalFilter,
  DashboardWidget,
  FilterOperator,
  PublishedDataset,
} from './model';

const createId = (prefix: string) => `${prefix}-${Date.now()}-${Math.round(Math.random() * 1000)}`;

export function DashboardGlobalFilterConfig({
  open,
  filters,
  widgets,
  datasets,
  analyses,
  onChange,
  onClose,
}: {
  open: boolean;
  filters: DashboardGlobalFilter[];
  widgets: DashboardWidget[];
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  onChange: (filters: DashboardGlobalFilter[]) => void;
  onClose: () => void;
}) {
  const intl = useIntl();
  const textOperators: Array<{ label: string; value: FilterOperator }> = [
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.eq' }), value: 'eq' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.neq' }), value: 'neq' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.contains' }), value: 'contains' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.gt' }), value: 'gt' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.gte' }), value: 'gte' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.lt' }), value: 'lt' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.lte' }), value: 'lte' },
  ];
  const dateOperators: Array<{ label: string; value: FilterOperator }> = [
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.eq' }), value: 'eq' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.operator.neq' }), value: 'neq' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.after' }), value: 'gt' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.afterOrEqual' }), value: 'gte' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.before' }), value: 'lt' },
    { label: intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.beforeOrEqual' }), value: 'lte' },
  ];
  const widgetContext = widgets.map((widget) => ({
    widget,
    dataset: resolveWidgetDataset(widget, datasets, analyses),
  }));

  const patchFilter = (filterId: string, patch: Partial<DashboardGlobalFilter>) => {
    onChange(filters.map((filter) => filter.id === filterId ? { ...filter, ...patch } : filter));
  };

  const removeFilter = (filterId: string) => {
    onChange(filters.filter((filter) => filter.id !== filterId));
  };

  const addTextFilter = () => {
    const context = widgetContext.find((item) => item.dataset?.fields.length);
    const field = context?.dataset?.fields[0];
    const filter: DashboardGlobalFilter = {
      id: createId('filter'),
      name: field?.label || intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.defaultName' }),
      operator: 'eq',
      bindings: context && field ? [{ widgetId: context.widget.id, field: field.key }] : [],
    };
    onChange([...filters, filter]);
  };

  const addDateFilter = () => {
    const context = widgetContext.find((item) =>
      item.dataset?.fields.some((field) => isDateFieldType(field.dataType)));
    const field = context?.dataset?.fields.find((item) => isDateFieldType(item.dataType));
    if (!context || !field) {
      message.info(intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.noDateField' }));
      return;
    }
    const filter: DashboardGlobalFilter = {
      id: createId('date-filter'),
      name: field.label || intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.date' }),
      operator: 'eq',
      bindings: [{ widgetId: context.widget.id, field: field.key }],
    };
    onChange([...filters, filter]);
  };

  return (
    <Drawer
      title={(
        <div>
          <div className="text-[13px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.globalTitle' })}
          </div>
          <div className="mt-0.5 text-[10px] font-normal text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.mappingHint' })}
          </div>
        </div>
      )}
      width={500}
      open={open}
      onClose={onClose}
      extra={(
        <div className="flex items-center gap-1.5">
          <Button size="small" icon={<Plus size={12} />} onClick={addTextFilter}>
            {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.filter' })}
          </Button>
          <Button size="small" icon={<CalendarDays size={12} />} onClick={addDateFilter}>
            {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.date' })}
          </Button>
        </div>
      )}
    >
      {!filters.length ? (
        <div className="flex min-h-[360px] items-center justify-center">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={(
              <div className="text-[11px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.configEmpty' })}
              </div>
            )}
          />
        </div>
      ) : (
        <div className="space-y-3">
          {filters.map((filter, index) => {
            const dateFilter = isDateFilter(filter, widgets, datasets, analyses);
            const firstBinding = filter.bindings[0];
            const firstContext = firstBinding
              ? widgetContext.find((item) => item.widget.id === firstBinding.widgetId)
              : undefined;
            const firstField = firstContext?.dataset?.fields.find((field) => field.key === firstBinding?.field);
            const dateTime = firstField?.dataType === 'datetime';
            const defaultDate = filter.defaultValue === undefined || filter.defaultValue === null
              ? null
              : dayjs(String(filter.defaultValue));

            return (
              <div key={filter.id} className="rounded-[8px] border border-[#e5e7eb] bg-white p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[6px] bg-[#f5f6f7] text-[#667085]">
                      {dateFilter ? <CalendarDays size={13} /> : <SlidersHorizontal size={13} />}
                    </div>
                    <div className="min-w-0">
                      <div className="truncate text-[11px] font-medium text-[#344054]">
                        {filter.name || intl.formatMessage(
                          { id: 'pages.dashboard.editor.globalFilter.fallbackName' },
                          { index: index + 1 },
                        )}
                      </div>
                      <div className="mt-0.5 text-[9px] text-[#98a2b3]">
                        {intl.formatMessage(
                          { id: dateFilter
                            ? 'pages.dashboard.editor.globalFilter.dateSummary'
                            : 'pages.dashboard.editor.globalFilter.fieldSummary' },
                          { count: filter.bindings.length },
                        )}
                      </div>
                    </div>
                  </div>
                  <Tooltip title={intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.delete' })}>
                    <Button
                      size="small"
                      type="text"
                      danger
                      icon={<Trash2 size={12} />}
                      onClick={() => removeFilter(filter.id)}
                    />
                  </Tooltip>
                </div>

                <div className="mt-3 grid grid-cols-[1fr_132px] gap-2">
                  <div>
                    <div className="mb-1 text-[10px] text-[#667085]">
                      {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.name' })}
                    </div>
                    <Input
                      size="small"
                      value={filter.name}
                      maxLength={200}
                      onChange={(event) => patchFilter(filter.id, { name: event.target.value })}
                    />
                  </div>
                  <div>
                    <div className="mb-1 text-[10px] text-[#667085]">
                      {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.condition' })}
                    </div>
                    <Select
                      size="small"
                      className="w-full"
                      value={filter.operator}
                      options={dateFilter ? dateOperators : textOperators}
                      onChange={(operator) => patchFilter(filter.id, { operator })}
                    />
                  </div>
                </div>

                <div className="mt-2">
                  <div className="mb-1 text-[10px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.defaultValue' })}
                  </div>
                  {dateFilter ? (
                    <DatePicker
                      size="small"
                      allowClear
                      showTime={dateTime ? { format: 'HH:mm:ss' } : false}
                      format={dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'}
                      value={defaultDate?.isValid() ? defaultDate : null}
                      className="w-full"
                      placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.noDefault' })}
                      onChange={(value) => patchFilter(filter.id, {
                        defaultValue: value
                          ? value.format(dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD')
                          : undefined,
                      })}
                    />
                  ) : (
                    <Input
                      size="small"
                      allowClear
                      placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.noDefault' })}
                      value={filter.defaultValue === undefined || filter.defaultValue === null
                        ? ''
                        : String(filter.defaultValue)}
                      onChange={(event) => patchFilter(filter.id, {
                        defaultValue: event.target.value || undefined,
                      })}
                    />
                  )}
                </div>

                <div className="mt-3 border-t border-[#edf0f3] pt-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-[10px] font-medium text-[#667085]">
                      {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.targetCharts' })}
                    </span>
                    <span className="text-[9px] text-[#98a2b3]">
                      {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.targetHint' })}
                    </span>
                  </div>

                  {!widgetContext.length ? (
                    <div className="rounded-[5px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
                      {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.addChartFirst' })}
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      {widgetContext.map(({ widget, dataset }) => {
                        const binding = filter.bindings.find((item) => item.widgetId === widget.id);
                        const fieldOptions = (dataset?.fields || [])
                          .filter((field) => !dateFilter || isDateFieldType(field.dataType))
                          .map((field) => ({
                            label: `${field.label} · ${field.dataType}`,
                            value: field.key,
                          }));
                        const enabled = Boolean(binding);

                        return (
                          <div
                            key={widget.id}
                            className="flex min-h-9 items-center gap-2 rounded-[5px] bg-[#fafbfc] px-2"
                          >
                            <Switch
                              size="small"
                              checked={enabled}
                              disabled={!fieldOptions.length}
                              onChange={(checked) => {
                                if (!checked) {
                                  patchFilter(filter.id, {
                                    bindings: filter.bindings.filter((item) => item.widgetId !== widget.id),
                                  });
                                  return;
                                }
                                const first = fieldOptions[0]?.value;
                                if (!first) return;
                                patchFilter(filter.id, {
                                  bindings: [
                                    ...filter.bindings.filter((item) => item.widgetId !== widget.id),
                                    { widgetId: widget.id, field: first },
                                  ],
                                });
                              }}
                            />
                            <div className="min-w-0 flex-1 truncate text-[10px] text-[#475467]">
                              {widget.title || intl.formatMessage({ id: 'pages.dashboard.editor.unnamedChart' })}
                            </div>
                            <Select
                              size="small"
                              className="w-[190px]"
                              placeholder={intl.formatMessage({
                                id: fieldOptions.length
                                  ? 'pages.dashboard.editor.globalFilter.selectField'
                                  : dateFilter
                                    ? 'pages.dashboard.editor.globalFilter.noDateFields'
                                    : 'pages.dashboard.editor.globalFilter.noFields',
                              })}
                              disabled={!enabled || !fieldOptions.length}
                              value={binding?.field}
                              options={fieldOptions}
                              onChange={(field) => patchFilter(filter.id, {
                                bindings: filter.bindings.map((item) =>
                                  item.widgetId === widget.id ? { ...item, field } : item),
                              })}
                            />
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </Drawer>
  );
}
