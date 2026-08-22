import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  DownOutlined,
  KeyOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
  SyncOutlined,
  TableOutlined,
  UpOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  Button,
  Checkbox,
  ConfigProvider,
  Empty,
  Input,
  InputNumber,
  message,
  Select,
  Spin,
  Tag,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BRAND_THEME } from '@/styles/brand';
import { realtimeApi } from './api';
import type {
  CdcPipelineSpec,
  DataSourceCatalogTable,
  DataSourceOption,
  RealtimeJob,
  TableRoute,
} from './types';

type PrimaryKeyStatus = 'loading' | 'ready' | 'missing' | 'error';
type StartupMode = CdcPipelineSpec['startupMode'];

interface SelectedTableState {
  table: DataSourceCatalogTable;
  route: TableRoute;
  status: PrimaryKeyStatus;
  error?: string;
}

interface AdvancedSettings {
  schemaEvolution: CdcPipelineSpec['schemaEvolution'];
  parallelism: number;
  maxRetries: number;
  batchSize: number;
  flushIntervalMs: number;
  maxBatchBytes: number;
  statementCacheSize: number;
}

const DEFAULT_SPEC: CdcPipelineSpec = {
  sourceDataSourceRef: 0,
  sinkDataSourceRef: 0,
  tables: [],
  startupMode: 'initial',
  schemaEvolution: 'EVOLVE',
  parallelism: 1,
  checkpointIntervalMs: 60_000,
  restart: { strategy: 'fixed-delay', attempts: 3, delayMs: 10_000 },
  sink: {
    maxRetries: 3,
    batchSize: 1_000,
    flushIntervalMs: 2_000,
    maxBatchBytes: 16_777_216,
    statementCacheSize: 128,
    strictReplaySafety: true,
  },
};

const SYNC_MODES: Array<{
  value: StartupMode;
  title: string;
  description: string;
  badge?: string;
}> = [
  {
    value: 'initial',
    title: '初始化并持续同步',
    description: '先同步当前历史数据，完成后持续读取 Binlog 新增和变更。',
    badge: '推荐',
  },
  {
    value: 'latest-offset',
    title: '仅同步新变更',
    description: '不处理历史数据，从任务启动时的最新 Binlog 位点开始同步。',
  },
];

const toOption = (item: DataSourceOption) => ({
  label: `${item.label} (${item.dbType})`,
  value: Number(item.value),
});

const initialSelectedTables = (job: RealtimeJob): SelectedTableState[] =>
  (job.spec?.tables || [])
    .filter((route) => route.matchMode === 'EXACT')
    .map((route) => ({
      table: { name: route.sourceTable, type: 'TABLE' },
      route: { ...route, keyColumns: [...route.keyColumns] },
      status: route.keyColumns.length > 0 ? 'ready' : 'missing',
    }));

const initialAdvancedSettings = (job: RealtimeJob): AdvancedSettings => {
  const spec = job.spec || DEFAULT_SPEC;
  return {
    schemaEvolution: spec.schemaEvolution,
    parallelism: spec.parallelism,
    maxRetries: spec.sink.maxRetries,
    batchSize: spec.sink.batchSize,
    flushIntervalMs: spec.sink.flushIntervalMs,
    maxBatchBytes: spec.sink.maxBatchBytes,
    statementCacheSize: spec.sink.statementCacheSize,
  };
};

const isPhysicalTable = (table: DataSourceCatalogTable) =>
  !String(table.type || '')
    .toUpperCase()
    .includes('VIEW');

