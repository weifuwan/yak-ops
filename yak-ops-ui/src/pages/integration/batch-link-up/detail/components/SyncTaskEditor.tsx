import { Alert, Select } from 'antd';
import type { DataSourceRecord } from '@/services/data-source';

import {
  isAutoCreateTableEnabledForDataSourceType,
  isUpsertEnabledForDataSourceType,
} from '../../connectorProfiles';
import {
  resolveEndpointCapability,
  validateEditorCapabilities,
} from '../capabilities';
import useDataSourceColumns from '../hooks/useDataSourceColumns';
import useDataSourceTables from '../hooks/useDataSourceTables';
import useOfflineConnectorRuntime from '../hooks/useOfflineConnectorRuntime';
import {
  DEFAULT_INCREMENTAL_CONFIG,
  updateEndpointConfig,
  type SyncEditorState,
} from '../model';
import ChannelConfigSection from './ChannelConfigSection';
import FieldMappingSection, { type FieldMappingValue } from './FieldMappingSection';
import MultiTableConfigSection from './MultiTableConfigSection';
import NotificationConfigSection from './NotificationConfigSection';
import ScheduleConfigSection from './ScheduleConfigSection';
import SingleTableConfigSection from './SingleTableConfigSection';
import TaskBasicSection from './TaskBasicSection';

interface SyncTaskEditorProps {
  editor: SyncEditorState;
  dataSources: DataSourceRecord[];
  dataSourceLoading: boolean;
  onChange: (value: SyncEditorState) => void;
}

const normalizeMappings = (value: unknown): FieldMappingValue[] => {
  if (!Array.isArray(value)) return [];
  return value
    .map((item: any) => ({
      source: String(item?.source ?? item?.sourceField ?? '').trim(),
      target: String(item?.target ?? item?.targetField ?? '').trim(),
    }))
    .filter((item) => item.source && item.target);
};

