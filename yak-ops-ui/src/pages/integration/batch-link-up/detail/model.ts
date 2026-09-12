import type { DataSourceRecord } from '@/services/data-source';
import { API_SUCCESS_CODE } from '@/services/http/response';

import { connectorIdForDataSourceType } from './form-schema/valueAdapter';

export type SyncMode = 'GUIDE_SINGLE' | 'GUIDE_MULTI';
export type EndpointKind = 'source' | 'sink';
export type SyncNotificationRecipientType =
  | 'PROJECT_OWNER'
  | 'EXPLICIT_USERS';

export interface SyncTaskBasic {
  jobName: string;
  jobDesc: string;
  mode: SyncMode;
}

export interface SyncEndpoint {
  connectorId: string;
  pluginName: string;
  dbType: string;
  dataSourceId: string;
  config: Record<string, any>;
}

export interface SyncChannel {
  parallelism: number;
  speedLimitEnabled: boolean;
  recordsPerSecond: number;
  dirtyDataPolicy: 'stop' | 'skip';
  dirtyDataLimit: number;
}

export interface SyncColumnMapping {
  source: string;
  target: string;
}

export interface SyncMapping {
  columns: SyncColumnMapping[];
}

export interface SyncIncremental {
  enabled: boolean;
  strategy: 'MAX_TIMESTAMP';
  column: string;
  bootstrapMode: 'SOURCE_CURRENT_MAX';
}

export interface SyncSchedule {
  cron: string;
  enabled: boolean;
  retryOnFailure: boolean;
}

export interface SyncNotification {
  enabled: boolean;
  triggers: string[];
  recipientType: SyncNotificationRecipientType;
  recipientUserIds: number[];
  inAppEnabled: boolean;
  alertEnabled: boolean;
  alertChannelIds: number[];
}

export interface SyncEditorState {
  id: string;
  mode: SyncMode;
  basic: SyncTaskBasic;
  source: SyncEndpoint;
  sink: SyncEndpoint;
  channel: SyncChannel;
  mapping: SyncMapping;
  incremental?: SyncIncremental;
  schedule: SyncSchedule;
  notification: SyncNotification;
  state?: Record<string, any>;
}

export interface CreateSyncTaskValues {
  jobName: string;
  jobDesc?: string;
  mode: SyncMode;
}

export interface CreateSyncEndpoint {
  connectorId: string;
  pluginName: string;
  dbType: string;
}

export const DEFAULT_CHANNEL_CONFIG: SyncChannel = {
  parallelism: 1,
  speedLimitEnabled: false,
  recordsPerSecond: 10000,
  dirtyDataPolicy: 'stop',
  dirtyDataLimit: 0,
};

export const DEFAULT_MAPPING_CONFIG: SyncMapping = {
  columns: [],
};

export const DEFAULT_INCREMENTAL_CONFIG: SyncIncremental = {
  enabled: false,
  strategy: 'MAX_TIMESTAMP',
  column: '',
  bootstrapMode: 'SOURCE_CURRENT_MAX',
};

export const DEFAULT_SCHEDULE_CONFIG: SyncSchedule = {
  cron: '',
  enabled: false,
  retryOnFailure: false,
};

export const DEFAULT_NOTIFICATION_CONFIG: SyncNotification = {
  enabled: true,
  triggers: ['FINAL_FAILURE'],
  recipientType: 'PROJECT_OWNER',
  recipientUserIds: [],
  inAppEnabled: true,
  alertEnabled: false,
  alertChannelIds: [],
};

export const isApiSuccess = (response: any): boolean =>
  response?.code === API_SUCCESS_CODE;

export const responseMessage = (response: any, fallback: string): string =>
  response?.message || response?.msg || fallback;

export const extractGeneratedId = (response: any): string => {
  const data = response?.data;
  const value = data && typeof data === 'object' ? data.id : data;
  return value === undefined || value === null ? '' : String(value);
};

