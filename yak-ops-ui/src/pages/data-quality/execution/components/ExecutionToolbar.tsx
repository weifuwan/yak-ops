import YakButton from '@/components/YakButton';
import { useIntl } from '@umijs/max';
import { DatePicker, Input, Popover, Segmented } from 'antd';
import type { Dayjs } from 'dayjs';
import { ListFilter, RefreshCw, Search } from 'lucide-react';

import type { ExecutionAdvancedFilterState } from '../hooks/useQualityExecutionPage';
import ExecutionAdvancedFilter from './ExecutionAdvancedFilter';
import type { ExecutionViewMode } from './ExecutionRecordTable';

const { RangePicker } = DatePicker;

interface ExecutionToolbarProps {
  viewMode: ExecutionViewMode;
  onViewModeChange: (value: ExecutionViewMode) => void;
  keywordDraft: string;
  onKeywordDraftChange: (value: string) => void;
  dateRange: [Dayjs, Dayjs] | null;
  onDateRangeChange: (value: [Dayjs, Dayjs] | null) => void;
  advancedOpen: boolean;
  onAdvancedOpenChange: (open: boolean) => void;
  advancedFilterCount: number;
  draftFilters: ExecutionAdvancedFilterState;
  onDraftFiltersChange: (value: ExecutionAdvancedFilterState) => void;
  onSearch: () => void;
  onApplyAdvanced: () => void;
  onReset: () => void;
  onRefresh: () => void;
}

export default function ExecutionToolbar({
  viewMode,
  onViewModeChange,
  keywordDraft,
  onKeywordDraftChange,
  dateRange,
  onDateRangeChange,
  advancedOpen,
  onAdvancedOpenChange,
  advancedFilterCount,
  draftFilters,
  onDraftFiltersChange,
  onSearch,
  onApplyAdvanced,
  onReset,
  onRefresh,
}: ExecutionToolbarProps) {
  const intl = useIntl();
  return (
    <div className="shrink-0 border-b border-[#eceef0] pb-2">
      <div className="flex min-w-0 flex-nowrap items-center gap-3 overflow-x-auto">
        <Segmented<ExecutionViewMode>
          value={viewMode}
          options={[
            {
              label: intl.formatMessage({
                id: 'pages.dataQuality.execution.view.rule',
              }),
              value: 'RULE',
            },
            {
              label: intl.formatMessage({
                id: 'pages.dataQuality.execution.view.monitor',
              }),
              value: 'EXECUTION',
            },
          ]}
          onChange={onViewModeChange}
          className="shrink-0"
        />

        <div className="ml-auto flex shrink-0 items-center gap-2">
          <Input
            allowClear
            variant="filled"
            value={keywordDraft}
            onChange={(event) => onKeywordDraftChange(event.target.value)}
            onPressEnter={onSearch}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.searchPlaceholder',
            })}
            className="w-[220px]"
          />

          <RangePicker
            variant="filled"
            value={dateRange}
            showTime={false}
            format="YYYY-MM-DD"
            className="w-[250px]"
            onChange={(value) => {
              if (value?.[0] && value?.[1]) {
                onDateRangeChange([value[0], value[1]]);
              } else {
                onDateRangeChange(null);
              }
            }}
          />

          <YakButton
            type="text"
            className="!text-[#667085]"
            onClick={onSearch}
          >
            {intl.formatMessage({ id: 'pages.dataQuality.execution.query' })}
          </YakButton>

          <Popover
            trigger="click"
            placement="bottomRight"
            open={advancedOpen}
            onOpenChange={onAdvancedOpenChange}
            content={
              <ExecutionAdvancedFilter
                value={draftFilters}
                onChange={onDraftFiltersChange}
                onApply={onApplyAdvanced}
                onReset={onReset}
              />
            }
          >
            <YakButton
              type="text"
              className="!text-[#667085]"
              icon={<ListFilter size={14} />}
            >
              {intl.formatMessage({ id: 'pages.dataQuality.execution.advanced' })}
              {advancedFilterCount ? ` (${advancedFilterCount})` : ''}
            </YakButton>
          </Popover>

          <YakButton
            type="text"
            iconOnly
            className="!text-[#667085]"
            aria-label={intl.formatMessage({
              id: 'pages.dataQuality.execution.refreshAria',
            })}
            icon={<RefreshCw size={14} />}
            onClick={onRefresh}
          />
        </div>
      </div>
    </div>
  );
}
