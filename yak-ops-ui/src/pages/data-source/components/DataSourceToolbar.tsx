import { YakButton, YakTab } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Input, Select } from 'antd';
import { motion } from 'framer-motion';
import { Grid2X2, LayoutList, Search } from 'lucide-react';

import {
  COMMON_DB_OPTIONS,
  getDataSourceEnvironmentTabs,
  PAGE_ANIMATION,
} from '../constants';
import DatabaseIcons from '../icon/DatabaseIcons';
import type { DataSourceViewMode } from '../types';

interface DataSourceToolbarProps {
  environment?: string;
  dbType?: string;
  keyword: string;
  viewMode: DataSourceViewMode;
  hasActiveFilters: boolean;
  onEnvironmentChange: (value?: string) => void;
  onDbTypeChange: (value?: string) => void;
  onKeywordChange: (value: string) => void;
  onViewModeChange: (value: DataSourceViewMode) => void;
  onReset: () => void;
}

const DB_TYPE_LABELS: Record<string, string> = {
  MYSQL: 'MYSQL',
  ORACLE: 'ORACLE',
  POSTGRE_SQL: 'PostgreSQL',
  DORIS: 'Doris',
  KINGBASE: 'KINGBASE',
  DAMENG: 'DAMENG',
};

const dbTypeLabel = (value: string) => DB_TYPE_LABELS[value] || value;

const DbTypeLabel = ({ value }: { value: string }) => (
  <span className="flex min-w-0 items-center gap-2">
    <span className="flex h-[18px] w-[18px] shrink-0 items-center justify-center">
      <DatabaseIcons dbType={value} width="18px" height="18px" />
    </span>
    <span className="truncate">{dbTypeLabel(value)}</span>
  </span>
);

