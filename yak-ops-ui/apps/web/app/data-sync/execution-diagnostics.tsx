import {
  Alert,
  Badge,
  Button,
  SectionCard,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Table,
  type BadgeProps,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import {
  getDataSyncTraceSummary,
  listDataSyncSinkTrace,
  listDataSyncSourceTrace,
  type DataSyncAttemptRecord,
  type DataSyncInstanceRecord,
  type DataSyncSinkTraceRecord,
  type DataSyncSourceTraceRecord,
  type DataSyncTraceStatus,
  type DataSyncTraceSummary,
} from "@/service/data-sync";

import { isActiveDataSyncInstance } from "./execution-detail";
import { DataSyncExecutionLogPanel } from "./execution-log";

const TRACE_PAGE_SIZE = 50;
const POLL_INTERVAL_MILLIS = 2000;

type TraceStatusFilter = "ALL" | DataSyncTraceStatus;

const TRACE_STATUS_ITEMS = {
  ALL: "全部状态",
  SUCCESS: "成功",
  FAILED: "失败",
};

const countFormatter = new Intl.NumberFormat("zh-CN");

const formatCount = (value?: number) => countFormatter.format(value || 0);

const formatDuration = (value?: number) => {
  const millis = value || 0;
  if (millis < 1000) return `${millis} ms`;
  if (millis < 60_000) return `${(millis / 1000).toFixed(millis < 10_000 ? 2 : 1)} s`;
  const minutes = Math.floor(millis / 60_000);
  const seconds = Math.round((millis % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const traceTone = (status?: string): BadgeProps["tone"] =>
  status === "FAILED" ? "danger" : "success";

const rangeText = (record: DataSyncSourceTraceRecord) => {
  if (!record.splitColumn) return "整表";
  return `${record.splitColumn} [${record.lowerBoundInclusive ?? "-"}, ${record.upperBoundInclusive ?? "-"}]`;
};

function MetricCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-[#eef0f3] bg-white px-4 py-3">
      <div className="text-xs text-[#98a2b3]">{label}</div>
      <div className="mt-1 truncate text-xl font-semibold tracking-tight text-[#242731]">
        {value}
      </div>
      {hint ? <div className="mt-1 truncate text-[11px] text-[#98a2b3]">{hint}</div> : null}
    </div>
  );
}

function TraceStatusSelect({
  value,
  onChange,
}: {
  value: TraceStatusFilter;
  onChange: (value: TraceStatusFilter) => void;
}) {
  return (
    <Select
      size="small"
      items={TRACE_STATUS_ITEMS}
      value={value}
      onValueChange={(next) => onChange(String(next || "ALL") as TraceStatusFilter)}
    >
      <SelectTrigger variant="outlined" className="w-[112px]">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="ALL">
          <SelectItemText>全部状态</SelectItemText>
          <SelectItemIndicator />
        </SelectItem>
        <SelectItem value="SUCCESS">
          <SelectItemText>成功</SelectItemText>
          <SelectItemIndicator />
        </SelectItem>
        <SelectItem value="FAILED">
          <SelectItemText>失败</SelectItemText>
          <SelectItemIndicator />
        </SelectItem>
      </SelectContent>
    </Select>
  );
}

interface DataSyncExecutionDiagnosticsProps {
  record?: DataSyncInstanceRecord;
  attempts: DataSyncAttemptRecord[];
  active: boolean;
}

export function DataSyncExecutionDiagnostics({
  record,
  attempts,
  active,
}: DataSyncExecutionDiagnosticsProps) {
  const executionId = record?.id;
  const live = isActiveDataSyncInstance(record);
  const defaultAttemptNo =
    record?.currentAttempt || Math.max(1, ...attempts.map((attempt) => attempt.attemptNo || 1));

  const [attemptNo, setAttemptNo] = useState(defaultAttemptNo);
  const [summary, setSummary] = useState<DataSyncTraceSummary>();
  const [sourceRecords, setSourceRecords] = useState<DataSyncSourceTraceRecord[]>([]);
  const [sinkRecords, setSinkRecords] = useState<DataSyncSinkTraceRecord[]>([]);
  const [sourceFailures, setSourceFailures] = useState<DataSyncSourceTraceRecord[]>([]);
  const [sinkFailures, setSinkFailures] = useState<DataSyncSinkTraceRecord[]>([]);
  const [sourceCursor, setSourceCursor] = useState<string>();
  const [sinkCursor, setSinkCursor] = useState<string>();
  const [sourceHasMore, setSourceHasMore] = useState(false);
  const [sinkHasMore, setSinkHasMore] = useState(false);
  const [sourceStatus, setSourceStatus] = useState<TraceStatusFilter>("ALL");
  const [sinkStatus, setSinkStatus] = useState<TraceStatusFilter>("ALL");
  const [selectedSource, setSelectedSource] = useState<DataSyncSourceTraceRecord>();
  const [lifecycleOpen, setLifecycleOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sourceLoadingMore, setSourceLoadingMore] = useState(false);
  const [sinkLoadingMore, setSinkLoadingMore] = useState(false);

  const attemptItems = useMemo(
    () =>
      Object.fromEntries(
        [...attempts]
          .sort((a, b) => b.attemptNo - a.attemptNo)
          .map((attempt) => [
            String(attempt.attemptNo),
            `Attempt #${attempt.attemptNo} · ${attempt.status}`,
          ]),
      ),
    [attempts],
  );

  const loadFailures = useCallback(async (id: string, currentAttemptNo: number) => {
    const [sourceResult, sinkResult] = await Promise.all([
      listDataSyncSourceTrace(id, {
        attemptNo: currentAttemptNo,
        pageSize: 10,
        status: "FAILED",
      }),
      listDataSyncSinkTrace(id, {
        attemptNo: currentAttemptNo,
        pageSize: 10,
        status: "FAILED",
      }),
    ]);
    setSourceFailures(sourceResult?.records || []);
    setSinkFailures(sinkResult?.records || []);
  }, []);

  const loadDiagnostics = useCallback(
    async (silent = false) => {
      if (!active || !executionId) return;
      if (!silent) setLoading(true);
      try {
        const [summaryValue, sourceResult, sinkResult] = await Promise.all([
          getDataSyncTraceSummary(executionId, attemptNo),
          listDataSyncSourceTrace(executionId, {
            attemptNo,
            pageSize: TRACE_PAGE_SIZE,
            status: sourceStatus === "ALL" ? undefined : sourceStatus,
          }),
          listDataSyncSinkTrace(executionId, {
            attemptNo,
            pageSize: TRACE_PAGE_SIZE,
            status: sinkStatus === "ALL" ? undefined : sinkStatus,
          }),
        ]);
        setSummary(summaryValue);
        setSourceRecords(sourceResult?.records || []);
        setSourceCursor(sourceResult?.nextCursor);
        setSourceHasMore(Boolean(sourceResult?.hasMore));
        setSinkRecords(sinkResult?.records || []);
        setSinkCursor(sinkResult?.nextCursor);
        setSinkHasMore(Boolean(sinkResult?.hasMore));
        setSelectedSource(undefined);

        if ((summaryValue?.errorCount || 0) > 0) {
          await loadFailures(executionId, attemptNo);
        } else {
          setSourceFailures([]);
          setSinkFailures([]);
        }
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [active, attemptNo, executionId, loadFailures, sinkStatus, sourceStatus],
  );

  const refreshSummary = useCallback(async () => {
    if (!active || !executionId) return;
    const value = await getDataSyncTraceSummary(executionId, attemptNo);
    setSummary(value);
    if ((value?.errorCount || 0) > 0 && sourceFailures.length + sinkFailures.length === 0) {
      await loadFailures(executionId, attemptNo);
    }
  }, [active, attemptNo, executionId, loadFailures, sinkFailures.length, sourceFailures.length]);

  useEffect(() => {
    setAttemptNo(defaultAttemptNo);
    setSummary(undefined);
    setSourceRecords([]);
    setSinkRecords([]);
    setSourceFailures([]);
    setSinkFailures([]);
    setSourceCursor(undefined);
    setSinkCursor(undefined);
    setSourceHasMore(false);
    setSinkHasMore(false);
    setSelectedSource(undefined);
    setSourceStatus("ALL");
    setSinkStatus("ALL");
    setLifecycleOpen(false);
  }, [executionId, defaultAttemptNo]);

  useEffect(() => {
    void loadDiagnostics();
  }, [loadDiagnostics]);

  useEffect(() => {
    if (!active || !executionId || !live) return;
    const timer = window.setInterval(() => void refreshSummary(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [active, executionId, live, refreshSummary]);

  const loadMoreSource = async () => {
    if (!executionId || !sourceCursor || sourceLoadingMore) return;
    setSourceLoadingMore(true);
    try {
      const result = await listDataSyncSourceTrace(executionId, {
        attemptNo,
        pageSize: TRACE_PAGE_SIZE,
        cursor: sourceCursor,
        status: sourceStatus === "ALL" ? undefined : sourceStatus,
      });
      setSourceRecords((current) => [...current, ...(result?.records || [])]);
      setSourceCursor(result?.nextCursor);
      setSourceHasMore(Boolean(result?.hasMore));
    } finally {
      setSourceLoadingMore(false);
    }
  };

  const loadMoreSink = async () => {
    if (!executionId || !sinkCursor || sinkLoadingMore) return;
    setSinkLoadingMore(true);
    try {
      const result = await listDataSyncSinkTrace(executionId, {
        attemptNo,
        pageSize: TRACE_PAGE_SIZE,
        cursor: sinkCursor,
        status: sinkStatus === "ALL" ? undefined : sinkStatus,
      });
      setSinkRecords((current) => [...current, ...(result?.records || [])]);
      setSinkCursor(result?.nextCursor);
      setSinkHasMore(Boolean(result?.hasMore));
    } finally {
      setSinkLoadingMore(false);
    }
  };

  const sourceColumns: TableColumns<DataSyncSourceTraceRecord> = [
    {
      key: "range",
      title: "Split 范围",
      minWidth: 220,
      render: (_value, item) => (
        <div className="min-w-0">
          <div className="truncate font-mono text-xs text-[#344054]" title={rangeText(item)}>
            {rangeText(item)}
          </div>
          <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]" title={item.splitId}>
            {item.splitId}
          </div>
        </div>
      ),
    },
    {
      key: "rows",
      title: "读取行数",
      width: 110,
      align: "right",
      render: (_value, item) => formatCount(item.rows),
    },
    {
      key: "duration",
      title: "读取耗时",
      width: 110,
      align: "right",
      render: (_value, item) => formatDuration(item.durationMillis),
    },
    {
      key: "status",
      title: "状态",
      width: 90,
      render: (_value, item) => (
        <Badge tone={traceTone(item.status)}>{item.status === "FAILED" ? "失败" : "成功"}</Badge>
      ),
    },
    {
      key: "sql",
      title: "诊断",
      width: 90,
      render: (_value, item) => (
        <Button size="small" variant="ghost" onClick={() => setSelectedSource(item)}>
          查看 SQL
        </Button>
      ),
    },
  ];

  const sinkColumns: TableColumns<DataSyncSinkTraceRecord> = [
    {
      key: "batchNo",
      title: "Batch",
      width: 90,
      render: (_value, item) => <span className="font-mono text-xs">#{item.batchNo}</span>,
    },
    {
      key: "rows",
      title: "Rows",
      width: 100,
      align: "right",
      render: (_value, item) => formatCount(item.rows),
    },
    {
      key: "execute",
      title: "Execute",
      width: 110,
      align: "right",
      render: (_value, item) => formatDuration(item.executeDurationMillis),
    },
    {
      key: "commit",
      title: "Commit",
      width: 110,
      align: "right",
      render: (_value, item) => formatDuration(item.commitDurationMillis),
    },
    {
      key: "total",
      title: "总耗时",
      width: 110,
      align: "right",
      render: (_value, item) =>
        formatDuration((item.executeDurationMillis || 0) + (item.commitDurationMillis || 0)),
    },
    {
      key: "status",
      title: "状态",
      width: 90,
      render: (_value, item) => (
        <Badge tone={traceTone(item.status)}>{item.status === "FAILED" ? "失败" : "成功"}</Badge>
      ),
    },
  ];

  const lifecycleSection = (
    <SectionCard title="生命周期事件">
      <div className="flex items-center justify-between gap-4">
        <div className="text-xs leading-5 text-[#98a2b3]">
          保留低频 Execution / Attempt 生命周期事件，用于审计状态变化，不作为运行诊断主视图。
        </div>
        <Button size="small" variant="ghost" onClick={() => setLifecycleOpen((value) => !value)}>
          {lifecycleOpen ? "收起" : "展开"}
        </Button>
      </div>
      {lifecycleOpen ? (
        <div className="mt-3">
          <DataSyncExecutionLogPanel record={record} active={active} embedded />
        </div>
      ) : null}
    </SectionCard>
  );

  if (!record) {
    return (
      <SectionCard title="执行诊断">
        <div className="flex min-h-56 items-center justify-center text-sm text-[#98a2b3]">
          选择左侧执行记录查看诊断信息
        </div>
      </SectionCard>
    );
  }

  if (loading && !summary) {
    return (
      <SectionCard title="执行诊断">
        <div className="flex min-h-56 items-center justify-center">
          <Spinner size="large" label="加载执行诊断" />
        </div>
      </SectionCard>
    );
  }

  if (summary && !summary.available) {
    return (
      <div className="space-y-4">
        <Alert>
          当前 Execution 没有 Runtime Trace。它可能创建于诊断能力上线前，或 Trace Store
          未能创建会话。
        </Alert>
        {lifecycleSection}
      </div>
    );
  }

  const errors = [
    ...sourceFailures.map((item) => ({
      key: `source-${item.splitId}-${item.timestamp}`,
      side: "SOURCE",
      scope: rangeText(item),
      rows: item.rows,
      duration: item.durationMillis,
      stage: item.failureStage,
      errorType: item.errorType,
      message: item.errorMessage,
    })),
    ...sinkFailures.map((item) => ({
      key: `sink-${item.batchNo}-${item.timestamp}`,
      side: "SINK",
      scope: `Batch #${item.batchNo}`,
      rows: item.rows,
      duration: (item.executeDurationMillis || 0) + (item.commitDurationMillis || 0),
      stage: item.failureStage,
      errorType: item.errorType,
      message: item.errorMessage,
    })),
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {attempts.length > 1 ? (
            <Select
              size="small"
              items={attemptItems}
              value={String(attemptNo)}
              onValueChange={(value) => setAttemptNo(Number(value || defaultAttemptNo))}
            >
              <SelectTrigger variant="outlined" className="w-[190px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {[...attempts]
                  .sort((a, b) => b.attemptNo - a.attemptNo)
                  .map((attempt) => (
                    <SelectItem key={attempt.id} value={String(attempt.attemptNo)}>
                      <SelectItemText>
                        Attempt #{attempt.attemptNo} · {attempt.status}
                      </SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          ) : (
            <span className="text-xs text-[#667085]">Attempt #{attemptNo}</span>
          )}
          {live ? <span className="text-xs text-[#98a2b3]">运行中 · 汇总 2s 自动刷新</span> : null}
        </div>
        <Button
          size="small"
          variant="ghost"
          disabled={loading}
          onClick={() => void loadDiagnostics()}
        >
          <RefreshCw
            size={14}
            className={loading ? "animate-spin motion-reduce:animate-none" : undefined}
          />
          刷新
        </Button>
      </div>

      <div className="grid grid-cols-4 gap-3 max-xl:grid-cols-2 max-md:grid-cols-1">
        <MetricCard label="Source 读取" value={formatCount(summary?.sourceRows)} hint="rows" />
        <MetricCard
          label="Target 写入"
          value={formatCount(summary?.sinkRows)}
          hint="committed rows"
        />
        <MetricCard
          label="Source Splits"
          value={`${formatCount(
            (summary?.sourceFinishedSplitCount || 0) + (summary?.sourceFailedSplitCount || 0),
          )} / ${formatCount(summary?.sourceSplitCount)}`}
          hint="完成 / 规划"
        />
        <MetricCard
          label="Sink Batches"
          value={formatCount(
            (summary?.sinkCommittedBatchCount || 0) + (summary?.sinkFailedBatchCount || 0),
          )}
          hint={`成功 ${formatCount(summary?.sinkCommittedBatchCount)} · 失败 ${formatCount(
            summary?.sinkFailedBatchCount,
          )}`}
        />
      </div>

      {(summary?.droppedEventCount || 0) > 0 ? (
        <Alert>
          有 {formatCount(summary?.droppedEventCount)} 条 Runtime Trace
          事件未记录。数据同步结果不受影响， 但本次诊断明细可能不完整。
        </Alert>
      ) : null}

      {(summary?.errorCount || 0) > 0 ? (
        <SectionCard title="执行异常">
          <div className="space-y-3">
            {errors.length === 0 ? (
              <div className="text-sm text-[#667085]">
                已记录 {formatCount(summary?.errorCount)} 个异常事件，错误明细暂未读取到。
              </div>
            ) : (
              errors.map((error) => (
                <div
                  key={error.key}
                  className="rounded-lg border border-[#f4d7d7] bg-[#fffafa] px-4 py-3"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone="danger">{error.side}</Badge>
                    <span className="font-medium text-[13px] text-[#344054]">{error.scope}</span>
                    {error.stage ? (
                      <span className="font-mono text-[11px] text-[#98a2b3]">{error.stage}</span>
                    ) : null}
                  </div>
                  <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs text-[#667085]">
                    <span>Rows：{formatCount(error.rows)}</span>
                    <span>耗时：{formatDuration(error.duration)}</span>
                    {error.errorType ? <span>类型：{error.errorType}</span> : null}
                  </div>
                  {error.message ? (
                    <div className="mt-2 break-words font-mono text-xs leading-5 text-[#b42318]">
                      {error.message}
                    </div>
                  ) : null}
                </div>
              ))
            )}
          </div>
        </SectionCard>
      ) : null}

      <SectionCard title="Source 读取">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="text-xs text-[#98a2b3]">
            Split 总耗时 {formatDuration(summary?.sourceSplitDurationMillis)} · 当前加载{" "}
            {formatCount(sourceRecords.length)} 条
          </div>
          <TraceStatusSelect value={sourceStatus} onChange={setSourceStatus} />
        </div>

        <Table<DataSyncSourceTraceRecord>
          columns={sourceColumns}
          dataSource={sourceRecords}
          rowKey={(item) => `${item.splitId}-${item.timestamp}`}
          pagination={false}
          size="small"
          bordered
          scroll={{ x: 760 }}
          emptyText="暂无 Source Split 诊断记录"
        />

        {selectedSource ? (
          <div className="mt-4 rounded-lg border border-[#eef0f3] bg-[#fafafa] p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold text-[#344054]">
                  {rangeText(selectedSource)}
                </div>
                <div className="mt-1 font-mono text-[11px] text-[#98a2b3]">
                  {selectedSource.workerName || "-"}
                </div>
              </div>
              <Button size="small" variant="ghost" onClick={() => setSelectedSource(undefined)}>
                收起
              </Button>
            </div>
            <div className="mt-3 text-xs text-[#98a2b3]">SQL</div>
            <pre className="mt-1 overflow-x-auto whitespace-pre-wrap break-words rounded-md border border-[#e6e8eb] bg-white px-3 py-2 font-mono text-xs leading-5 text-[#344054]">
              {selectedSource.sql || "-"}
            </pre>
            {selectedSource.parameters?.length ? (
              <>
                <div className="mt-3 text-xs text-[#98a2b3]">Split Parameters</div>
                <div className="mt-1 font-mono text-xs text-[#344054]">
                  [{selectedSource.parameters.join(", ")}]
                </div>
              </>
            ) : null}
          </div>
        ) : null}

        {sourceHasMore ? (
          <div className="mt-3 flex justify-center">
            <Button
              size="small"
              variant="ghost"
              loading={sourceLoadingMore}
              onClick={() => void loadMoreSource()}
            >
              加载更多
            </Button>
          </div>
        ) : null}
      </SectionCard>

      <SectionCard title="Target 写入">
        <div className="mb-4 grid grid-cols-[160px_160px_minmax(0,1fr)] gap-4 max-lg:grid-cols-1">
          <div>
            <div className="text-xs text-[#98a2b3]">写入模式</div>
            <div className="mt-1 text-[13px] text-[#344054]">
              {[summary?.sinkSaveMode, summary?.sinkWriteMode].filter(Boolean).join(" + ") || "-"}
            </div>
          </div>
          <div>
            <div className="text-xs text-[#98a2b3]">Batch Size</div>
            <div className="mt-1 font-mono text-[13px] text-[#344054]">
              {summary?.sinkBatchSize || "-"}
            </div>
          </div>
          <div className="min-w-0">
            <div className="text-xs text-[#98a2b3]">SQL Template</div>
            <pre className="mt-1 max-h-28 overflow-auto whitespace-pre-wrap break-words rounded-md border border-[#eef0f3] bg-[#fafafa] px-3 py-2 font-mono text-xs leading-5 text-[#344054]">
              {summary?.sinkSql || "-"}
            </pre>
          </div>
        </div>

        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="text-xs text-[#98a2b3]">
            Execute {formatDuration(summary?.sinkExecuteDurationMillis)} · Commit{" "}
            {formatDuration(summary?.sinkCommitDurationMillis)} · 当前加载{" "}
            {formatCount(sinkRecords.length)} 条
          </div>
          <TraceStatusSelect value={sinkStatus} onChange={setSinkStatus} />
        </div>

        <Table<DataSyncSinkTraceRecord>
          columns={sinkColumns}
          dataSource={sinkRecords}
          rowKey={(item) => `${item.batchNo}-${item.timestamp}`}
          pagination={false}
          size="small"
          bordered
          scroll={{ x: 700 }}
          emptyText="暂无 Sink Batch 诊断记录"
        />

        {sinkHasMore ? (
          <div className="mt-3 flex justify-center">
            <Button
              size="small"
              variant="ghost"
              loading={sinkLoadingMore}
              onClick={() => void loadMoreSink()}
            >
              加载更多
            </Button>
          </div>
        ) : null}
      </SectionCard>

      {lifecycleSection}
    </div>
  );
}
