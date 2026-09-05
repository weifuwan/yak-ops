import { useIntl } from '@umijs/max';
import { Button, Input, Select } from 'antd';
import { Plus, X } from 'lucide-react';
import { FILTER_OPERATOR_OPTIONS } from './helpers';
import type { DashboardFilter, FilterOperator, SortDirection } from './model';

export function QueryControls({
  sortOptions,
  filterOptions,
  sortField,
  sortDirection,
  filters,
  onSortField,
  onSortDirection,
  onFiltersChange,
}: {
  sortOptions: Array<{ label: string; value: string }>;
  filterOptions: Array<{ label: string; value: string }>;
  sortField?: string;
  sortDirection: SortDirection;
  filters: DashboardFilter[];
  onSortField: (field?: string) => void;
  onSortDirection: (direction: SortDirection) => void;
  onFiltersChange: (filters: DashboardFilter[]) => void;
}) {
  const intl = useIntl();
  const operatorOptions = FILTER_OPERATOR_OPTIONS.map((item) => ({
    value: item.value,
    label: intl.formatMessage({ id: item.messageId }),
  }));

  const updateFilter = (id: string, patch: Partial<DashboardFilter>) => {
    onFiltersChange(filters.map((filter) => filter.id === id ? { ...filter, ...patch } : filter));
  };

  const removeFilter = (id: string) => {
    onFiltersChange(filters.filter((filter) => filter.id !== id));
  };

  const addFilter = () => {
    if (filters.length >= 8) return;
    const firstField = filterOptions[0]?.value;
    if (!firstField) return;
    onFiltersChange([
      ...filters,
      {
        id: `filter-${Date.now()}-${filters.length}`,
        field: firstField,
        operator: 'eq',
        value: '',
      },
    ]);
  };

  return (
    <div className="space-y-4">
      <div>
        <div className="mb-1 text-[11px] text-[#667085]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.query.sort' })}
        </div>
        <div className="flex gap-2">
          <Select
            allowClear
            size="small"
            className="min-w-0 flex-1"
            placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.query.sortFieldPlaceholder' })}
            value={sortField}
            options={sortOptions}
            onChange={onSortField}
          />
          <Select
            size="small"
            className="w-[76px]"
            disabled={!sortField}
            value={sortDirection}
            options={[
              { label: intl.formatMessage({ id: 'pages.dashboard.editor.query.asc' }), value: 'asc' },
              { label: intl.formatMessage({ id: 'pages.dashboard.editor.query.desc' }), value: 'desc' },
            ]}
            onChange={onSortDirection}
          />
        </div>
      </div>

      <div className="border-t border-[#edf0f3] pt-4">
        <div className="mb-2 flex items-center justify-between">
          <div>
            <div className="text-[11px] text-[#667085]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.query.filters' })}
            </div>
            <div className="mt-0.5 text-[9px] text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.query.andHint' })}
            </div>
          </div>
          <Button
            size="small"
            type="text"
            className="!h-6 !px-1.5 !text-[10px]"
            icon={<Plus size={11} />}
            disabled={!filterOptions.length || filters.length >= 8}
            onClick={addFilter}
          >
            {intl.formatMessage({ id: 'pages.dashboard.editor.query.add' })}
          </Button>
        </div>

        {filters.length ? (
          <div className="space-y-2.5">
            {filters.map((filter) => (
              <div key={filter.id} className="rounded-[7px] border border-[#e8eaee] bg-[#fafbfc] p-2.5">
                <div className="grid grid-cols-[1fr_88px_24px] gap-1.5">
                  <Select
                    size="small"
                    value={filter.field}
                    options={filterOptions}
                    onChange={(field: string) => updateFilter(filter.id, { field })}
                  />
                  <Select
                    size="small"
                    value={filter.operator}
                    options={operatorOptions}
                    onChange={(operator: FilterOperator) => updateFilter(filter.id, { operator })}
                  />
                  <button
                    type="button"
                    className="flex h-6 w-6 items-center justify-center rounded-[5px] text-[#98a2b3] hover:bg-[#f0f1f3] hover:text-[#475467]"
                    aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.query.deleteFilter' })}
                    onClick={() => removeFilter(filter.id)}
                  >
                    <X size={11} />
                  </button>
                </div>
                <Input
                  size="small"
                  className="mt-2"
                  placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.query.valuePlaceholder' })}
                  value={filter.value}
                  onChange={(event) => updateFilter(filter.id, { value: event.target.value })}
                />
              </div>
            ))}
          </div>
        ) : (
          <div className="rounded-[6px] border border-dashed border-[#e2e5e9] px-2.5 py-3 text-center text-[9px] text-[#a0a6af]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.query.empty' })}
          </div>
        )}
      </div>
    </div>
  );
}