export default function WizardJobEditor({
  job,
  dataSources,
  onClose,
  onSaved,
}: {
  job: RealtimeJob;
  dataSources: DataSourceOption[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [sourceId, setSourceId] = useState<number | undefined>(job.spec?.sourceDataSourceRef);
  const [sinkId, setSinkId] = useState<number | undefined>(job.spec?.sinkDataSourceRef);
  const [tables, setTables] = useState<DataSourceCatalogTable[]>([]);
  const [selectedTables, setSelectedTables] = useState<SelectedTableState[]>(() =>
    initialSelectedTables(job),
  );
  const [startupMode, setStartupMode] = useState<StartupMode>(job.spec?.startupMode || 'initial');
  const [advanced, setAdvanced] = useState<AdvancedSettings>(() => initialAdvancedSettings(job));
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [tableLoading, setTableLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const sourceIdRef = useRef<number | undefined>(sourceId);
  const catalogRequestRef = useRef(0);

  const sourceOptions = useMemo(
    () => dataSources.filter((item) => item.dbType === 'MYSQL').map(toOption),
    [dataSources],
  );
  const sinkOptions = useMemo(
    () =>
      dataSources
        .filter((item) => ['MYSQL', 'POSTGRE_SQL', 'POSTGRESQL'].includes(item.dbType))
        .map((item) => ({
          ...toOption(item),
          disabled: Number(item.value) === sourceId,
        })),
    [dataSources, sourceId],
  );

  const selectedNameSet = useMemo(
    () => new Set(selectedTables.map((item) => item.table.name)),
    [selectedTables],
  );
  const filteredTables = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return tables;
    return tables.filter(
      (table) =>
        table.name.toLowerCase().includes(keyword) ||
        String(table.remarks || '')
          .toLowerCase()
          .includes(keyword),
    );
  }, [search, tables]);

  const hasUnsupportedRoutes = Boolean(job.spec?.tables.some((route) => route.matchMode !== 'EXACT'));
  const primaryKeyPending = selectedTables.some((item) => item.status === 'loading');
  const primaryKeyInvalid = selectedTables.some((item) => item.status !== 'ready');
  const sinkMappingInvalid = selectedTables.some(
    (item) => !item.route.sinkTable.trim() || /[\r\n]/.test(item.route.sinkTable),
  );

  const loadTables = useCallback(async (dataSourceId: number) => {
    const requestId = ++catalogRequestRef.current;
    setTableLoading(true);
    try {
      const response = await realtimeApi.catalogTables(dataSourceId);
      if (requestId !== catalogRequestRef.current || sourceIdRef.current !== dataSourceId) return;

      const uniqueTables = Array.from(
        new Map(
          (response.data || [])
            .filter(isPhysicalTable)
            .map((table) => [table.name, table] as const),
        ).values(),
      ).sort((left, right) => left.name.localeCompare(right.name));

      setTables(uniqueTables);
      setSelectedTables((previous) =>
        previous.map((selected) => {
          const catalogTable = uniqueTables.find((table) => table.name === selected.table.name);
          return catalogTable ? { ...selected, table: catalogTable } : selected;
        }),
      );
    } catch (error: any) {
      if (requestId === catalogRequestRef.current && sourceIdRef.current === dataSourceId) {
        setTables([]);
        message.error(error?.message || 'Source 表列表加载失败');
      }
    } finally {
      if (requestId === catalogRequestRef.current) setTableLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!sourceId) return;
    sourceIdRef.current = sourceId;
    void loadTables(sourceId);
  }, [loadTables, sourceId]);

  const resolvePrimaryKey = async (table: DataSourceCatalogTable, dataSourceId: number) => {
    setSelectedTables((previous) =>
      previous.map((selected) =>
        selected.table.name === table.name
          ? { ...selected, table, status: 'loading', error: undefined }
          : selected,
      ),
    );

    try {
      const response = await realtimeApi.catalogColumns(dataSourceId, table);
      if (sourceIdRef.current !== dataSourceId) return;
      const keyColumns = (response.data || [])
        .filter((column) => column.primaryKey)
        .sort((left, right) => (left.ordinalPosition || 0) - (right.ordinalPosition || 0))
        .map((column) => column.name);

      setSelectedTables((previous) =>
        previous.map((selected) => {
          if (selected.table.name !== table.name) return selected;
          return {
            ...selected,
            table,
            route: {
              sourceTable: table.name,
              sinkTable: selected.route.sinkTable || table.name,
              matchMode: 'EXACT',
              keyColumns,
            },
            status: keyColumns.length > 0 ? 'ready' : 'missing',
            error: undefined,
          };
        }),
      );
    } catch (error: any) {
      if (sourceIdRef.current !== dataSourceId) return;
      setSelectedTables((previous) =>
        previous.map((selected) =>
          selected.table.name === table.name
            ? {
                ...selected,
                status: 'error',
                error: error?.message || '主键元数据读取失败',
              }
            : selected,
        ),
      );
    }
  };

  const handleSourceChange = (nextSourceId: number) => {
    sourceIdRef.current = nextSourceId;
    catalogRequestRef.current += 1;
    setSourceId(nextSourceId);
    setTables([]);
    setSelectedTables([]);
    setSearch('');
    if (sinkId === nextSourceId) setSinkId(undefined);
  };

  const handleTableToggle = (table: DataSourceCatalogTable, checked: boolean) => {
    if (!sourceId) return;
    if (!checked) {
      setSelectedTables((previous) =>
        previous.filter((selected) => selected.table.name !== table.name),
      );
      return;
    }
    if (selectedNameSet.has(table.name)) return;

    setSelectedTables((previous) => [
      ...previous,
      {
        table,
        route: {
          sourceTable: table.name,
          sinkTable: table.name,
          matchMode: 'EXACT',
          keyColumns: [],
        },
        status: 'loading',
      },
    ]);
    void resolvePrimaryKey(table, sourceId);
  };

  const updateSinkTable = (sourceTable: string, sinkTable: string) => {
    setSelectedTables((previous) =>
      previous.map((selected) =>
        selected.table.name === sourceTable
          ? { ...selected, route: { ...selected.route, sinkTable } }
          : selected,
      ),
    );
  };

  const updateAdvanced = <K extends keyof AdvancedSettings>(
    key: K,
    value: AdvancedSettings[K],
  ) => {
    setAdvanced((previous) => ({ ...previous, [key]: value }));
  };

  const save = async () => {
    if (!sourceId) {
      message.warning('请选择 Source 数据源');
      return;
    }
    if (!sinkId) {
      message.warning('请选择 Sink 数据源');
      return;
    }
    if (sourceId === sinkId) {
      message.warning('Source 与 Sink 不能使用同一个数据源');
      return;
    }
    if (selectedTables.length === 0) {
      message.warning('请至少选择一张同步表');
      return;
    }
    if (hasUnsupportedRoutes) {
      message.warning('当前任务包含向导暂不支持的正则表规则，请使用原编辑器处理');
      return;
    }
    if (primaryKeyPending) {
      message.warning('主键仍在识别中，请稍后再保存');
      return;
    }
    const invalidTables = selectedTables.filter((item) => item.status !== 'ready');
    if (invalidTables.length > 0) {
      message.warning(`以下表暂不能保存：${invalidTables.map((item) => item.table.name).join('、')}`);
      return;
    }
    if (sinkMappingInvalid) {
      message.warning('目标表名不能为空，也不能包含换行');
      return;
    }

    const baseSpec = job.spec || DEFAULT_SPEC;
    const spec: CdcPipelineSpec = {
      ...baseSpec,
      sourceDataSourceRef: sourceId,
      sinkDataSourceRef: sinkId,
      tables: selectedTables.map((item) => ({
        ...item.route,
        sinkTable: item.route.sinkTable.trim(),
        keyColumns: [...item.route.keyColumns],
      })),
      startupMode,
      schemaEvolution: advanced.schemaEvolution,
      parallelism: advanced.parallelism,
      checkpointIntervalMs: baseSpec.checkpointIntervalMs || DEFAULT_SPEC.checkpointIntervalMs,
      restart: { ...baseSpec.restart },
      sink: {
        ...baseSpec.sink,
        maxRetries: advanced.maxRetries,
        batchSize: advanced.batchSize,
        flushIntervalMs: advanced.flushIntervalMs,
        maxBatchBytes: advanced.maxBatchBytes,
        statementCacheSize: advanced.statementCacheSize,
        strictReplaySafety: true,
      },
    };

    setSaving(true);
    try {
      await realtimeApi.update(job.id, {
        name: job.name,
        description: job.description,
        runtimeEnvironmentId: job.runtimeEnvironmentId,
        spec,
      });
      message.success('向导配置已保存');
      onSaved();
    } catch (error: any) {
      message.error(error?.message || '保存向导配置失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ConfigProvider theme={BRAND_THEME} variant="filled">
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f8fa] text-[#161823]">
        <div className="mx-auto w-full max-w-[1180px] px-6 pb-28 pt-6">
          <header className="mb-5 flex items-start justify-between gap-4 rounded-xl bg-white px-7 py-6">
            <div>
              <div className="mb-1 text-[12px] font-medium text-[var(--yak-brand-color)]">向导模式 · 流程 3</div>
              <h1 className="m-0 text-[20px] font-semibold text-[#101828]">配置实时同步任务</h1>
              <div className="mt-2 text-[13px] text-[#667085]">
                {job.name} · 运行环境 #{job.runtimeEnvironmentId}
              </div>
            </div>
            <div className="rounded-lg bg-[#f9fafb] px-4 py-2 text-[12px] text-[#667085]">
              选数据源、选表、确认映射和同步方式即可完成配置
            </div>
          </header>

          {hasUnsupportedRoutes && (
            <div className="mb-5 rounded-xl border border-[#fedf89] bg-[#fffaeb] px-5 py-4 text-[13px] text-[#93370d]">
              当前任务包含正则表规则，向导模式暂不能安全表达这些配置。本页可查看，但不会允许覆盖保存。
            </div>
          )}

          <section className="mb-5 rounded-xl bg-white px-7 py-6">
            <div className="mb-5 flex items-center gap-2">
              <DatabaseOutlined className="text-[var(--yak-brand-color)]" />
              <h2 className="m-0 text-[16px] font-semibold text-[#101828]">Source / Sink 数据源</h2>
            </div>
            <div className="grid grid-cols-[minmax(0,1fr)_42px_minmax(0,1fr)] items-end gap-4 max-md:grid-cols-1">
              <div>
                <div className="mb-2 text-[13px] font-medium text-[#344054]">Source 来源数据源</div>
                <Select
                  showSearch
                  optionFilterProp="label"
                  value={sourceId}
                  className="w-full"
                  placeholder="请选择 MySQL Source"
                  options={sourceOptions}
                  onChange={handleSourceChange}
                />
                <div className="mt-2 text-[12px] text-[#98a2b3]">一期 Source 仅支持 MySQL CDC。</div>
              </div>
              <div className="flex h-8 items-center justify-center text-[#98a2b3] max-md:hidden">
                <ArrowRightOutlined />
              </div>
              <div>
                <div className="mb-2 text-[13px] font-medium text-[#344054]">Sink 目标数据源</div>
                <Select
                  showSearch
                  optionFilterProp="label"
                  value={sinkId}
                  className="w-full"
                  placeholder="请选择 MySQL / PostgreSQL Sink"
                  options={sinkOptions}
                  onChange={setSinkId}
                />
                <div className="mt-2 text-[12px] text-[#98a2b3]">Source 与 Sink 不能引用同一个数据源。</div>
              </div>
            </div>
          </section>

          <section className="mb-5 rounded-xl bg-white px-7 py-6">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <TableOutlined className="text-[var(--yak-brand-color)]" />
                  <h2 className="m-0 text-[16px] font-semibold text-[#101828]">同步表与目标映射</h2>
                </div>
                <div className="mt-1 text-[12px] text-[#98a2b3]">
                  选择 1 张就是单表同步，选择多张就是多表同步；目标表默认同名，可按需修改。
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Tag>{selectedTables.length} 张已选</Tag>
                <Button
                  size="small"
                  icon={<ReloadOutlined spin={tableLoading} />}
                  disabled={!sourceId || tableLoading}
                  onClick={() => sourceId && void loadTables(sourceId)}
                >
                  刷新表
                </Button>
              </div>
            </div>

            {!sourceId ? (
              <div className="rounded-xl border border-dashed border-[#d0d5dd] py-14">
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请先选择 Source 数据源" />
              </div>
            ) : (
              <div className="grid grid-cols-[minmax(0,1fr)_minmax(420px,1fr)] gap-5 max-lg:grid-cols-1">
                <div className="overflow-hidden rounded-xl border border-[#e4e7ec]">
                  <div className="border-b border-[#eaecf0] bg-[#fcfcfd] p-4">
                    <Input
                      allowClear
                      value={search}
                      prefix={<SearchOutlined className="text-[#98a2b3]" />}
                      placeholder="搜索表名或表备注"
                      onChange={(event) => setSearch(event.target.value)}
                    />
                  </div>
                  <div className="max-h-[480px] overflow-y-auto">
                    {tableLoading ? (
                      <div className="flex min-h-[220px] items-center justify-center">
                        <Spin tip="正在读取 Source Catalog..." />
                      </div>
                    ) : filteredTables.length === 0 ? (
                      <div className="py-12">
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有找到可同步的数据表" />
                      </div>
                    ) : (
                      filteredTables.map((table) => (
                        <label
                          key={table.name}
                          className="flex cursor-pointer items-start gap-3 border-b border-[#f2f4f7] px-4 py-3 last:border-b-0 hover:bg-[#fafbfc]"
                        >
                          <Checkbox
                            checked={selectedNameSet.has(table.name)}
                            onChange={(event) => handleTableToggle(table, event.target.checked)}
                          />
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-[13px] font-medium text-[#344054]">{table.name}</div>
                            <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">
                              {table.remarks || table.type || 'TABLE'}
                            </div>
                          </div>
                        </label>
                      ))
                    )}
                  </div>
                </div>

                <div className="overflow-hidden rounded-xl border border-[#e4e7ec]">
                  <div className="border-b border-[#eaecf0] bg-[#fcfcfd] px-4 py-3">
                    <div className="text-[13px] font-medium text-[#344054]">表映射与主键</div>
                    <div className="mt-0.5 text-[11px] text-[#98a2b3]">主键自动识别；目标表名可修改。</div>
                  </div>
                  <div className="max-h-[480px] overflow-y-auto">
                    {selectedTables.length === 0 ? (
                      <div className="py-12">
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有选择数据表" />
                      </div>
                    ) : (
                      selectedTables.map((selected) => (
                        <div key={selected.table.name} className="border-b border-[#f2f4f7] px-4 py-4 last:border-b-0">
                          <div className="mb-2 flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="truncate text-[13px] font-medium text-[#344054]">
                                {selected.table.name}
                              </div>
                              <div className="mt-1 flex flex-wrap items-center gap-1.5">
                                {selected.status === 'loading' && (
                                  <span className="inline-flex items-center gap-1 text-[11px] text-[#667085]">
                                    <Spin size="small" /> 正在识别主键
                                  </span>
                                )}
                                {selected.status === 'ready' && (
                                  <>
                                    <CheckCircleOutlined className="text-[#12b76a]" />
                                    {selected.route.keyColumns.map((column) => (
                                      <Tag key={column} icon={<KeyOutlined />}>
                                        {column}
                                      </Tag>
                                    ))}
                                  </>
                                )}
                                {selected.status === 'missing' && (
                                  <span className="inline-flex items-center gap-1 text-[11px] text-[#b54708]">
                                    <WarningOutlined /> 未检测到主键，一期暂不支持保存
                                  </span>
                                )}
                                {selected.status === 'error' && (
                                  <span className="text-[11px] text-[#b42318]">
                                    {selected.error || '主键识别失败'}
                                  </span>
                                )}
                              </div>
                            </div>
                            {selected.status === 'error' && sourceId && (
                              <Button
                                size="small"
                                type="link"
                                onClick={() => void resolvePrimaryKey(selected.table, sourceId)}
                              >
                                重试
                              </Button>
                            )}
                          </div>
                          <div className="grid grid-cols-[minmax(0,1fr)_24px_minmax(0,1fr)] items-center gap-2">
                            <Input value={selected.table.name} disabled />
                            <ArrowRightOutlined className="text-[#98a2b3]" />
                            <Input
                              value={selected.route.sinkTable}
                              status={!selected.route.sinkTable.trim() ? 'error' : undefined}
                              placeholder="目标表名，例如 public.orders"
                              onChange={(event) =>
                                updateSinkTable(selected.table.name, event.target.value)
                              }
                            />
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            )}
          </section>

          <section className="mb-5 rounded-xl bg-white px-7 py-6">
            <div className="mb-5 flex items-center gap-2">
              <SyncOutlined className="text-[var(--yak-brand-color)]" />
              <div>
                <h2 className="m-0 text-[16px] font-semibold text-[#101828]">同步方式</h2>
                <div className="mt-1 text-[12px] text-[#98a2b3]">选择任务第一次启动时如何读取 Source。</div>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3 max-lg:grid-cols-1">
              {SYNC_MODES.map((mode) => {
                const selected = startupMode === mode.value;
                return (
                  <button
                    key={mode.value}
                    type="button"
                    className={[
                      'relative min-h-[120px] rounded-xl border p-4 text-left transition-all',
                      selected
                        ? 'border-[var(--yak-brand-color)] bg-[rgba(254,44,85,0.04)] shadow-[0_0_0_2px_rgba(254,44,85,0.06)]'
                        : 'border-[#e4e7ec] bg-white hover:border-[#fda29b]',
                    ].join(' ')}
                    onClick={() => setStartupMode(mode.value)}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-[14px] font-semibold text-[#101828]">{mode.title}</div>
                      {mode.badge && (
                        <span className="rounded-full bg-[#fff1f0] px-2 py-0.5 text-[10px] font-medium text-[var(--yak-brand-color)]">
                          {mode.badge}
                        </span>
                      )}
                    </div>
                    <div className="mt-2 text-[12px] leading-5 text-[#667085]">{mode.description}</div>
                  </button>
                );
              })}
              <div className="relative min-h-[120px] rounded-xl border border-dashed border-[#d0d5dd] bg-[#f9fafb] p-4 opacity-75">
                <div className="flex items-center justify-between gap-2">
                  <div className="text-[14px] font-semibold text-[#667085]">仅初始化数据</div>
                  <Tag>后续</Tag>
                </div>
                <div className="mt-2 text-[12px] leading-5 text-[#98a2b3]">
                  Flink CDC 支持 snapshot，但 Yak 当前需先补齐 FINISHED 正常完成态语义，本流程暂不开放。
                </div>
              </div>
            </div>
          </section>

          <section className="rounded-xl bg-white px-7 py-6">
            <button
              type="button"
              className="flex w-full items-center justify-between border-0 bg-transparent p-0 text-left"
              onClick={() => setAdvancedOpen((value) => !value)}
            >
              <div className="flex items-center gap-2">
                <SettingOutlined className="text-[var(--yak-brand-color)]" />
                <div>
                  <h2 className="m-0 text-[16px] font-semibold text-[#101828]">高级配置</h2>
                  <div className="mt-1 text-[12px] text-[#98a2b3]">普通场景保持默认值即可。</div>
                </div>
              </div>
              {advancedOpen ? <UpOutlined className="text-[#98a2b3]" /> : <DownOutlined className="text-[#98a2b3]" />}
            </button>

            {advancedOpen && (
              <div className="mt-6 border-t border-[#f0f2f5] pt-6">
                <div className="grid grid-cols-3 gap-x-5 gap-y-4 max-lg:grid-cols-2 max-md:grid-cols-1">
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">Schema Evolution</div>
                    <Select
                      className="w-full"
                      value={advanced.schemaEvolution}
                      options={[
                        { value: 'EVOLVE', label: '自动同步结构变更（EVOLVE）' },
                        { value: 'IGNORE', label: '忽略结构变更（IGNORE）' },
                        { value: 'FAIL', label: '遇到结构变更失败（FAIL）' },
                      ]}
                      onChange={(value) => updateAdvanced('schemaEvolution', value)}
                    />
                  </div>
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">并行度</div>
                    <InputNumber
                      min={1}
                      max={256}
                      className="w-full"
                      value={advanced.parallelism}
                      onChange={(value) => updateAdvanced('parallelism', Number(value || 1))}
                    />
                  </div>
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">Sink Batch Size</div>
                    <InputNumber
                      min={1}
                      className="w-full"
                      value={advanced.batchSize}
                      onChange={(value) => updateAdvanced('batchSize', Number(value || 1))}
                    />
                  </div>
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">Flush 间隔（ms）</div>
                    <InputNumber
                      min={1}
                      className="w-full"
                      value={advanced.flushIntervalMs}
                      onChange={(value) => updateAdvanced('flushIntervalMs', Number(value || 1))}
                    />
                  </div>
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">Sink 重试次数</div>
                    <InputNumber
                      min={0}
                      className="w-full"
                      value={advanced.maxRetries}
                      onChange={(value) => updateAdvanced('maxRetries', Number(value || 0))}
                    />
                  </div>
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">最大批次字节</div>
                    <InputNumber
                      min={1}
                      className="w-full"
                      value={advanced.maxBatchBytes}
                      onChange={(value) => updateAdvanced('maxBatchBytes', Number(value || 1))}
                    />
                  </div>
                  <div>
                    <div className="mb-2 text-[12px] font-medium text-[#475467]">Statement Cache</div>
                    <InputNumber
                      min={1}
                      className="w-full"
                      value={advanced.statementCacheSize}
                      onChange={(value) => updateAdvanced('statementCacheSize', Number(value || 1))}
                    />
                  </div>
                </div>
                <div className="mt-5 rounded-lg bg-[#f9fafb] px-4 py-3 text-[11px] leading-5 text-[#667085]">
                  Checkpoint、重启策略继续使用一期稳定默认值；Strict Replay Safety 固定启用。已有任务进入向导时，会保留未在本页编辑的运行参数。
                </div>
              </div>
            )}
          </section>
        </div>

        <footer className="fixed bottom-0 left-0 right-0 z-30 border-t border-[#eaecf0] bg-white/95 px-6 py-4 shadow-[0_-8px_18px_rgba(16,24,40,0.06)] backdrop-blur">
          <div className="mx-auto flex w-full max-w-[1180px] items-center justify-between gap-4">
            <div className="text-[12px] text-[#667085]">
              {primaryKeyPending
                ? '正在读取主键元数据…'
                : primaryKeyInvalid && selectedTables.length > 0
                  ? '存在未识别主键的数据表，暂不能保存。'
                  : sinkMappingInvalid && selectedTables.length > 0
                    ? '请完善目标表映射。'
                    : selectedTables.length > 0
                      ? `已准备 ${selectedTables.length} 张表 · ${startupMode === 'initial' ? '初始化并持续同步' : '仅同步新变更'}。`
                      : '请选择 Source、Sink 和至少一张数据表。'}
            </div>
            <div className="flex items-center gap-2">
              <Button disabled={saving} onClick={onClose}>
                取消
              </Button>
              <Button
                type="primary"
                danger
                loading={saving}
                disabled={hasUnsupportedRoutes || primaryKeyPending}
                onClick={() => void save()}
              >
                保存向导配置
              </Button>
            </div>
          </div>
        </footer>
      </div>
    </ConfigProvider>
  );
}
