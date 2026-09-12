import {
  DatabaseOutlined,
  EyeOutlined,
  ExportOutlined,
} from '@ant-design/icons';
import {
  Button,
  Input,
  Segmented,
  Select,
  Spin,
  Switch,
} from 'antd';
import { useState, type ChangeEvent, type ReactNode } from 'react';

import {
  allowsCapability,
  allowsOverwrite,
  CONNECTOR_CAPABILITY,
  type EndpointCapabilityState,
} from '../capabilities';
import type { DataSourceColumnOption } from '../hooks/useDataSourceColumns';
import type { SyncIncremental } from '../model';
import EditorSection from './EditorSection';
import SingleTablePreviewModal from './SingleTablePreviewModal';

interface SingleTableConfigSectionProps {
  sourceDataSourceId?: string | number;
  sourceConnectorId?: string;
  sourceConfig: Record<string, any>;
  sinkConfig: Record<string, any>;
  incremental: SyncIncremental;
  sourceCapability: EndpointCapabilityState;
  sinkCapability: EndpointCapabilityState;
  autoCreateTableEnabled: boolean;
  upsertEnabled: boolean;
  sourceTables: string[];
  targetTables: string[];
  sourceLoading: boolean;
  targetLoading: boolean;
  primaryKeyOptions: DataSourceColumnOption[];
  primaryKeyLoading: boolean;
  incrementalColumnOptions: DataSourceColumnOption[];
  incrementalColumnLoading: boolean;
  sourceReady: boolean;
  targetReady: boolean;
  allowCustomTargetName?: boolean;
  sourceExtraParameters: ReactNode;
  sinkExtraParameters: ReactNode;
  onSourceTableSearch: (keyword: string) => void;
  onTargetTableSearch: (keyword: string) => void;
  onSourceChange: (patch: Record<string, any>) => void;
  onSinkChange: (patch: Record<string, any>) => void;
  onIncrementalChange: (patch: Partial<SyncIncremental>) => void;
}

interface EndpointPanelProps {
  icon: ReactNode;
  title: string;
  children: ReactNode;
}

function EndpointPanel({ icon, title, children }: EndpointPanelProps) {
  return (
    <div className="rounded-xl border border-[#e8eaee] bg-[#fcfcfd] p-5">
      <div className="flex items-center gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-brand-color-soft-hover)] text-[var(--yak-brand-color)]">
          {icon}
        </span>
        <div className="text-[14px] font-semibold text-[#182230]">{title}</div>
      </div>
      <div className="mt-5 space-y-4">{children}</div>
    </div>
  );
}

function FieldLabel({ children, required = false }: { children: ReactNode; required?: boolean }) {
  return (
    <div className="mb-2 text-[12px] font-medium text-[#475467]">
      {children}
      {required ? <span className="ml-1 text-[var(--yak-brand-color)]">*</span> : null}
    </div>
  );
}

const splitPrimaryKeys = (value: unknown): string[] =>
  String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

