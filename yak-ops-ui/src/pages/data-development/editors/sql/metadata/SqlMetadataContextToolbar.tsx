import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { useIntl } from '@umijs/max';
import { Popover, Spin, message } from 'antd';
import { ChevronDown, Database, Layers3, Search, Server } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import type { DevelopmentId } from '../../../types';
import {
  selectSqlDatabaseContext,
  selectSqlDataSourceContext,
  selectSqlSchemaContext,
  useSqlMetadataContext,
} from './sqlMetadataContextStore';
import {
  getSqlDataSourceBinding,
  listSqlDataSources,
  type SqlDataSourceOption,
} from './sqlMetadataService';

interface SqlMetadataContextToolbarProps {
  nodeId: DevelopmentId;
}

interface ContextPickerItem {
  value: string;
  label: string;
  searchText?: string;
  icon?: ReactNode;
}

interface ContextPickerProps {
  ariaLabel: string;
  value?: string;
  displayValue?: string;
  placeholder: string;
  icon: ReactNode;
  items: ContextPickerItem[];
  loading?: boolean;
  disabled?: boolean;
  popupWidth?: number;
  minWidthClassName?: string;
  onSelect: (value: string) => void;
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const ContextPicker = ({
  ariaLabel,
  value,
  displayValue,
  placeholder,
  icon,
  items,
  loading = false,
  disabled = false,
  popupWidth = 210,
  minWidthClassName = 'min-w-[108px]',
  onSelect,
}: ContextPickerProps) => {
  const intl = useIntl();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const normalizedKeyword = keyword.trim().toLowerCase();

  const filteredItems = useMemo(
    () =>
      normalizedKeyword
        ? items.filter((item) =>
            `${item.label} ${item.searchText || ''}`
              .toLowerCase()
              .includes(normalizedKeyword),
          )
        : items,
    [items, normalizedKeyword],
  );

  const popup = (
    <div style={{ width: popupWidth }}>
      <div className="flex h-8 items-center gap-1.5 border-b border-[#e5e7eb] px-2.5">
        <Search size={13} strokeWidth={1.8} className="shrink-0 text-[#6b7280]" />
        <input
          autoFocus
          value={keyword}
          placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.search' })}
          onChange={(event) => setKeyword(event.target.value)}
          className="h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-[12px] text-[#30323b] outline-none placeholder:text-[#9ca3af]"
        />
      </div>

      <div className="max-h-[240px] overflow-y-auto p-1">
        {loading ? (
          <div className="flex h-10 items-center justify-center">
            <Spin size="small" />
          </div>
        ) : filteredItems.length ? (
          filteredItems.map((item) => {
            const selected = item.value === value;
            return (
              <button
                key={item.value}
                type="button"
                title={item.label}
                onClick={() => {
                  onSelect(item.value);
                  setOpen(false);
                  setKeyword('');
                }}
                className={[
                  'flex h-8 w-full items-center gap-2 rounded-[2px] px-2 text-left text-[12px] transition-colors hover:bg-[#f5f5f6]',
                  selected ? 'text-[#161823]' : 'text-[#30323b]',
                ].join(' ')}
              >
                <span className="flex h-4 w-4 shrink-0 items-center justify-center">
                  {item.icon || icon}
                </span>
                <span className="min-w-0 flex-1 truncate">{item.label}</span>
              </button>
            );
          })
        ) : (
          <div className="flex h-10 items-center justify-center text-[11px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.noMatch' })}
          </div>
        )}
      </div>
    </div>
  );

  return (
    <Popover
      trigger="click"
      placement="bottomLeft"
      arrow={false}
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled) return;
        setOpen(nextOpen);
        if (!nextOpen) setKeyword('');
      }}
      overlayClassName="sql-metadata-context-popover"
      content={popup}
    >
      <button
        type="button"
        aria-label={ariaLabel}
        title={
          disabled
            ? intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.sqlMetadata.fixedByConnection' },
                { label: ariaLabel },
              )
            : undefined
        }
        disabled={disabled}
        className={[
          'flex h-6 max-w-[176px] items-center gap-1.5 rounded-[3px] px-1.5 text-[12px] outline-none transition-colors',
          minWidthClassName,
          disabled
            ? 'cursor-not-allowed bg-[#f5f6f7] text-[#a4a9b2]'
            : open || displayValue
              ? 'bg-[#f1f2f4] text-[#161823]'
              : 'text-[#30323b] hover:bg-[#f5f5f6]',
        ].join(' ')}
      >
        <span className="flex h-4 w-4 shrink-0 items-center justify-center">{icon}</span>
        <span
          className={[
            'min-w-0 flex-1 truncate text-left',
            disabled
              ? 'text-[#8f959f]'
              : displayValue
                ? 'text-[#30323b]'
                : 'text-[#7b808a]',
          ].join(' ')}
        >
          {displayValue || placeholder}
        </span>
        {!disabled ? (
          <ChevronDown
            size={12}
            strokeWidth={1.8}
            className={[
              'shrink-0 transition-transform duration-150',
              open ? 'rotate-180' : '',
            ].join(' ')}
          />
        ) : null}
      </button>
    </Popover>
  );
};