export const extractSavedId = (response: any, fallback: string): string => {
  const data = response?.data;
  const value = data && typeof data === 'object' ? data.id : data;
  return value === undefined || value === null || value === ''
    ? fallback
    : String(value);
};

const normalizeMappingColumns = (value: unknown): SyncColumnMapping[] => {
  if (!Array.isArray(value)) {
    return [];
  }

  const usedSources = new Set<string>();
  const usedTargets = new Set<string>();

  return value.reduce<SyncColumnMapping[]>((result, item: any) => {
    const source = String(
      item?.source ?? item?.sourceField ?? '',
    ).trim();
    const target = String(
      item?.target ?? item?.targetField ?? '',
    ).trim();

    if (
      !source ||
      !target ||
      usedSources.has(source) ||
      usedTargets.has(target)
    ) {
      return result;
    }

    usedSources.add(source);
    usedTargets.add(target);
    result.push({ source, target });
    return result;
  }, []);
};

const normalizeMapping = (raw: any): SyncMapping => {
  const directMapping =
    raw?.mapping && typeof raw.mapping === 'object'
      ? raw.mapping
      : undefined;
  const legacyMapping =
    raw?.source?.config?.mapping &&
    typeof raw.source.config.mapping === 'object'
      ? raw.source.config.mapping
      : undefined;
  const mapping = directMapping || legacyMapping || {};

  return {
    columns: normalizeMappingColumns(mapping.columns),
  };
};

const serializeMapping = (mapping?: SyncMapping): SyncMapping => ({
  columns: normalizeMappingColumns(mapping?.columns),
});

const normalizeSchedule = (raw: any): SyncSchedule => {
  const schedule =
    raw?.schedule && typeof raw.schedule === 'object'
      ? raw.schedule
      : {};
  const cron = String(
    schedule.cron ?? schedule.cronExpression ?? '',
  ).trim();
  const legacyRunType = String(
    schedule.scheduleRunType || '',
  ).toLowerCase();
  const enabled = schedule.enabled !== undefined
    ? Boolean(schedule.enabled)
    : Boolean(
        cron &&
          legacyRunType !== 'pause' &&
          legacyRunType !== 'paused',
      );
  const retryOnFailure = schedule.retryOnFailure !== undefined
    ? Boolean(schedule.retryOnFailure)
    : Boolean(schedule.autoRetry);

  return {
    cron,
    enabled,
    retryOnFailure,
  };
};

const serializeSchedule = (
  schedule?: SyncSchedule,
): SyncSchedule => ({
  cron: String(schedule?.cron || '').trim(),
  enabled: Boolean(schedule?.enabled),
  retryOnFailure: Boolean(schedule?.retryOnFailure),
});

const normalizeNotificationIds = (value: unknown): number[] => {
  if (!Array.isArray(value)) return [];
  return Array.from(
    new Set(
      value
        .map((item) => Number(item))
        .filter((id) => Number.isSafeInteger(id) && id > 0),
    ),
  );
};

const normalizeNotification = (raw: any): SyncNotification => {
  const notification =
    raw?.notification && typeof raw.notification === 'object'
      ? raw.notification
      : {};
  const recipientType = String(
    notification.recipientType || 'PROJECT_OWNER',
  ).toUpperCase();

  return {
    enabled:
      notification.enabled === undefined
        ? DEFAULT_NOTIFICATION_CONFIG.enabled
        : Boolean(notification.enabled),
    triggers: ['FINAL_FAILURE'],
    recipientType:
      recipientType === 'EXPLICIT_USERS'
        ? 'EXPLICIT_USERS'
        : 'PROJECT_OWNER',
    recipientUserIds: normalizeNotificationIds(
      notification.recipientUserIds,
    ),
    inAppEnabled:
      notification.inAppEnabled === undefined
        ? DEFAULT_NOTIFICATION_CONFIG.inAppEnabled
        : Boolean(notification.inAppEnabled),
    alertEnabled:
      notification.alertEnabled === undefined
        ? DEFAULT_NOTIFICATION_CONFIG.alertEnabled
        : Boolean(notification.alertEnabled),
    alertChannelIds: normalizeNotificationIds(
      notification.alertChannelIds,
    ),
  };
};