const DataSourceToolbar = ({
  environment,
  dbType,
  keyword,
  viewMode,
  hasActiveFilters,
  onEnvironmentChange,
  onDbTypeChange,
  onKeywordChange,
  onViewModeChange,
  onReset,
}: DataSourceToolbarProps) => {
  const intl = useIntl();
  const environmentTabs = getDataSourceEnvironmentTabs(intl);

  return (
    <motion.section
      variants={PAGE_ANIMATION.fadeUp}
      className="flex min-h-9 items-end justify-between gap-6 border-b border-solid border-[#eceef2] max-xl:flex-col max-xl:items-stretch max-xl:gap-3"
    >
      <div className="flex items-end">
        <YakTab
          size="small"
          activeKey={environment || 'all'}
          className={[
            '[&_.ant-tabs-nav]:!mb-0',
            '[&_.ant-tabs-nav::before]:!hidden',
            '[&_.ant-tabs-tab]:!px-0',
            '[&_.ant-tabs-tab]:!pb-[10px]',
            '[&_.ant-tabs-tab+.ant-tabs-tab]:!ml-8',
            '[&_.ant-tabs-tab]:!text-[13px]',
            '[&_.ant-tabs-tab]:!text-[#8c919b]',
            '[&_.ant-tabs-tab.ant-tabs-tab-active_.ant-tabs-tab-btn]:!font-semibold',
            '[&_.ant-tabs-tab.ant-tabs-tab-active_.ant-tabs-tab-btn]:!text-[#292c35]',
            '[&_.ant-tabs-tab::after]:!bottom-[-1px]',
            '[&_.ant-tabs-tab::after]:!h-0.5',
            '[&_.ant-tabs-tab::after]:!bg-[#252832]',
          ].join(' ')}
          items={environmentTabs.map((item) => ({
            key: item.key,
            label: item.label,
          }))}
          onChange={(key) => {
            const target = environmentTabs.find((item) => item.key === key);
            onEnvironmentChange(target?.value);
          }}
        />
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2 pb-[5px] max-xl:justify-start">
        <Select
          allowClear
          showSearch
          variant="filled"
          value={dbType}
          className={[
            '!w-[144px]',
            '[&_.ant-select-selector]:!h-9',
            '[&_.ant-select-selector]:!rounded-[10px]',
            '[&_.ant-select-selector]:!border-0',
            '[&_.ant-select-selector]:!bg-[#f6f7f9]',
            '[&_.ant-select-selection-item]:!flex',
            '[&_.ant-select-selection-item]:!items-center',
            '[&_.ant-select-selection-item]:!text-[12px]',
            '[&_.ant-select-selection-placeholder]:!text-[12px]',
            '[&_.ant-select-selection-placeholder]:!leading-[36px]',
          ].join(' ')}
          placeholder={intl.formatMessage({
            id: 'pages.datasource.toolbar.typePlaceholder',
          })}
          options={COMMON_DB_OPTIONS}
          popupMatchSelectWidth={188}
          popupClassName={[
            '!rounded-[10px]',
            '!p-1.5',
            '!shadow-[0_10px_30px_rgba(31,35,41,0.14)]',
            '[&_.ant-select-item]:!min-h-8',
            '[&_.ant-select-item]:!rounded-[7px]',
            '[&_.ant-select-item]:!px-2.5',
            '[&_.ant-select-item]:!py-1.5',
            '[&_.ant-select-item-option-content]:!text-[13px]',
            '[&_.ant-select-item-option-active]:!bg-[#f5f6f8]',
            '[&_.ant-select-item-option-selected]:!bg-[#fff1f3]',
            '[&_.ant-select-item-option-selected]:!font-semibold',
            '[&_.ant-select-item-option-selected.ant-select-item-option-active]:!bg-[#fff1f3]',
          ].join(' ')}
          filterOption={(input, option) => {
            const value = String(option?.value || '');
            return `${value} ${dbTypeLabel(value)}`
              .toLowerCase()
              .includes(input.toLowerCase());
          }}
          optionRender={(option) => (
            <DbTypeLabel value={String(option.value || '')} />
          )}
          labelRender={(label) => {
            const value = String(label.value || '');
            return value ? <DbTypeLabel value={value} /> : label.label;
          }}
          onChange={onDbTypeChange}
        />

        <Input
          allowClear
          variant="filled"
          value={keyword}
          prefix={
            <Search size={15} strokeWidth={1.8} className="text-[#8f949e]" />
          }
          className={[
            '!w-[292px] max-md:!w-[220px]',
            '[&.ant-input-affix-wrapper]:!h-9',
            '[&.ant-input-affix-wrapper]:!rounded-[10px]',
            '[&.ant-input-affix-wrapper]:!border-0',
            '[&.ant-input-affix-wrapper]:!bg-[#f6f7f9]',
            '[&_.ant-input]:!text-[12px]',
          ].join(' ')}
          placeholder={intl.formatMessage({
            id: 'pages.datasource.toolbar.searchPlaceholder',
          })}
          onChange={(event) => onKeywordChange(event.target.value)}
        />

        {hasActiveFilters ? (
          <YakButton
            type="text"
            size="small"
            className="!h-9 !rounded-[9px] !px-2.5 !text-[12px] !text-[#777c86]"
            onClick={onReset}
          >
            {intl.formatMessage({ id: 'pages.datasource.toolbar.reset' })}
          </YakButton>
        ) : null}

        <div className="flex h-9 items-center gap-0.5 rounded-[10px] bg-[#f4f5f7] p-[3px]">
          <YakButton
            type="text"
            iconOnly
            title={intl.formatMessage({ id: 'pages.datasource.toolbar.gridView' })}
            className={[
              '!h-[30px] !w-[30px] !rounded-[7px] !border-0 !p-0',
              viewMode === 'grid'
                ? '!bg-white !text-[#2d313a] !shadow-[0_1px_4px_rgba(31,35,41,0.10)]'
                : '!bg-transparent !text-[#92969f] hover:!text-[#555b66]',
            ].join(' ')}
            icon={<Grid2X2 size={15} strokeWidth={1.8} />}
            onClick={() => onViewModeChange('grid')}
          />

          <YakButton
            type="text"
            iconOnly
            title={intl.formatMessage({ id: 'pages.datasource.toolbar.listView' })}
            className={[
              '!h-[30px] !w-[30px] !rounded-[7px] !border-0 !p-0',
              viewMode === 'list'
                ? '!bg-white !text-[#2d313a] !shadow-[0_1px_4px_rgba(31,35,41,0.10)]'
                : '!bg-transparent !text-[#92969f] hover:!text-[#555b66]',
            ].join(' ')}
            icon={<LayoutList size={16} strokeWidth={1.8} />}
            onClick={() => onViewModeChange('list')}
          />
        </div>
      </div>
    </motion.section>
  );
};

export default DataSourceToolbar;
