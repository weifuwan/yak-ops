import { YakButton, YakFilterSwitch } from '@/components/ui';
import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { DatePicker, Input, Popover, Select } from 'antd';
import { useState } from 'react';

import type {
  OfflineSyncConnectorOption,
  OfflineSyncSearchField,
  OfflineSyncSearchState,
} from '../types';

const { RangePicker } = DatePicker;

interface OfflineSyncFilterBarProps {
  filterDraft: OfflineSyncSearchState;
  currentStatus: string;
  connectorOptions: OfflineSyncConnectorOption[];
  advancedFilterCount: number;
  onDraftChange: (
    field: OfflineSyncSearchField,
    value: OfflineSyncSearchState[OfflineSyncSearchField],
  ) => void;
  onQuickFilterChange: (
    field: OfflineSyncSearchField,
    value: OfflineSyncSearchState[OfflineSyncSearchField],
  ) => void;
  onStatusChange: (value: string) => void;
  onSearch: () => void;
  onReset: () => void;
  onAdvancedReset: () => void;
}

const OfflineSyncFilterBar = ({
  filterDraft,
  currentStatus,
  connectorOptions,
  advancedFilterCount,
  onDraftChange,
  onQuickFilterChange,
  onStatusChange,
  onSearch,
  onReset,
  onAdvancedReset,
}: OfflineSyncFilterBarProps) => {
  const intl = useIntl();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const statusOptions = [
    {
      label: intl.formatMessage({ id: 'pages.batchLinkUp.status.all' }),
      value: 'ALL',
    },
    {
      label: intl.formatMessage({ id: 'pages.batchLinkUp.status.running' }),
      value: 'RUNNING',
    },
    {
      label: intl.formatMessage({ id: 'pages.batchLinkUp.status.succeeded' }),
      value: 'COMPLETED',
    },
    {
      label: intl.formatMessage({ id: 'pages.batchLinkUp.status.failed' }),
      value: 'FAILED',
    },
  ];

  const applyAdvancedFilters = () => {
    onSearch();
    setAdvancedOpen(false);
  };

  return (
    <div className="flex min-h-[44px] items-center justify-between gap-4">
      <YakFilterSwitch
        value={currentStatus}
        options={statusOptions}
        onChange={onStatusChange}
      />

      <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
        <Input
          allowClear
          variant="filled"
          value={filterDraft.jobName}
          prefix={<SearchOutlined className="text-[#98a2b3]" />}
          placeholder={intl.formatMessage({
            id: 'pages.batchLinkUp.filter.jobNamePlaceholder',
          })}
          className="!h-9 !w-[220px] !min-w-[180px]"
          onChange={(event) =>
            onDraftChange('jobName', event.target.value || undefined)
          }
          onPressEnter={onSearch}
        />

        <Select
          allowClear
          showSearch
          variant="filled"
          value={filterDraft.sourceType}
          options={connectorOptions}
          placeholder={intl.formatMessage({
            id: 'pages.batchLinkUp.filter.sourceType',
          })}
          className="!h-9 !w-[150px] !min-w-[140px]"
          optionFilterProp="value"
          onChange={(value) => onQuickFilterChange('sourceType', value)}
        />

        <RangePicker
          allowClear
          variant="filled"
          value={filterDraft.createTime as never}
          format="YYYY-MM-DD"
          placeholder={[
            intl.formatMessage({ id: 'pages.batchLinkUp.filter.startDate' }),
            intl.formatMessage({ id: 'pages.batchLinkUp.filter.endDate' }),
          ]}
          className="!h-9 !w-[250px] !min-w-[230px]"
          onChange={(value) =>
            onQuickFilterChange(
              'createTime',
              (value || undefined) as unknown as OfflineSyncSearchState['createTime'],
            )
          }
        />

        <YakButton className="!h-9 !px-4" onClick={onSearch}>
          {intl.formatMessage({ id: 'pages.batchLinkUp.filter.search' })}
        </YakButton>

        <YakButton type="text" className="!h-9 !px-2" onClick={onReset}>
          {intl.formatMessage({ id: 'pages.batchLinkUp.filter.reset' })}
        </YakButton>

        <Popover
          trigger="click"
          placement="bottomRight"
          open={advancedOpen}
          onOpenChange={setAdvancedOpen}
          overlayClassName="sync-task-advanced-filter"
          content={
            <div className="w-[430px]">
              <div className="mb-4">
                <div className="text-[14px] font-semibold text-[#101828]">
                  {intl.formatMessage({ id: 'pages.batchLinkUp.filter.advanced' })}
                </div>
                <div className="mt-1 text-[12px] text-[#98a2b3]">
                  {intl.formatMessage({
                    id: 'pages.batchLinkUp.filter.advancedDescription',
                  })}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-x-3 gap-y-4">
                <div>
                  <div className="mb-1.5 text-[12px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.batchLinkUp.filter.taskId' })}
                  </div>
                  <Input
                    allowClear
                    variant="filled"
                    value={filterDraft.id}
                    placeholder={intl.formatMessage({
                      id: 'pages.batchLinkUp.filter.taskIdPlaceholder',
                    })}
                    onChange={(event) =>
                      onDraftChange('id', event.target.value || undefined)
                    }
                    onPressEnter={applyAdvancedFilters}
                  />
                </div>

                <div>
                  <div className="mb-1.5 text-[12px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.batchLinkUp.filter.sinkType' })}
                  </div>
                  <Select
                    allowClear
                    showSearch
                    variant="filled"
                    value={filterDraft.sinkType}
                    options={connectorOptions}
                    placeholder={intl.formatMessage({
                      id: 'pages.batchLinkUp.filter.sinkTypePlaceholder',
                    })}
                    optionFilterProp="value"
                    className="w-full"
                    onChange={(value) => onDraftChange('sinkType', value)}
                  />
                </div>

                <div>
                  <div className="mb-1.5 text-[12px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.batchLinkUp.filter.sourceTable' })}
                  </div>
                  <Input
                    allowClear
                    variant="filled"
                    value={filterDraft.sourceTable}
                    placeholder={intl.formatMessage({
                      id: 'pages.batchLinkUp.filter.sourceTablePlaceholder',
                    })}
                    onChange={(event) =>
                      onDraftChange(
                        'sourceTable',
                        event.target.value || undefined,
                      )
                    }
                    onPressEnter={applyAdvancedFilters}
                  />
                </div>

                <div>
                  <div className="mb-1.5 text-[12px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.batchLinkUp.filter.sinkTable' })}
                  </div>
                  <Input
                    allowClear
                    variant="filled"
                    value={filterDraft.sinkTable}
                    placeholder={intl.formatMessage({
                      id: 'pages.batchLinkUp.filter.sinkTablePlaceholder',
                    })}
                    onChange={(event) =>
                      onDraftChange(
                        'sinkTable',
                        event.target.value || undefined,
                      )
                    }
                    onPressEnter={applyAdvancedFilters}
                  />
                </div>
              </div>

              <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#f0f0f0] pt-4">
                <YakButton
                  size="small"
                  onClick={() => {
                    onAdvancedReset();
                    setAdvancedOpen(false);
                  }}
                >
                  {intl.formatMessage({ id: 'pages.batchLinkUp.filter.reset' })}
                </YakButton>
                <YakButton
                  type="primary"
                  size="small"
                  onClick={applyAdvancedFilters}
                >
                  {intl.formatMessage({ id: 'pages.batchLinkUp.filter.apply' })}
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
            {intl.formatMessage({ id: 'pages.batchLinkUp.filter.advanced' })}
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

export default OfflineSyncFilterBar;