export default function SingleTableConfigSection({
  sourceDataSourceId,
  sourceConnectorId,
  sourceConfig,
  sinkConfig,
  incremental,
  sourceCapability,
  sinkCapability,
  autoCreateTableEnabled,
  upsertEnabled,
  sourceTables,
  targetTables,
  sourceLoading,
  targetLoading,
  primaryKeyOptions,
  primaryKeyLoading,
  incrementalColumnOptions,
  incrementalColumnLoading,
  sourceReady,
  targetReady,
  allowCustomTargetName = false,
  sourceExtraParameters,
  sinkExtraParameters,
  onSourceTableSearch,
  onTargetTableSearch,
  onSourceChange,
  onSinkChange,
  onIncrementalChange,
}: SingleTableConfigSectionProps) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const sourceReadMode = sourceConfig.readMode === 'sql' ? 'sql' : 'table';
  const supportsCustomSql = allowsCapability(
    sourceCapability,
    CONNECTOR_CAPABILITY.CUSTOM_SQL,
  );
  const supportsAutoCreate =
    autoCreateTableEnabled &&
    allowsCapability(
      sinkCapability,
      CONNECTOR_CAPABILITY.AUTO_CREATE_TABLE,
    );
  const effectiveAutoCreateTable =
    supportsAutoCreate && Boolean(sinkConfig.autoCreateTable);
  const supportsUpsert =
    upsertEnabled &&
    allowsCapability(
      sinkCapability,
      CONNECTOR_CAPABILITY.UPSERT,
    );
  const supportsOverwrite = allowsOverwrite(sinkCapability);
  const supportsIncremental =
    String(sourceConnectorId || '').toLowerCase() === 'jdbc' &&
    sourceReadMode === 'table' &&
    supportsUpsert;
  const cursorColumns = incrementalColumnOptions.filter((option) => {
    const type = String(option.typeName || '').toUpperCase();
    return /(DATE|TIME|CHAR|TEXT)/.test(type);
  });
  const previewDisabled =
    !sourceDataSourceId ||
    (sourceReadMode === 'sql'
      ? !String(sourceConfig.sql || '').trim()
      : !String(sourceConfig.table || '').trim());

  const readModeOptions = [
    { label: '选择数据表', value: 'table' },
    ...(supportsCustomSql
      ? [{ label: '自定义 SQL', value: 'sql' }]
      : sourceReadMode === 'sql'
        ? [{ label: '自定义 SQL（当前不支持）', value: 'sql', disabled: true }]
        : []),
  ];

  const currentWriteMode = String(sinkConfig.writeMode || 'append').toLowerCase();
  const writeModeOptions = [
    { label: '追加写入 Append', value: 'append' },
    ...(supportsOverwrite
      ? [{ label: '覆盖写入 Overwrite', value: 'overwrite' }]
      : currentWriteMode === 'overwrite'
        ? [{ label: '覆盖写入 Overwrite（当前不支持）', value: 'overwrite', disabled: true }]
        : []),
    ...(supportsUpsert
      ? [{ label: '主键更新 Upsert', value: 'upsert' }]
      : currentWriteMode === 'upsert'
        ? [{ label: '主键更新 Upsert（当前不支持）', value: 'upsert', disabled: true }]
        : []),
  ];

  return (
    <EditorSection title="单表同步配置">
      <div className="grid grid-cols-2 items-start gap-5 max-lg:grid-cols-1">
        <EndpointPanel icon={<DatabaseOutlined />} title="Source 来源配置">
          <div>
            <FieldLabel>读取方式</FieldLabel>
            <Segmented
              block
              value={sourceReadMode}
              options={readModeOptions}
              onChange={(readMode: string | number) =>
                onSourceChange({
                  readMode,
                  ...(readMode === 'table' ? { sql: '' } : { table: '' }),
                })
              }
            />
          </div>

          {sourceReadMode === 'sql' ? (
            <div>
              <FieldLabel required>查询 SQL</FieldLabel>
              <Input.TextArea
                rows={10}
                variant="filled"
                value={sourceConfig.sql || ''}
                placeholder="SELECT * FROM source_table"
                className="font-mono"
                onChange={(event: ChangeEvent<HTMLTextAreaElement>) => onSourceChange({ sql: event.target.value })}
              />
              {!supportsCustomSql ? (
                <div className="mt-1.5 text-[11px] leading-5 text-[#b54708]">
                  当前 Source Connector 未声明 CUSTOM_SQL，请切换为数据表读取后再保存。
                </div>
              ) : null}
            </div>
          ) : (
            <div>
              <FieldLabel required>来源表</FieldLabel>
              <Select
                showSearch
                variant="filled"
                disabled={!sourceReady}
                value={sourceConfig.table || undefined}
                options={sourceTables.map((table) => ({ label: table, value: table }))}
                loading={sourceLoading}
                filterOption={false}
                notFoundContent={sourceLoading ? <Spin size="small" /> : undefined}
                placeholder={sourceReady ? '输入表名搜索' : '请先选择来源数据源'}
                className="w-full"
                onSearch={onSourceTableSearch}
                onDropdownVisibleChange={(open) => {
                  if (open) onSourceTableSearch('');
                }}
                onChange={(table: string) => onSourceChange({ table })}
              />
            </div>
          )}

          <Button
            block
            icon={<EyeOutlined />}
            disabled={previewDisabled}
            onClick={() => setPreviewOpen(true)}
          >
            数据预览
          </Button>

          {sourceExtraParameters}
        </EndpointPanel>

        <EndpointPanel icon={<ExportOutlined />} title="Sink 目标配置">
          {supportsAutoCreate || sinkConfig.autoCreateTable ? (
            <div className="rounded-lg bg-[#f5f5f6] px-3.5 py-3">
              <div className="flex items-center justify-between">
                <div className="text-[12px] font-medium text-[#475467]">自动创建目标表</div>
                <Switch
                  checked={Boolean(sinkConfig.autoCreateTable)}
                  onChange={(autoCreateTable: boolean) =>
                    onSinkChange({
                      autoCreateTable,
                      table: '',
                      targetTableName: '',
                      primaryKey: '',
                    })
                  }
                />
              </div>
              {!supportsAutoCreate && sinkConfig.autoCreateTable ? (
                <div className="mt-2 text-[11px] leading-5 text-[#b54708]">
                  {autoCreateTableEnabled
                    ? '当前 Sink Connector 未声明 AUTO_CREATE_TABLE，请关闭后选择已有目标表。'
                    : '当前目标数据源 Stage 1 仅支持写入已有表，请关闭自动建表后选择目标表。'}
                </div>
              ) : null}
            </div>
          ) : null}

          {effectiveAutoCreateTable ? (
            <div>
              <FieldLabel required>目标表名</FieldLabel>
              <Input
                variant="filled"
                disabled={!targetReady}
                value={sinkConfig.targetTableName || ''}
                placeholder={targetReady ? '请输入需要创建的目标表名' : '请先选择目标数据源'}
                onChange={(event: ChangeEvent<HTMLInputElement>) => onSinkChange({ targetTableName: event.target.value })}
              />
            </div>
          ) : allowCustomTargetName ? (
            <div>
              <FieldLabel required>目标 Collection</FieldLabel>
              <Input
                variant="filled"
                disabled={!targetReady}
                value={sinkConfig.table || ''}
                placeholder={targetReady ? '输入已有或新的 Collection 名称' : '请先选择目标数据源'}
                onChange={(event: ChangeEvent<HTMLInputElement>) =>
                  onSinkChange({ table: event.target.value, primaryKey: '' })
                }
              />
              <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
                MongoDB 会在首次成功 INSERT 时自然创建不存在的 Collection；这里不启用 Link-Up AUTO_CREATE_TABLE 语义。
              </div>
            </div>
          ) : (
            <div>
              <FieldLabel required>目标表</FieldLabel>
              <Select
                showSearch
                variant="filled"
                disabled={!targetReady}
                value={sinkConfig.table || undefined}
                options={targetTables.map((table) => ({ label: table, value: table }))}
                loading={targetLoading}
                filterOption={false}
                notFoundContent={targetLoading ? <Spin size="small" /> : undefined}
                placeholder={targetReady ? '输入表名搜索' : '请先选择目标数据源'}
                className="w-full"
                onSearch={onTargetTableSearch}
                onDropdownVisibleChange={(open) => {
                  if (open) onTargetTableSearch('');
                }}
                onChange={(table: string) => onSinkChange({ table, primaryKey: '' })}
              />
            </div>
          )}

          <div>
            <FieldLabel required>写入模式</FieldLabel>
            <Select
              variant="filled"
              value={currentWriteMode}
              options={writeModeOptions}
              className="w-full"
              onChange={(writeMode: string) =>
                onSinkChange({
                  writeMode,
                  ...(writeMode === 'upsert' ? {} : { primaryKey: '' }),
                })
              }
            />
            {!supportsOverwrite && currentWriteMode === 'overwrite' ? (
              <div className="mt-1.5 text-[11px] leading-5 text-[#b54708]">
                当前 Native Sink 不支持覆盖写入，请选择 Append
                {supportsUpsert ? ' 或 Upsert' : ''}。
              </div>
            ) : null}
            {!supportsUpsert && currentWriteMode === 'upsert' ? (
              <div className="mt-1.5 text-[11px] leading-5 text-[#b54708]">
                {upsertEnabled
                  ? '当前 Sink Connector 未声明 UPSERT，请选择其他写入模式。'
                  : '当前目标数据源不支持 Upsert/MERGE，请选择 Append 或 Overwrite。'}
              </div>
            ) : null}
          </div>

          {currentWriteMode === 'upsert' ? (
            <div>
              <FieldLabel required>主键字段</FieldLabel>
              <Select
                mode="tags"
                allowClear
                showSearch
                variant="filled"
                value={splitPrimaryKeys(sinkConfig.primaryKey)}
                loading={primaryKeyLoading}
                disabled={!targetReady}
                options={primaryKeyOptions.map((option) => ({
                  label: option.description
                    ? `${option.label} · ${option.description}`
                    : option.label,
                  value: option.value,
                }))}
                placeholder={
                  primaryKeyLoading
                    ? '正在加载字段'
                    : primaryKeyOptions.length > 0
                      ? '请选择一个或多个主键字段'
                      : '请先选择来源或目标表'
                }
                optionFilterProp="label"
                className="w-full"
                onChange={(primaryKeys: string[]) =>
                  onSinkChange({ primaryKey: primaryKeys.join(',') })
                }
              />
            </div>
          ) : null}

          {sinkExtraParameters}
        </EndpointPanel>
      </div>

      <div className="mt-5 rounded-xl border border-[#e8eaee] bg-[#fcfcfd] p-5">
        <div className="flex items-start justify-between gap-6">
          <div>
            <div className="text-[14px] font-semibold text-[#182230]">
              全量 + 游标增量
            </div>
            <div className="mt-1 text-[12px] leading-5 text-[#667085]">
              首次同步全表并记录来源当前 MAX；后续只同步 (已提交游标, 本次 MAX]。仅成功后推进游标，失败重试复用原上界。
            </div>
          </div>
          <Switch
            checked={incremental.enabled}
            disabled={!supportsIncremental}
            onChange={(enabled) => {
              onIncrementalChange({
                enabled,
                ...(enabled ? {} : { column: '' }),
              });
              if (enabled && currentWriteMode !== 'upsert') {
                onSinkChange({ writeMode: 'upsert' });
              }
            }}
          />
        </div>
        {!supportsIncremental ? (
          <div className="mt-3 text-[11px] leading-5 text-[#b54708]">
            该能力要求 JDBC 单表来源，以及支持 Upsert 的目标 Connector。
          </div>
        ) : null}
        {incremental.enabled ? (
          <div className="mt-4 max-w-[520px]">
            <FieldLabel required>增量游标字段</FieldLabel>
            <Select
              showSearch
              variant="filled"
              className="w-full"
              value={incremental.column || undefined}
              loading={incrementalColumnLoading}
              disabled={!sourceConfig.table}
              options={cursorColumns.map((option) => ({
                label: option.description
                  ? `${option.label} · ${option.description}`
                  : option.label,
                value: option.value,
              }))}
              optionFilterProp="label"
              placeholder="选择日期、时间戳或 ISO 字符时间字段"
              onChange={(column) => onIncrementalChange({ column })}
            />
            <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
              字符字段必须使用可按字典序排序的 ISO 时间格式；目标端须选择 Upsert 并配置主键。
            </div>
          </div>
        ) : null}
      </div>

      <SingleTablePreviewModal
        open={previewOpen}
        dataSourceId={sourceDataSourceId}
        sourceConfig={sourceConfig}
        onCancel={() => setPreviewOpen(false)}
      />
    </EditorSection>
  );
}