const SqlMetadataContextToolbar = ({
  nodeId,
}: SqlMetadataContextToolbarProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const context = useSqlMetadataContext(nodeId);
  const [dataSources, setDataSources] = useState<SqlDataSourceOption[]>([]);
  const [dataSourceLoading, setDataSourceLoading] = useState(false);
  const [bindingLoading, setBindingLoading] = useState(false);

  const text = (id: string) => intlRef.current.formatMessage({ id });

  useEffect(() => {
    let active = true;
    setDataSourceLoading(true);
    listSqlDataSources()
      .then((values) => {
        if (active) setDataSources(values || []);
      })
      .catch((error) => {
        if (active) {
          message.error(
            errorText(
              error,
              text('pages.dataDevelopment.editor.sqlMetadata.queryDataSourceFailed'),
            ),
          );
        }
      })
      .finally(() => {
        if (active) setDataSourceLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!context.dataSourceId || context.dataSourceName || !dataSources.length) return;
    const selected = dataSources.find((item) => item.value === context.dataSourceId);
    if (!selected) return;
    selectSqlDataSourceContext(nodeId, {
      id: selected.value,
      name: selected.label,
      dbType: selected.dbType,
    });
  }, [context.dataSourceId, context.dataSourceName, dataSources, nodeId]);

  useEffect(() => {
    let active = true;
    if (!context.dataSourceId) {
      setBindingLoading(false);
      return () => {
        active = false;
      };
    }

    setBindingLoading(true);
    getSqlDataSourceBinding(context.dataSourceId)
      .then((binding) => {
        if (!active) return;
        selectSqlDatabaseContext(nodeId, binding.database);
        selectSqlSchemaContext(nodeId, binding.schema);
      })
      .catch((error) => {
        if (!active) return;
        selectSqlDatabaseContext(nodeId, undefined);
        message.error(
          errorText(
            error,
            text('pages.dataDevelopment.editor.sqlMetadata.bindingFailed'),
          ),
        );
      })
      .finally(() => {
        if (active) setBindingLoading(false);
      });

    return () => {
      active = false;
    };
  }, [context.dataSourceId, nodeId]);

  const normalizedDbType = context.dbType?.trim().toUpperCase();
  const showSchemaPicker = Boolean(
    context.dataSourceId &&
      normalizedDbType &&
      !['MYSQL', 'MARIADB', 'SQLITE'].includes(normalizedDbType),
  );

  const dataSourceItems = dataSources.map((item) => ({
    value: item.value,
    label: `@${item.label}`,
    searchText: item.dbType,
    icon: (
      <Server
        size={13}
        strokeWidth={1.8}
        className="text-[var(--yak-brand-color)]"
      />
    ),
  }));

  const databasePlaceholder = !context.dataSourceId
    ? '<database>'
    : bindingLoading
      ? intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.loading' })
      : intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.defaultDatabase' });
  const schemaPlaceholder = bindingLoading
    ? intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.loading' })
    : intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.defaultSchema' });

  return (
    <>
      <div
        className="flex min-w-0 shrink-0 items-center gap-1"
        style={BRAND_CSS_VARIABLES}
      >
        <ContextPicker
          ariaLabel={intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlMetadata.selectDataSource' })}
          value={context.dataSourceId}
          displayValue={context.dataSourceName ? `@${context.dataSourceName}` : undefined}
          placeholder="@datasource"
          icon={
            <Server
              size={13}
              strokeWidth={1.8}
              className="text-[var(--yak-brand-color)]"
            />
          }
          items={dataSourceItems}
          loading={dataSourceLoading}
          popupWidth={210}
          minWidthClassName="min-w-[108px]"
          onSelect={(value) => {
            const selected = dataSources.find((item) => item.value === value);
            if (!selected) return;
            selectSqlDataSourceContext(nodeId, {
              id: selected.value,
              name: selected.label,
              dbType: selected.dbType,
            });
          }}
        />

        <ContextPicker
          ariaLabel="Database"
          value={context.database}
          displayValue={bindingLoading ? undefined : context.database}
          placeholder={databasePlaceholder}
          icon={<Database size={13} strokeWidth={1.8} className="text-[#8f959f]" />}
          items={[]}
          disabled
          minWidthClassName="min-w-[112px]"
          onSelect={() => undefined}
        />

        {showSchemaPicker ? (
          <ContextPicker
            ariaLabel="Schema"
            value={context.schema}
            displayValue={bindingLoading ? undefined : context.schema}
            placeholder={schemaPlaceholder}
            icon={<Layers3 size={13} strokeWidth={1.8} className="text-[#8f959f]" />}
            items={[]}
            disabled
            minWidthClassName="min-w-[104px]"
            onSelect={() => undefined}
          />
        ) : null}
      </div>

      <style>{`
        .sql-metadata-context-popover .ant-popover-inner {
          padding: 0;
          overflow: hidden;
          border: 1px solid #dfe3e8;
          border-radius: 3px;
          box-shadow: 0 4px 12px rgba(16, 24, 40, 0.10);
        }
        .sql-metadata-context-popover .ant-popover-inner-content {
          padding: 0;
        }
      `}</style>
    </>
  );
};

export default SqlMetadataContextToolbar;
