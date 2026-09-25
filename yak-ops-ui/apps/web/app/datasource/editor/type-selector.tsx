import { Button, Empty, Input } from '@yak-ops/yak-ui';
import { Search, X } from 'lucide-react';
import { useMemo, useState } from 'react';

import DatabaseIcons from '../icons/DatabaseIcons';
import { useIntl } from '../i18n';
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
  const keyword = query.trim().toLowerCase();

  const dataSources = useMemo(
    () =>
      dataSourceGroups.flatMap((group) =>
        group.datasourceList.map((item) => ({
          ...item,
          searchText: [item.dbType, item.connectorType, item.type]
            .filter(Boolean)
            .join(' ')
            .toLowerCase(),
        })),
      ),
    [dataSourceGroups],
  );

  const filteredDataSources = useMemo(
    () =>
      dataSources.filter(
        (item) => !keyword || item.searchText.includes(keyword),
      ),
    [dataSources, keyword],
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0">
        <div className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
          {intl.formatMessage({ id: 'pages.datasource.typeSelector.title' })}
        </div>

        <div className="relative">
          <Search
            aria-hidden="true"
            size={14}
            strokeWidth={1.8}
            className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-[#98A2B3]"
          />
          <Input
            placeholder={intl.formatMessage({
              id: 'pages.datasource.typeSelector.searchPlaceholder',
            })}
            value={query}
            className="pl-9 pr-9"
            onChange={(event) => setQuery(event.target.value)}
          />
          {query ? (
            <Button
              variant="ghost"
              size="small"
              type="button"
              aria-label={intl.formatMessage({
                id: 'pages.datasource.typeSelector.clearSearch',
              })}
              className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[#98A2B3]"
              onClick={() => setQuery('')}
            >
              <X size={13} strokeWidth={1.8} />
            </Button>
          ) : null}
        </div>
      </div>

      <section className="mt-5 flex min-h-0 flex-1 flex-col">
        <div className="mb-2 flex shrink-0 items-center justify-between">
          <span className="text-xs font-semibold text-[#161823]">
            {intl.formatMessage({
              id: 'pages.datasource.typeSelector.supported',
            })}
          </span>
          <span className="text-[11px] text-[#98A2B3]">
            {filteredDataSources.length}
          </span>
        </div>

        {filteredDataSources.length === 0 ? (
          <div className="flex min-h-0 flex-1 items-center justify-center px-5 py-8">
            <Empty
              description={intl.formatMessage({
                id: 'pages.datasource.typeSelector.empty',
              })}
            />
          </div>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {filteredDataSources.map((item) => (
                <Button
                  key={item.dbType}
                  type="button"
                  disabled={item.disabled}
                  className="!h-auto !min-h-[46px] !min-w-0 !justify-start !px-3 !py-2 !text-left"
                  onClick={() => onSelect(item.dbType)}
                >
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white">
                    <DatabaseIcons
                      dbType={item.dbType}
                      width="15px"
                      height="15px"
                    />
                  </span>
                  <span
                    className="min-w-0 flex-1 truncate text-[13px] font-medium"
                    title={item.dbType}
                  >
                    {item.dbType === 'POSTGRE_SQL'
                      ? 'PostgreSQL'
                      : item.dbType === 'MYSQL'
                        ? 'MySQL'
                        : item.dbType === 'ORACLE'
                          ? 'Oracle'
                          : item.dbType}
                  </span>
                </Button>
              ))}
            </div>
          </div>
        )}
      </section>
    </div>
  );
};

export default DataSourceTypeSelector;