const serializeNotification = (
  notification?: SyncNotification,
): SyncNotification => {
  const recipientType =
    notification?.recipientType === 'EXPLICIT_USERS'
      ? 'EXPLICIT_USERS'
      : 'PROJECT_OWNER';
  const alertEnabled = notification?.alertEnabled === true;

  return {
    enabled: notification?.enabled !== false,
    triggers: ['FINAL_FAILURE'],
    recipientType,
    recipientUserIds:
      recipientType === 'EXPLICIT_USERS'
        ? normalizeNotificationIds(
            notification?.recipientUserIds,
          )
        : [],
    inAppEnabled: notification?.inAppEnabled !== false,
    alertEnabled,
    alertChannelIds: alertEnabled
      ? normalizeNotificationIds(notification?.alertChannelIds)
      : [],
  };
};

const endpointFromType = (
  endpoint?: Partial<CreateSyncEndpoint> | null,
): SyncEndpoint => {
  const dbType = String(endpoint?.dbType || '');
  const connectorId =
    String(endpoint?.connectorId || '') || connectorIdForDataSourceType(dbType);

  return {
    connectorId,
    pluginName: String(endpoint?.pluginName || dbType),
    dbType,
    dataSourceId: '',
    config: {},
  };
};

const serializeChannel = (channel: SyncChannel) => ({
  parallelism: Math.max(1, Number(channel.parallelism || 1)),
  speedLimitEnabled: Boolean(channel.speedLimitEnabled),
  recordsPerSecond: Math.max(1, Number(channel.recordsPerSecond || 10000)),
  dirtyDataPolicy: channel.dirtyDataPolicy === 'skip' ? 'SKIP' : 'STOP',
  dirtyDataLimit: Math.max(0, Number(channel.dirtyDataLimit || 0)),
});

const multiDraftEndpoint = (
  endpoint: SyncEndpoint,
  kind: EndpointKind,
) =>
  kind === 'source'
    ? {
        connectorId: endpoint.connectorId,
        dbType: endpoint.dbType,
        dataSourceId: '',
        database: '',
        tables: [] as string[],
        tablePattern: '',
        options: {},
      }
    : {
        connectorId: endpoint.connectorId,
        dbType: endpoint.dbType,
        dataSourceId: '',
        database: '',
        tableNamingRule: 'SAME_NAME',
        tablePrefix: '',
        tableSuffix: '',
        autoCreateTable: true,
        writeMode: 'APPEND',
        options: {},
      };

export const buildCreatePayload = (
  taskId: string,
  values: CreateSyncTaskValues,
  source: CreateSyncEndpoint,
  sink: CreateSyncEndpoint,
) => {
  const sourceEndpoint = endpointFromType(source);
  const sinkEndpoint = endpointFromType(sink);
  const basic = {
    jobName: values.jobName.trim(),
    jobDesc: values.jobDesc?.trim() || '',
    mode: values.mode,
  };

  if (values.mode === 'GUIDE_MULTI') {
    return {
      id: taskId,
      basic,
      source: multiDraftEndpoint(sourceEndpoint, 'source'),
      sink: multiDraftEndpoint(sinkEndpoint, 'sink'),
      channel: serializeChannel(DEFAULT_CHANNEL_CONFIG),
      mapping: { ...DEFAULT_MAPPING_CONFIG },
      incremental: { ...DEFAULT_INCREMENTAL_CONFIG },
      schedule: { ...DEFAULT_SCHEDULE_CONFIG },
      notification: { ...DEFAULT_NOTIFICATION_CONFIG },
    };
  }

  return {
    id: taskId,
    basic,
    source: sourceEndpoint,
    sink: sinkEndpoint,
    channel: DEFAULT_CHANNEL_CONFIG,
    mapping: { ...DEFAULT_MAPPING_CONFIG },
    incremental: { ...DEFAULT_INCREMENTAL_CONFIG },
    schedule: { ...DEFAULT_SCHEDULE_CONFIG },
    notification: { ...DEFAULT_NOTIFICATION_CONFIG },
  };
};

