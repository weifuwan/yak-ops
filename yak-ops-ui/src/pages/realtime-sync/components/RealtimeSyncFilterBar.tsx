import { YakButton, YakFilterSwitch } from '@/components/ui';
import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Input, Popover, Select } from 'antd';
import { useState } from 'react';

import {
  getRealtimeSyncReleaseOptions,
  getRealtimeSyncStatusTabs,
} from '../constants';
import type {
  RealtimeFilterField,
  RealtimeFilterState,
  RealtimePageStateGroup,
} from '../types';

interface RealtimeSyncFilterBarProps {
  filterDraft: RealtimeFilterState;
  activeStateGroup: RealtimePageStateGroup;
  advancedFilterCount: number;
  onDraftChange: <Field extends RealtimeFilterField>(
    field: Field,
    value: RealtimeFilterState[Field],
  ) => void;
  onStateGroupChange: (value: RealtimePageStateGroup) => void;
  onReleaseStateChange: (
    value: RealtimeFilterState['releaseState'],
  ) => void;
  onSearch: () => boolean;
  onReset: () => void;
}

const RealtimeSyncFilterBar = ({
  filterDraft,
  activeStateGroup,
  advancedFilterCount,
  onDraftChange,
  onStateGroupChange,
  onReleaseStateChange,
  onSearch,
  onReset,
}: RealtimeSyncFilterBarProps) => {
  const intl = useIntl();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const hasActiveFilters =
    activeStateGroup !== 'ALL' ||
    Boolean(filterDraft.keyword || filterDraft.releaseState || filterDraft.id);
  const statusTabs = getRealtimeSyncStatusTabs(intl);
  const releaseOptions = getRealtimeSyncReleaseOptions(intl);

  const applyAdvancedFilter = () => {
    if (onSearch()) setAdvancedOpen(false);
  };

  const resetFilters = () => {
    onReset();
    setAdvancedOpen(false);
  };

  return (
    <div className="flex min-h-9 items-center justify-between gap-6">
      <YakFilterSwitch
        value={activeStateGroup}
        options={statusTabs}
        onChange={onStateGroupChange}
      />

      <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
        <Input
          allowClear
          variant="filled"
          value={filterDraft.keyword}
          prefix={<SearchOutlined className="text-[#98a2b3]" />}
          placeholder={intl.formatMessage({
            id: 'pages.realtimeSync.filter.searchPlaceholder',
          })}
          className="!h-9 !w-[240px] !min-w-[190px]"
          onChange={(event) =>
            onDraftChange('keyword', event.target.value || undefined)
          }
          onPressEnter={() => void onSearch()}
        />

        <Select
          allowClear
          variant="filled"
          value={filterDraft.releaseState}
          options={releaseOptions.map((item) => ({ ...item }))}
          placeholder={intl.formatMessage({
            id: 'pages.realtimeSync.filter.releaseState',
          })}
          className="!h-9 !w-[145px] !min-w-[135px]"
          onChange={onReleaseStateChange}
        />

        <YakButton className="!h-9 !px-4" onClick={() => void onSearch()}>
          {intl.formatMessage({ id: 'pages.realtimeSync.filter.search' })}
        </YakButton>

        {hasActiveFilters ? (
          <YakButton
            type="text"
            className="!h-9 !px-2 !text-[#777c86]"
            onClick={resetFilters}
          >
            {intl.formatMessage({ id: 'pages.realtimeSync.filter.reset' })}
          </YakButton>
        ) : null}

        <Popover
          trigger="click"
          placement="bottomRight"
          open={advancedOpen}
          onOpenChange={setAdvancedOpen}
          content={
            <div className="w-[320px]">
              <div className="text-[14px] font-semibold text-[#101828]">
                {intl.formatMessage({ id: 'pages.realtimeSync.filter.advanced' })}
              </div>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                {intl.formatMessage({
                  id: 'pages.realtimeSync.filter.advancedDescription',
                })}
              </div>

              <div className="mt-4">
                <div className="mb-1.5 text-[12px] text-[#667085]">
                  {intl.formatMessage({ id: 'pages.realtimeSync.filter.taskId' })}
                </div>
                <Input
                  allowClear
                  variant="filled"
                  value={filterDraft.id}
                  placeholder={intl.formatMessage({
                    id: 'pages.realtimeSync.filter.taskIdPlaceholder',
                  })}
                  onChange={(event) =>
                    onDraftChange('id', event.target.value || undefined)
                  }
                  onPressEnter={applyAdvancedFilter}
                />
              </div>

              <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#f0f0f0] pt-4">
                <YakButton size="small" onClick={resetFilters}>
                  {intl.formatMessage({ id: 'pages.realtimeSync.filter.resetAll' })}
                </YakButton>
                <YakButton
                  type="primary"
                  size="small"
                  onClick={applyAdvancedFilter}
                >
                  {intl.formatMessage({ id: 'pages.realtimeSync.filter.apply' })}
                </YakButton>
              </div>
            </div>
          }
        >
          <YakButton
            size="small"
            icon={<FilterOutlined />}
            className={[
              '!h-9 !px-3',
              advancedFilterCount > 0
                ? '!border-[#ffccc7] !bg-[#fff1f0] !text-[#ff4d4f]'
                : '',
            ].join(' ')}
          >
            {intl.formatMessage({ id: 'pages.realtimeSync.filter.advanced' })}
            {advancedFilterCount > 0 ? (
              <span className="ml-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#ff4d4f] px-1 text-[10px] leading-[18px] text-white">
                {advancedFilterCount}
              </span>
            ) : null}
          </YakButton>
        </Popover>
      </div>
    </div>
  );
};

export default RealtimeSyncFilterBar;
