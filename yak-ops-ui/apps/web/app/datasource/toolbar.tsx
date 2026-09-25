import {
  Button,
  Combobox,
  ComboboxContent,
  ComboboxEmptyState,
  ComboboxInput,
  ComboboxItem,
  Input,
  Tabs,
  TabsList,
  TabsTab,
} from '@yak-ops/yak-ui';
import { Search, X } from 'lucide-react';

import {
  COMMON_DB_OPTIONS,
  getDataSourceEnvironmentTabs,
} from './constants';
import DatabaseIcons from './icons/DatabaseIcons';
import { useIntl } from './i18n';

interface DataSourceToolbarProps {
  environment?: string;
  dbType?: string;
  keyword: string;
  hasActiveFilters: boolean;
  onEnvironmentChange: (value?: string) => void;
  onDbTypeChange: (value?: string) => void;
  onKeywordChange: (value: string) => void;
  onReset: () => void;
}

const dbTypeLabel = (value: string) =>
  COMMON_DB_OPTIONS.find((option) => option.value === value)?.label || value;

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
  hasActiveFilters,
  onEnvironmentChange,
  onDbTypeChange,
  onKeywordChange,
  onReset,
}: DataSourceToolbarProps) => {
  const intl = useIntl();
  const environmentTabs = getDataSourceEnvironmentTabs(intl);

  return (
    <section className="flex min-h-9 items-end justify-between gap-6 border-b border-solid border-[#eceef2] max-xl:flex-col max-xl:items-stretch max-xl:gap-3">
      <div className="flex items-end">
        <Tabs
          value={environment || 'all'}
          onValueChange={(key) => {
            const target = environmentTabs.find((item) => item.key === key);
            onEnvironmentChange(target?.value);
          }}
        >
          <TabsList className="gap-8 border-b-0">
            {environmentTabs.map((item) => (
              <TabsTab
                key={item.key}
                value={item.key}
                className="px-0 pb-[10px] pt-0 text-[13px]"
              >
                {item.label}
              </TabsTab>
            ))}
          </TabsList>
        </Tabs>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2 pb-[5px] max-xl:justify-start">
        <div className="relative w-[188px]">
          <Combobox<string>
            value={dbType ?? null}
            itemToStringLabel={(value) => dbTypeLabel(value)}
            onValueChange={(value) => onDbTypeChange(value ?? undefined)}
          >
            <ComboboxInput
              placeholder={intl.formatMessage({
                id: 'pages.datasource.toolbar.typePlaceholder',
              })}
              className={dbType ? 'pr-9 text-xs' : 'text-xs'}
            />
            {dbType ? (
              <Button
                variant="ghost"
                size="small"
                type="button"
                aria-label="Clear datasource type"
                className="absolute right-1 top-1/2 z-10 h-7 w-7 -translate-y-1/2 p-0 text-[#98a2b3]"
                onClick={() => onDbTypeChange(undefined)}
              >
                <X size={13} />
              </Button>
            ) : null}
            <ComboboxContent className="min-w-[188px]">
              {COMMON_DB_OPTIONS.map((option) => (
                <ComboboxItem key={option.value} value={option.value}>
                  <DbTypeLabel value={option.value} />
                </ComboboxItem>
              ))}
              <ComboboxEmptyState>
                {intl.formatMessage({
                  id: 'pages.datasource.typeSelector.empty',
                })}
              </ComboboxEmptyState>
            </ComboboxContent>
          </Combobox>
        </div>

        <div className="relative w-[292px] max-md:w-[220px]">
          <Search
            size={15}
            strokeWidth={1.8}
            className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-[#8f949e]"
          />
          <Input
            value={keyword}
            className="pl-9 pr-9 text-xs"
            placeholder={intl.formatMessage({
              id: 'pages.datasource.toolbar.searchPlaceholder',
            })}
            onChange={(event) => onKeywordChange(event.target.value)}
          />
          {keyword ? (
            <Button
              variant="ghost"
              size="small"
              type="button"
              aria-label="Clear search"
              className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[#98a2b3]"
              onClick={() => onKeywordChange('')}
            >
              <X size={13} />
            </Button>
          ) : null}
        </div>

        {hasActiveFilters ? (
          <Button
            variant="ghost"
            size="small"
            className="h-9 px-2.5 text-[12px] text-[#777c86]"
            onClick={onReset}
          >
            {intl.formatMessage({ id: 'pages.datasource.toolbar.reset' })}
          </Button>
        ) : null}
      </div>
    </section>
  );
};

export default DataSourceToolbar;