const legacyEndpoint = (
  raw: any,
  kind: EndpointKind,
): Partial<SyncEndpoint> | null => {
  const workflow = raw?.workflow || {};
  const node = Array.isArray(workflow?.nodes)
    ? workflow.nodes.find(
        (item: any) =>
          item?.data?.nodeType === kind || item?.type === kind,
      )
    : undefined;

  if (!node) return null;

  const data = node?.data || {};
  const config = data?.config || {};
  const workflowType = kind === 'source'
    ? workflow?.sourceType
    : workflow?.targetType;

  return {
    connectorId:
      config?.connectorId ||
      data?.connectorId ||
      workflowType?.connectorId ||
      config?.connectorType ||
      data?.connectorType ||
      workflowType?.connectorType,
    pluginName:
      config?.pluginName ||
      data?.pluginName ||
      workflowType?.pluginName,
    dbType:
      config?.dbType ||
      data?.dbType ||
      workflowType?.dbType,
    dataSourceId: String(
      config?.dataSourceId ||
        (kind === 'source'
          ? workflow?.sourceDataSourceId || raw?.basic?.sourceDataSourceId
          : workflow?.targetDataSourceId || raw?.basic?.targetDataSourceId) ||
        '',
    ),
    config,
  };
};

const directConfig = (
  value: Record<string, any>,
  kind: EndpointKind,
): Record<string, any> => {
  const config =
    value?.config && typeof value.config === 'object'
      ? { ...value.config }
      : {};
  const options =
    value?.options && typeof value.options === 'object'
      ? value.options
      : config.connectorOptions || {};

  // 字段映射是任务级配置。旧任务中的 source.config.mapping
  // 会由 normalizeMapping 迁移到顶层，端点配置不再保留该字段。
  delete config.mapping;

  const keys = kind === 'source'
    ? [
        'database',
        'readMode',
        'table',
        'tables',
        'tablePattern',
        'sql',
        'whereCondition',
        'fetchSize',
      ]
    : [
        'database',
        'targetMode',
        'table',
        'targetTableName',
        'tableNamingRule',
        'tablePrefix',
        'tableSuffix',
        'autoCreateTable',
        'writeMode',
        'primaryKey',
        'batchSize',
        'sql',
      ];

  for (const key of keys) {
    if (value?.[key] !== undefined) {
      config[key] = value[key];
    }
  }

  if (typeof config.tableNamingRule === 'string') {
    config.tableNamingRule = config.tableNamingRule.toLowerCase();
  }
  if (typeof config.writeMode === 'string') {
    config.writeMode = config.writeMode.toLowerCase();
  }

  if (config.tableNameAffix && !config.tablePrefix && !config.tableSuffix) {
    if (config.tableNamingRule === 'prefix') {
      config.tablePrefix = config.tableNameAffix;
    } else if (config.tableNamingRule === 'suffix') {
      config.tableSuffix = config.tableNameAffix;
    }
  }

  if (!config.fetchSize && options.fetch_size) {
    config.fetchSize = Number(options.fetch_size);
  }
  if (!config.batchSize && options.batch_size) {
    config.batchSize = Number(options.batch_size);
  }

  return {
    ...config,
    connectorOptions: options,
  };
};

