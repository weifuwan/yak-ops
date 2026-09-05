import { latestAnalysisQueryId } from '@/components/analysis/query-runtime';
import {
  fetchDashboard,
  fetchDashboardQueryPerformance,
  type DashboardQueryPerformance,
} from '@/services/dashboard';
import { useIntl } from '@umijs/max';
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Modal,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { Download, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { getDashboardPerformanceQuery } from './performance-runtime';

const formatTime = (value: string | undefined, locale: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString(locale, { hour12: false });
};

const escapeCsv = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`;

type DashboardPerformanceRow = DashboardQueryPerformance & {
  widgetId: string;
  widgetName: string;
};

export function DashboardPerformanceModal({
  open,
  dashboardId,
  dashboardName,
  onClose,
}: {
  open: boolean;
  dashboardId: string;
  dashboardName: string;
  onClose: () => void;
}) {
  const intl = useIntl();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [records, setRecords] = useState<DashboardPerformanceRow[]>([]);
  const [detail, setDetail] = useState<DashboardQueryPerformance>();
  const fallbackDashboardName = intl.formatMessage({ id: 'pages.dashboard.editor.performance.dashboardFallback' });
  const resolvedDashboardName = dashboardName || fallbackDashboardName;

  const load = useCallback(async () => {
    if (!/^\d+$/.test(dashboardId)) return;
    setLoading(true);
    setError(undefined);
    try {
      const dashboard = await fetchDashboard(dashboardId);
      const executions = dashboard.widgets.flatMap((widget) => {
        const binding = getDashboardPerformanceQuery(widget.id);
        if (!binding) return [];
        const queryId = latestAnalysisQueryId(binding.queryKey);
        if (!queryId) return [];
        return [{
          widgetId: widget.id,
          widgetName: binding.widgetName,
          queryId,
        }];
      });
      const queryIds = Array.from(new Set(executions.map((item) => item.queryId)));
      const traces = await fetchDashboardQueryPerformance(queryIds);
      const tracesById = new Map(traces.map((trace) => [trace.queryId, trace]));
      setRecords(executions.flatMap((execution) => {
        const trace = tracesById.get(execution.queryId);
        return trace ? [{ ...trace, ...execution }] : [];
      }));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : intl.formatMessage({ id: 'pages.dashboard.editor.performance.loadFailed' }),
      );
    } finally {
      setLoading(false);
    }
  }, [dashboardId, intl]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  const exportDetails = () => {
    const header = [
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.level1' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.level2' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.dataset' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.queryIdCompact' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.waitMs' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.prepareMs' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.executeMs' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.transferMs' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.totalMs' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.rows' }),
      intl.formatMessage({ id: 'pages.dashboard.editor.performance.startedAtCompact' }),
      'Data Source ID',
      'SQL',
    ];
    const rows = records.map((record) => [
      resolvedDashboardName,
      record.widgetName,
      record.datasetName || record.datasetId,
      record.queryId,
      record.waitMillis,
      record.prepareMillis,
      record.executeMillis,
      record.transferMillis,
      record.totalMillis,
      record.returnedRows,
      formatTime(record.startedAt, intl.locale),
      record.dataSourceId ?? '',
      record.sql,
    ]);
    const csv = `\uFEFF${[header, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\r\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${dashboardName || 'dashboard'}-performance.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const columns: TableColumnsType<DashboardPerformanceRow> = [
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.level1' }),
      width: 130,
      render: () => <span className="text-[#344054]">{resolvedDashboardName}</span>,
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.level2' }),
      dataIndex: 'widgetName',
      width: 180,
      render: (value: string) => (
        <Tooltip title={value}>
          <span className="block max-w-[160px] truncate">{value || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.dataset' }),
      width: 150,
      render: (_, record) => record.datasetName || record.datasetId,
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.queryIdCompact' }),
      dataIndex: 'queryId',
      width: 150,
      render: (value: string, record) => (
        <Button type="link" className="!h-auto !p-0 !text-[11px]" onClick={() => setDetail(record)}>
          {value.slice(0, 12)}…
        </Button>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.waitMs' }),
      dataIndex: 'waitMillis',
      width: 135,
      align: 'right',
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.prepareMs' }),
      dataIndex: 'prepareMillis',
      width: 135,
      align: 'right',
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.executeMs' }),
      dataIndex: 'executeMillis',
      width: 125,
      align: 'right',
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.transferMs' }),
      dataIndex: 'transferMillis',
      width: 130,
      align: 'right',
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.totalMs' }),
      dataIndex: 'totalMillis',
      width: 125,
      align: 'right',
      render: (value: number) => (
        <span className={value >= 1000 ? 'font-semibold text-[var(--yak-brand-color)]' : 'font-medium text-[#161823]'}>
          {value}
        </span>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.rows' }),
      dataIndex: 'returnedRows',
      width: 90,
      align: 'right',
    },
    {
      title: intl.formatMessage({ id: 'pages.dashboard.editor.performance.startedAtCompact' }),
      dataIndex: 'startedAt',
      width: 170,
      render: (value: string) => formatTime(value, intl.locale),
    },
  ];

  return (
    <>
      <Modal
        title={(
          <span className="text-[14px] font-semibold text-[#161823]">
            {intl.formatMessage(
              { id: 'pages.dashboard.editor.performance.title' },
              { name: resolvedDashboardName },
            )}
          </span>
        )}
        width="min(1240px, calc(100vw - 48px))"
        open={open}
        onCancel={onClose}
        footer={null}
        destroyOnClose={false}
        styles={{ body: { paddingTop: 8, height: 560, overflow: 'hidden' } }}
      >
        <div className="mb-3 flex items-center justify-between">
          <div>
            <div className="text-[12px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.performance.coreInfo' })}
            </div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.performance.hint' })}
            </div>
          </div>
          <div className="flex items-center gap-1">
            <Button type="text" size="small" icon={<RefreshCw size={12} />} loading={loading} onClick={() => void load()}>
              {intl.formatMessage({ id: 'pages.dashboard.editor.performance.refresh' })}
            </Button>
            <Button size="small" icon={<Download size={12} />} disabled={!records.length} onClick={exportDetails}>
              {intl.formatMessage({ id: 'pages.dashboard.editor.performance.export' })}
            </Button>
          </div>
        </div>

        {error ? <Alert className="mb-3" type="error" showIcon message={error} /> : null}

        <Table<DashboardPerformanceRow>
          rowKey={(record) => `${record.widgetId}-${record.queryId}`}
          size="small"
          loading={loading}
          columns={columns}
          dataSource={records}
          pagination={false}
          scroll={{ x: 1500, y: 420 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={intl.formatMessage({ id: 'pages.dashboard.editor.performance.empty' })}
              />
            ),
          }}
        />
      </Modal>

      <Modal
        title={intl.formatMessage({ id: 'pages.dashboard.editor.performance.detailTitle' })}
        width={820}
        open={Boolean(detail)}
        onCancel={() => setDetail(undefined)}
        footer={(
          <Button onClick={() => setDetail(undefined)}>
            {intl.formatMessage({ id: 'pages.dashboard.editor.performance.close' })}
          </Button>
        )}
      >
        {detail ? (
          <div>
            <Descriptions
              size="small"
              bordered
              column={3}
              items={[
                {
                  key: 'queryId',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.queryId' }),
                  children: detail.queryId,
                  span: 3,
                },
                {
                  key: 'dataset',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.dataset' }),
                  children: detail.datasetName,
                },
                {
                  key: 'version',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.version' }),
                  children: `V${detail.datasetVersionNo}`,
                },
                {
                  key: 'rows',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.rows' }),
                  children: detail.returnedRows,
                },
                {
                  key: 'wait',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.wait' }),
                  children: `${detail.waitMillis} ms`,
                },
                {
                  key: 'prepare',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.prepare' }),
                  children: `${detail.prepareMillis} ms`,
                },
                {
                  key: 'execute',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.execute' }),
                  children: `${detail.executeMillis} ms`,
                },
                {
                  key: 'transfer',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.transfer' }),
                  children: `${detail.transferMillis} ms`,
                },
                {
                  key: 'total',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.total' }),
                  children: `${detail.totalMillis} ms`,
                },
                {
                  key: 'start',
                  label: intl.formatMessage({ id: 'pages.dashboard.editor.performance.startedAt' }),
                  children: formatTime(detail.startedAt, intl.locale),
                },
              ]}
            />
            <div className="mt-4 text-[12px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.performance.sql' })}
            </div>
            <pre className="mt-2 max-h-[320px] overflow-auto whitespace-pre-wrap break-all border border-[#e4e7ec] bg-[#f7f8fa] p-3 font-mono text-[11px] leading-5 text-[#344054]">
              {detail.sql || intl.formatMessage({ id: 'pages.dashboard.editor.performance.sqlUnavailable' })}
            </pre>
            <div className="mt-2 text-[10px] leading-5 text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.performance.sqlHint' })}
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