export default function SyncTaskEditor({
  editor,
  dataSources,
  dataSourceLoading,
  onChange,
}: SyncTaskEditorProps) {
  const sourceConfig = editor.source.config || {};
  const sinkConfig = editor.sink.config || {};
  const incremental = editor.incremental || DEFAULT_INCREMENTAL_CONFIG;
  const sourceId = editor.source.dataSourceId;
  const targetId = editor.sink.dataSourceId;
  const mappingColumns = normalizeMappings(editor.mapping?.columns);
  const sinkAutoCreateTableEnabled =
    isAutoCreateTableEnabledForDataSourceType(editor.sink.dbType);
  const sinkUpsertEnabled =
    isUpsertEnabledForDataSourceType(editor.sink.dbType);
  const sinkAutoCreateTable =
    sinkAutoCreateTableEnabled && Boolean(sinkConfig.autoCreateTable);

  const connectorRuntime = useOfflineConnectorRuntime();
  const sourceCapability = resolveEndpointCapability(
    connectorRuntime.snapshot,
    editor.source.connectorId,
    'SOURCE',
  );
  const sinkCapability = resolveEndpointCapability(
    connectorRuntime.snapshot,
    editor.sink.connectorId,
    'SINK',
  );
  const capabilityErrors = validateEditorCapabilities(
    editor,
    connectorRuntime.snapshot,
  );

  const isMongoSource =
    editor.mode === 'GUIDE_SINGLE' &&
    String(editor.source.connectorId || '').toLowerCase() === 'mongodb' &&
    sourceConfig.readMode !== 'sql';
  const isMongoSink =
    editor.mode === 'GUIDE_SINGLE' &&
    String(editor.sink.connectorId || '').toLowerCase() === 'mongodb';

  const sourceCatalog = useDataSourceTables(sourceId, sourceConfig.database);
  const targetCatalog = useDataSourceTables(targetId, sinkConfig.database);
  const sourceColumnRequest = sourceConfig.readMode === 'sql'
    ? sourceConfig.sql?.trim() ? { query: sourceConfig.sql } : undefined
    : sourceConfig.table ? { table_path: sourceConfig.table } : undefined;
  const targetColumnRequest = !sinkAutoCreateTable && !isMongoSink && sinkConfig.table
    ? { table_path: sinkConfig.table }
    : undefined;
  const sourceColumnCatalog = useDataSourceColumns(sourceId, sourceColumnRequest);
  const targetColumnCatalog = useDataSourceColumns(targetId, targetColumnRequest);
  const targetSchemaDerived = Boolean(sinkAutoCreateTable || isMongoSink);
  const primaryKeyCatalog = targetSchemaDerived
    ? sourceColumnCatalog
    : targetColumnCatalog;
  const mappingTargetColumns = targetSchemaDerived
    ? sourceColumnCatalog.columns
    : targetColumnCatalog.columns;
  const mappingTargetLoading = targetSchemaDerived
    ? sourceColumnCatalog.loading
    : targetColumnCatalog.loading;

  const updateSource = (patch: Record<string, any>) => {
    const next = updateEndpointConfig(editor, 'source', patch);
    const changesReadMode = Object.prototype.hasOwnProperty.call(patch, 'readMode');
    const changesTable = Object.prototype.hasOwnProperty.call(patch, 'table');
    onChange(
      changesReadMode || changesTable
        ? {
            ...next,
            incremental: {
              ...incremental,
              enabled: changesReadMode && patch.readMode === 'sql'
                ? false
                : incremental.enabled,
              column: '',
            },
          }
        : next,
    );
  };
  const updateSink = (patch: Record<string, any>) =>
    onChange(updateEndpointConfig(editor, 'sink', patch));
  const updateMapping = (columns: FieldMappingValue[]) =>
    onChange({ ...editor, mapping: { columns } });

  const selectedMongoFields = Array.isArray(sourceConfig.fields)
    ? sourceConfig.fields.filter(Boolean).map(String)
    : [];

  const runtimeNotice = (() => {
    if (connectorRuntime.loading && !connectorRuntime.snapshot) {
      return (
        <Alert
          type="info"
          showIcon
          message="正在读取 Link-Up Connector 能力"
          description="能力确认完成前继续使用 Yak Ops 标准配置，保存时会校验当前 Connector 能力。"
        />
      );
    }

    if (connectorRuntime.error && !connectorRuntime.snapshot) {
      return (
        <Alert
          type="warning"
          showIcon
          message="暂时无法读取 Link-Up Connector 能力"
          description={`${connectorRuntime.error}。编辑器保留 Yak Ops 标准配置，保存时会再次校验。`}
        />
      );
    }

    if (connectorRuntime.snapshot && !connectorRuntime.snapshot.reachable) {
      return (
        <Alert
          type="warning"
          showIcon
          message="Link-Up Worker 当前不可达"
          description={
            connectorRuntime.snapshot.errorMessage ||
            '当前仅展示 Yak Ops 标准配置；Worker 恢复后会重新确认 Connector 能力。'
          }
        />
      );
    }

    if (capabilityErrors.length > 0) {
      return (
        <Alert
          type="error"
          showIcon
          message="当前 Connector 能力与任务配置不匹配"
          description={capabilityErrors.join('；')}
        />
      );
    }

    return null;
  })();

  const mongoFieldSelector = isMongoSource && sourceConfig.table ? (
    <div className="rounded-lg border border-[#eaecf0] bg-white p-3.5">
      <div className="mb-2 text-[12px] font-semibold text-[#344054]">
        来源字段
      </div>
      <Select
        mode="multiple"
        allowClear
        showSearch
        variant="filled"
        className="w-full"
        loading={sourceColumnCatalog.loading}
        disabled={!sourceId || !sourceConfig.table}
        value={selectedMongoFields}
        placeholder="不选择则同步全部自动发现字段"
        options={sourceColumnCatalog.columns.map((column) => ({
          label: column.description
            ? `${column.label} · ${column.description}`
            : column.label,
          value: column.value,
        }))}
        optionFilterProp="label"
        onChange={(fields) => updateSource({ fields })}
      />
      <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
        MongoDB 字段类型由 Catalog 自动采样推断，只选择字段名即可；嵌套字段可直接选择 address.city 这类路径。
      </div>
    </div>
  ) : null;

  return (
    <div className="space-y-5">
      {runtimeNotice}

      <div id="task-basic" className="scroll-mt-6">
        <TaskBasicSection
          editor={editor}
          dataSources={dataSources}
          dataSourceLoading={dataSourceLoading}
          onChange={onChange}
        />
      </div>

      <div id="sync-config" className="scroll-mt-6">
        {editor.mode === 'GUIDE_MULTI' ? (
          <MultiTableConfigSection
            sourceConfig={sourceConfig}
            sinkConfig={sinkConfig}
            sinkCapability={sinkCapability}
            autoCreateTableEnabled={sinkAutoCreateTableEnabled}
            upsertEnabled={sinkUpsertEnabled}
            sourceTables={sourceCatalog.tables}
            sourceLoading={sourceCatalog.loading}
            sourceReady={Boolean(sourceId)}
            targetReady={Boolean(targetId)}
            sourceExtraParameters={null}
            sinkExtraParameters={null}
            onSourceTableSearch={sourceCatalog.search}
            onSourceChange={updateSource}
            onSinkChange={updateSink}
          />
        ) : (
          <SingleTableConfigSection
            sourceDataSourceId={sourceId}
            sourceConnectorId={editor.source.connectorId}
            sourceConfig={sourceConfig}
            sinkConfig={sinkConfig}
            incremental={incremental}
            sourceCapability={sourceCapability}
            sinkCapability={sinkCapability}
            autoCreateTableEnabled={sinkAutoCreateTableEnabled}
            upsertEnabled={sinkUpsertEnabled}
            sourceTables={sourceCatalog.tables}
            targetTables={targetCatalog.tables}
            sourceLoading={sourceCatalog.loading}
            targetLoading={targetCatalog.loading}
            primaryKeyOptions={primaryKeyCatalog.columns}
            primaryKeyLoading={primaryKeyCatalog.loading}
            incrementalColumnOptions={sourceColumnCatalog.columns}
            incrementalColumnLoading={sourceColumnCatalog.loading}
            sourceReady={Boolean(sourceId)}
            targetReady={Boolean(targetId)}
            allowCustomTargetName={isMongoSink}
            sourceExtraParameters={mongoFieldSelector}
            sinkExtraParameters={null}
            onSourceTableSearch={sourceCatalog.search}
            onTargetTableSearch={targetCatalog.search}
            onSourceChange={(patch) =>
              updateSource(
                isMongoSource && Object.prototype.hasOwnProperty.call(patch, 'table')
                  ? { ...patch, fields: [] }
                  : patch,
              )
            }
            onSinkChange={updateSink}
            onIncrementalChange={(patch) =>
              onChange({
                ...editor,
                incremental: { ...incremental, ...patch },
              })
            }
          />
        )}
      </div>

      <div id="runtime-params" className="scroll-mt-6">
        <ChannelConfigSection
          editor={editor}
          sinkConfig={sinkConfig}
          sinkCapability={sinkCapability}
          onChange={onChange}
          onSinkChange={updateSink}
        />
      </div>

      <div id="schedule-config" className="scroll-mt-6">
        <ScheduleConfigSection editor={editor} onChange={onChange} />
      </div>

      <div id="notification-config" className="scroll-mt-6">
        <NotificationConfigSection editor={editor} onChange={onChange} />
      </div>

      {editor.mode === 'GUIDE_SINGLE' ? (
        <div id="field-mapping" className="scroll-mt-6">
          <FieldMappingSection
            value={mappingColumns}
            onChange={updateMapping}
            sourceColumns={sourceColumnCatalog.columns}
            targetColumns={mappingTargetColumns}
            sourceLoading={sourceColumnCatalog.loading}
            targetLoading={mappingTargetLoading}
            sourceReady={Boolean(sourceId && sourceColumnRequest)}
            targetReady={Boolean(targetId && (targetSchemaDerived || targetColumnRequest))}
            targetDerived={targetSchemaDerived}
          />
        </div>
      ) : null}
    </div>
  );
}