const normalizeEndpoint = (
  raw: any,
  kind: EndpointKind,
): SyncEndpoint => {
  const direct = raw?.[kind];
  const legacy = legacyEndpoint(raw, kind);
  const value = direct && typeof direct === 'object' ? direct : legacy || {};
  const basicType = kind === 'source'
    ? raw?.basic?.sourceType
    : raw?.basic?.targetType;
  const dbType = String(value?.dbType || basicType || '');
  const connectorId = String(
    value?.connectorId ||
      value?.connectorType ||
      connectorIdForDataSourceType(dbType),
  );

  return {
    connectorId,
    pluginName: String(value?.pluginName || dbType),
    dbType,
    dataSourceId: String(value?.dataSourceId || ''),
    config: directConfig(value as Record<string, any>, kind),
  };
};

export const normalizeEditDetail = (
  raw: any,
  fallbackId: string,
): SyncEditorState => {
  const id = String(raw?.id || fallbackId);
  const basicRaw = raw?.basic || {};
  const mode = (
    basicRaw?.mode || raw?.mode || 'GUIDE_SINGLE'
  ) as SyncMode;
  const legacyChannel = raw?.workflow?.channelConfig || {};
  const channelRaw = raw?.channel || {};
  const dirtyDataPolicy = String(
    channelRaw?.dirtyDataPolicy || legacyChannel?.dirtyDataPolicy || 'STOP',
  ).toUpperCase();

  return {
    id,
    mode,
    basic: {
      jobName: basicRaw?.jobName || raw?.jobName || '',
      jobDesc: basicRaw?.jobDesc || raw?.jobDesc || '',
      mode,
    },
    source: normalizeEndpoint(raw, 'source'),
    sink: normalizeEndpoint(raw, 'sink'),
    channel: {
      ...DEFAULT_CHANNEL_CONFIG,
      ...legacyChannel,
      ...channelRaw,
      parallelism: Number(
        channelRaw?.parallelism || raw?.env?.parallelism || 1,
      ),
      dirtyDataPolicy: dirtyDataPolicy === 'SKIP' ? 'skip' : 'stop',
    },
    mapping: normalizeMapping(raw),
    incremental: {
      ...DEFAULT_INCREMENTAL_CONFIG,
      ...(raw?.incremental || {}),
      enabled: Boolean(raw?.incremental?.enabled),
      column: String(raw?.incremental?.column || ''),
    },
    schedule: normalizeSchedule(raw),
    notification: normalizeNotification(raw),
    state: raw?.state,
  };
};

export const endpoint = (
  editor: SyncEditorState,
  kind: EndpointKind,
): SyncEndpoint => editor[kind];

const resetEndpointConfig = (
  kind: EndpointKind,
  config: Record<string, any>,
): Record<string, any> =>
  kind === 'source'
    ? {
        ...config,
        database: '',
        table: '',
        tables: [],
        tablePattern: '',
        sql: '',
      }
    : {
        ...config,
        database: '',
        table: '',
        targetTableName: '',
        sql: '',
        primaryKey: '',
      };

const connectorMeta = (record: DataSourceRecord) => {
  const dbType = String(record.dbType || '');

  return {
    dbType,
    connectorId: connectorIdForDataSourceType(dbType),
    pluginName:
      String((record as any).pluginName || '') ||
      (dbType ? `JDBC-${dbType}` : String(record.name || '')),
  };
};

export const applyEndpointSelection = (
  editor: SyncEditorState,
  kind: EndpointKind,
  record: DataSourceRecord,
): SyncEditorState => {
  const current = editor[kind];
  const recordId = String(record.id || '');
  const dataSourceChanged = current.dataSourceId !== recordId;
  const meta = connectorMeta(record);

  return {
    ...editor,
    [kind]: {
      ...current,
      ...meta,
      dataSourceId: recordId,
      config: dataSourceChanged
        ? resetEndpointConfig(kind, current.config)
        : current.config,
    },
    mapping: dataSourceChanged
      ? { ...DEFAULT_MAPPING_CONFIG }
      : editor.mapping,
    incremental:
      dataSourceChanged && kind === 'source'
        ? { ...DEFAULT_INCREMENTAL_CONFIG }
        : editor.incremental,
  };
};

