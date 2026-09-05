import { useIntl } from '@umijs/max';
import { Button, DatePicker, Input, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { Plus, RefreshCw, Settings2, SlidersHorizontal } from 'lucide-react';
import { isDateFilter, resolveBindingField } from './filter-utils';
import type {
  AnalysisAsset,
  DashboardGlobalFilter,
  DashboardWidget,
  FilterOperator,
  PublishedDataset,
  Scalar,
} from './model';

const OPERATOR_MESSAGE_IDS: Record<FilterOperator, string> = {
  eq: 'pages.dashboard.editor.operator.eq',
  neq: 'pages.dashboard.editor.operator.neq',
  contains: 'pages.dashboard.editor.operator.contains',
  gt: 'pages.dashboard.editor.operator.gt',
  gte: 'pages.dashboard.editor.operator.gte',
  lt: 'pages.dashboard.editor.operator.lt',
  lte: 'pages.dashboard.editor.operator.lte',
};

const own = (value: Record<string, Scalar | undefined>, key: string) =>
  Object.prototype.hasOwnProperty.call(value, key);

export function DashboardGlobalFilterBar({
  filters,
  runtimeValues,
  widgets,
  datasets,
  analyses,
  editable,
  onRuntimeValue,
  onReset,
  onManage,
}: {
  filters: DashboardGlobalFilter[];
  runtimeValues: Record<string, Scalar | undefined>;
  widgets: DashboardWidget[];
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  editable: boolean;
  onRuntimeValue: (filterId: string, value: Scalar | undefined) => void;
  onReset: () => void;
  onManage: () => void;
}) {
  const intl = useIntl();
  if (!filters.length && !editable) return null;

  return (
    <div
      className="flex min-h-11 shrink-0 items-center gap-2 border-b px-4 py-1.5"
      style={{
        backgroundColor: 'var(--dashboard-component-subtle-bg, #fbfcfd)',
        borderColor: 'var(--dashboard-component-border, #eceef1)',
      }}
    >
      <div
        className="flex shrink-0 items-center gap-1.5 text-[11px] font-medium"
        style={{ color: 'var(--dashboard-component-text, #475467)' }}
      >
        <SlidersHorizontal size={13} />
        {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.title' })}
        {filters.length ? (
          <span
            className="rounded-full px-1.5 py-px text-[9px] font-normal"
            style={{
              backgroundColor: 'var(--dashboard-component-bg, #eef0f2)',
              color: 'var(--dashboard-component-muted, #7a818c)',
            }}
          >
            {filters.length}
          </span>
        ) : null}
      </div>

      <div
        className="mx-1 h-5 w-px shrink-0"
        style={{ backgroundColor: 'var(--dashboard-component-border, #eceef1)' }}
      />

      <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto py-0.5">
        {filters.length ? filters.map((filter) => {
          const current = own(runtimeValues, filter.id)
            ? runtimeValues[filter.id]
            : filter.defaultValue;
          const dateFilter = isDateFilter(filter, widgets, datasets, analyses);
          const firstBinding = filter.bindings[0];
          const firstField = firstBinding
            ? resolveBindingField(
                firstBinding.widgetId,
                firstBinding.field,
                widgets,
                datasets,
                analyses,
              )
            : undefined;
          const dateTime = firstField?.dataType === 'datetime';
          const dateValue = current === undefined || current === null || current === ''
            ? null
            : dayjs(String(current));

          return (
            <div
              key={filter.id}
              className="flex h-8 shrink-0 items-center rounded-[7px] border pl-2.5 shadow-[0_1px_2px_rgba(16,24,40,.025)]"
              style={{
                backgroundColor: 'var(--dashboard-component-bg, #fff)',
                borderColor: 'var(--dashboard-component-border, #e7e9ed)',
              }}
            >
              <span
                className="mr-1.5 max-w-[120px] truncate text-[10px] font-medium"
                style={{ color: 'var(--dashboard-component-text, #475467)' }}
              >
                {filter.name}
              </span>
              <span
                className="mr-0.5 text-[9px]"
                style={{ color: 'var(--dashboard-component-muted, #a0a6af)' }}
              >
                {intl.formatMessage({ id: OPERATOR_MESSAGE_IDS[filter.operator] })}
              </span>
              {dateFilter ? (
                <DatePicker
                  variant="borderless"
                  size="small"
                  allowClear
                  showTime={dateTime ? { format: 'HH:mm:ss' } : false}
                  format={dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'}
                  className="!h-7 w-[158px] text-[10px]"
                  placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.all' })}
                  value={dateValue?.isValid() ? dateValue : null}
                  onChange={(value) => onRuntimeValue(
                    filter.id,
                    value
                      ? value.format(dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD')
                      : undefined,
                  )}
                />
              ) : (
                <Input
                  variant="borderless"
                  size="small"
                  allowClear
                  className="!h-7 w-[116px] text-[10px]"
                  placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.all' })}
                  value={current === undefined || current === null ? '' : String(current)}
                  onChange={(event) => onRuntimeValue(filter.id, event.target.value || undefined)}
                />
              )}
            </div>
          );
        }) : (
          <span
            className="text-[10px]"
            style={{ color: 'var(--dashboard-component-muted, #a0a6af)' }}
          >
            {intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.emptyHint' })}
          </span>
        )}
      </div>

      {filters.length ? (
        <Tooltip title={intl.formatMessage({ id: 'pages.dashboard.editor.globalFilter.reset' })}>
          <Button
            type="text"
            className="!h-7 !w-7 !min-w-0 !rounded-[6px] !p-0"
            style={{ color: 'var(--dashboard-component-muted, #667085)' }}
            icon={<RefreshCw size={12} />}
            onClick={onReset}
          />
        </Tooltip>
      ) : null}

      {editable ? (
        <Button
          size="small"
          className="!h-7 !rounded-[6px] !px-2.5 !text-[11px]"
          style={{ borderColor: 'var(--dashboard-component-border, #e4e7ec)' }}
          icon={filters.length ? <Settings2 size={12} /> : <Plus size={12} />}
          onClick={onManage}
        >
          {intl.formatMessage({
            id: filters.length
              ? 'pages.dashboard.editor.globalFilter.manage'
              : 'pages.dashboard.editor.globalFilter.add',
          })}
        </Button>
      ) : null}
    </div>
  );
}
