import { YakButton } from '@/components/ui';
import { SearchOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Empty, Input, Select } from 'antd';
import { useMemo, useState } from 'react';

import { COMMON_DB_OPTIONS } from '../constants';
import DatabaseIcons from '../icon/DatabaseIcons';
import type { DataSourceGroup } from '../types';

interface DataSourceTypeSelectorProps {
  dataSourceGroups: DataSourceGroup[];
  onSelect: (dbType: string) => void;
}

const DataSourceTypeSelector = ({
  dataSourceGroups,
  onSelect,
}: DataSourceTypeSelectorProps) => {
  const intl = useIntl();
  const [query, setQuery] = useState('');
  const [selectedGroupKey, setSelectedGroupKey] = useState<string | null>(null);
  const keyword = query.trim().toLowerCase();

  const flatDataSources = useMemo(
    () =>
      dataSourceGroups.flatMap((group) => {
        const groupKey = group.groupKey || group.groupName;
        return group.datasourceList.map((item) => ({
          ...item,
          groupKey,
          groupName: group.groupName,
          searchText: [item.dbType, item.connectorType, item.type]
            .filter(Boolean)
            .join(' ')
            .toLowerCase(),
        }));
      }),
    [dataSourceGroups],
  );

  const filteredDataSources = useMemo(
    () =>
      flatDataSources.filter((item) => {
        const matchesGroup =
          selectedGroupKey === null || item.groupKey === selectedGroupKey;
        const matchesKeyword = !keyword || item.searchText.includes(keyword);
        return matchesGroup && matchesKeyword;
      }),
    [flatDataSources, keyword, selectedGroupKey],
  );

  const groupedDataSources = useMemo(
    () =>
      dataSourceGroups
        .map((group) => ({
          groupKey: group.groupKey || group.groupName,
          groupName: group.groupName,
          items: filteredDataSources.filter(
            (item) => item.groupKey === (group.groupKey || group.groupName),
          ),
        }))
        .filter(
          (group) =>
            (selectedGroupKey === null || group.groupKey === selectedGroupKey) &&
            group.items.length > 0,
        ),
    [dataSourceGroups, filteredDataSources, selectedGroupKey],
  );

  const categoryOptions = useMemo(
    () => [
      {
        value: 'ALL',
        label: intl.formatMessage({
          id: 'pages.datasource.typeSelector.allCategories',
        }),
      },
      ...dataSourceGroups.map((group) => ({
        value: group.groupKey || group.groupName,
        label: group.groupName,
      })),
    ],
    [dataSourceGroups, intl],
  );

  const suggestedDataSources = useMemo(
    () =>
      COMMON_DB_OPTIONS.map((common) => {
        const matched = flatDataSources.find((item) => {
          const dbType = item.dbType?.toLowerCase();
          const value = common.value?.toLowerCase();
          const label = common.label?.toLowerCase();
          return dbType === value || dbType === label;
        });

        return {
          ...common,
          dbType: matched?.dbType || common.value,
        };
      })
        .filter((item) => Boolean(item.dbType))
        .slice(0, 3),
    [flatDataSources],
  );

  const renderSourceItem = (item: (typeof filteredDataSources)[number]) => (
    <YakButton
      key={[item.groupKey, item.dbType, item.connectorType || item.type || ''].join(
        '-',
      )}
      htmlType="button"
      disabled={item.disabled}
      className="!h-auto !min-h-[46px] !min-w-0 !justify-start !px-3 !py-2 !text-left"
      onClick={() => onSelect(item.dbType)}
    >
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white">
        <DatabaseIcons dbType={item.dbType} width="15px" height="15px" />
      </span>
      <span
        className="min-w-0 flex-1 truncate text-[13px] font-medium"
        title={item.dbType}
      >
        {item.dbType}
      </span>
    </YakButton>
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0">
        <div className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
          {intl.formatMessage({ id: 'pages.datasource.typeSelector.title' })}
        </div>

        <div className="flex gap-2">
          <Input
            allowClear
            variant="filled"
            prefix={<SearchOutlined className="text-[#98A2B3]" />}
            placeholder={intl.formatMessage({
              id: 'pages.datasource.typeSelector.searchPlaceholder',
            })}
            value={query}
            className="!h-9 !min-w-0 !flex-1 !rounded-lg"
            onChange={(event) => setQuery(event.target.value)}
          />

          <Select
            variant="filled"
            value={selectedGroupKey || 'ALL'}
            options={categoryOptions}
            className="!h-9 !w-[150px] shrink-0"
            popupMatchSelectWidth={false}
            onChange={(value) =>
              setSelectedGroupKey(value === 'ALL' ? null : value)
            }
          />
        </div>
      </div>

      {!keyword && selectedGroupKey === null && suggestedDataSources.length > 0 ? (
        <section className="mt-4 shrink-0">
          <div className="mb-2 text-xs font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.datasource.typeSelector.common' })}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {suggestedDataSources.map((item) => (
              <YakButton
                key={item.dbType}
                htmlType="button"
                className="!h-auto !min-w-0 !justify-start !px-2.5 !py-2 !text-left"
                onClick={() => onSelect(item.dbType)}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white">
                  <DatabaseIcons
                    dbType={item.dbType}
                    width="14px"
                    height="14px"
                  />
                </span>
                <span className="min-w-0 flex-1 truncate text-xs font-medium">
                  {item.label}
                </span>
              </YakButton>
            ))}
          </div>
        </section>
      ) : null}

      <section className="mt-5 flex min-h-0 flex-1 flex-col">
        <div className="mb-2 flex shrink-0 items-center justify-between">
          <span className="text-xs font-semibold text-[#161823]">
            {intl.formatMessage({
              id: 'pages.datasource.typeSelector.allDataSources',
            })}
          </span>
          <span className="text-[11px] text-[#98A2B3]">
            {filteredDataSources.length}
          </span>
        </div>

        {filteredDataSources.length === 0 ? (
          <div className="flex min-h-0 flex-1 items-center justify-center px-5 py-8">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={intl.formatMessage({
                id: 'pages.datasource.typeSelector.empty',
              })}
            />
          </div>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            <div className="space-y-4">
              {groupedDataSources.map((group) => (
                <section key={group.groupKey}>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-[11px] font-medium text-[#667085]">
                      {group.groupName}
                    </span>
                    <span className="text-[10px] text-[#B0B7C3]">
                      {group.items.length}
                    </span>
                  </div>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {group.items.map(renderSourceItem)}
                  </div>
                </section>
              ))}
            </div>
          </div>
        )}
      </section>
    </div>
  );
};

export default DataSourceTypeSelector;