export const updateEndpointConfig = (
  editor: SyncEditorState,
  kind: EndpointKind,
  patch: Record<string, any>,
): SyncEditorState => ({
  ...editor,
  [kind]: {
    ...editor[kind],
    config: {
      ...editor[kind].config,
      ...patch,
    },
  },
});

const stripDatabase = (table: unknown, database: string): string => {
  const value = String(table || '').trim();
  const prefix = database ? `${database}.` : '';
  return prefix && value.startsWith(prefix)
    ? value.slice(prefix.length)
    : value;
};

const multiSourcePayload = (source: SyncEndpoint) => {
  const config = source.config || {};
  const database = String(config.database || '').trim();
  const tables = Array.isArray(config.tables)
    ? config.tables
        .map((table: unknown) => stripDatabase(table, database))
        .filter(Boolean)
    : [];
  const options = {
    ...(config.connectorOptions || {}),
  };

  if (config.fetchSize) {
    options.fetch_size = Number(config.fetchSize);
  }

  return {
    connectorId: source.connectorId,
    dbType: source.dbType,
    dataSourceId: source.dataSourceId,
    database,
    tables,
    tablePattern: String(config.tablePattern || '').trim(),
    options,
  };
};

const multiSinkPayload = (sink: SyncEndpoint) => {
  const config = sink.config || {};
  const tableNamingRule = String(
    config.tableNamingRule || 'same_name',
  ).toUpperCase();
  const writeMode = String(config.writeMode || 'append').toUpperCase();
  const options = {
    ...(config.connectorOptions || {}),
  };

  if (config.batchSize) {
    options.batch_size = Number(config.batchSize);
  }

  return {
    connectorId: sink.connectorId,
    dbType: sink.dbType,
    dataSourceId: sink.dataSourceId,
    database: String(config.database || '').trim(),
    tableNamingRule,
    tablePrefix: String(config.tablePrefix || ''),
    tableSuffix: String(config.tableSuffix || ''),
    autoCreateTable: Boolean(config.autoCreateTable),
    writeMode,
    ...(writeMode === 'UPSERT'
      ? { primaryKey: String(config.primaryKey || '').trim() }
      : {}),
    options,
  };
};

const endpointSavePayload = (endpointValue: SyncEndpoint): SyncEndpoint => {
  const config = { ...(endpointValue.config || {}) };
  delete config.mapping;

  return {
    ...endpointValue,
    config,
  };
};

export const buildSavePayload = (
  editor: SyncEditorState,
) => {
  const basic = {
    jobName: editor.basic.jobName.trim(),
    jobDesc: editor.basic.jobDesc.trim(),
    mode: editor.mode,
  };
  const mapping = editor.mode === 'GUIDE_SINGLE'
    ? serializeMapping(editor.mapping)
    : { ...DEFAULT_MAPPING_CONFIG };
  const schedule = serializeSchedule(editor.schedule);
  const notification = serializeNotification(editor.notification);

  if (editor.mode === 'GUIDE_MULTI') {
    return {
      id: editor.id,
      basic,
      source: multiSourcePayload(editor.source),
      sink: multiSinkPayload(editor.sink),
      channel: serializeChannel(editor.channel),
      mapping,
      incremental: { ...DEFAULT_INCREMENTAL_CONFIG },
      schedule,
      notification,
    };
  }

  return {
    id: editor.id,
    basic,
    source: endpointSavePayload(editor.source),
    sink: endpointSavePayload(editor.sink),
    channel: editor.channel,
    mapping,
    incremental: {
      ...DEFAULT_INCREMENTAL_CONFIG,
      ...(editor.incremental || {}),
      column: String(editor.incremental?.column || '').trim(),
    },
    schedule,
    notification,
  };
};
