import {
  getWorkflowInstance,
  type WorkflowInstance,
  type WorkflowNodeInstance,
} from '@/services/workflow';
import { getWorkflowDefinition } from '@/services/workflow/definitions';
import { useIntl } from '@umijs/max';
import { Spin } from 'antd';
import dayjs from 'dayjs';
import { CircleAlert, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

interface WorkflowNodeInspectorLastRunProps {
  definitionId: string;
  nodeId: string;
}

const STATUS_META: Record<
  string,
  { messageId: string; textClassName: string; dotClassName: string }
> = {
  SUCCESS: {
    messageId: 'pages.workflow.editor.runtime.success',
    textClassName: 'text-[#067647]',
    dotClassName: 'bg-[#12b76a]',
  },
  SUCCESS_WITH_WARNINGS: {
    messageId: 'pages.workflow.editor.runtime.successWithWarnings',
    textClassName: 'text-[#b54708]',
    dotClassName: 'bg-[#f79009]',
  },
  RUNNING: {
    messageId: 'pages.workflow.editor.runtime.running',
    textClassName: 'text-[#175cd3]',
    dotClassName: 'bg-[#2e90fa]',
  },
  FAILED: {
    messageId: 'pages.workflow.editor.runtime.failed',
    textClassName: 'text-[#b42318]',
    dotClassName: 'bg-[#f04438]',
  },
  CANCELED: {
    messageId: 'pages.workflow.editor.runtime.canceled',
    textClassName: 'text-[#667085]',
    dotClassName: 'bg-[#98a2b3]',
  },
  TIMED_OUT: {
    messageId: 'pages.workflow.editor.runtime.timedOut',
    textClassName: 'text-[#b42318]',
    dotClassName: 'bg-[#f04438]',
  },
};

const jsonText = (value: unknown) => JSON.stringify(value ?? {}, null, 2);

const formatElapsed = (node?: WorkflowNodeInstance) => {
  const attempt = node?.attempts?.[node.attempts.length - 1];
  if (!attempt?.startedAt) return '--';
  const end = attempt.endedAt ? dayjs(attempt.endedAt) : dayjs();
  const duration = Math.max(0, end.diff(dayjs(attempt.startedAt), 'millisecond'));
  if (duration < 1000) return `${duration}ms`;
  return `${(duration / 1000).toFixed(3)}s`;
};

const JsonBlock = ({ title, value }: { title: string; value: unknown }) => (
  <section>
    <div className="mb-2 text-[12px] font-semibold text-[#344054]">{title}</div>
    <div className="max-h-[210px] overflow-auto rounded-xl bg-[#f5f6f7] px-3 py-3">
      <pre className="m-0 whitespace-pre-wrap break-words font-mono text-[11px] leading-[18px] text-[#344054]">
        {jsonText(value)}
      </pre>
    </div>
  </section>
);

const MetaRow = ({ label, value }: { label: string; value?: string | number }) => (
  <div className="flex min-h-7 items-center justify-between gap-4 text-[11px]">
    <span className="shrink-0 text-[rgba(22,24,35,.42)]">{label}</span>
    <span className="min-w-0 truncate text-right font-medium text-[#475467]">{value ?? '--'}</span>
  </div>
);

const WorkflowNodeInspectorLastRun = ({
  definitionId,
  nodeId,
}: WorkflowNodeInspectorLastRunProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const [loading, setLoading] = useState(false);
  const [instance, setInstance] = useState<WorkflowInstance>();
  const [nodeRun, setNodeRun] = useState<WorkflowNodeInstance>();
  const [loadError, setLoadError] = useState('');

  const load = useCallback(async () => {
    if (!definitionId || !nodeId) return;
    setLoading(true);
    setLoadError('');
    try {
      const definition = await getWorkflowDefinition(definitionId);
      const latestExecutionId = definition.latestExecutionId;

      if (!latestExecutionId) {
        setInstance(undefined);
        setNodeRun(undefined);
        return;
      }

      const detail = await getWorkflowInstance(latestExecutionId);
      setInstance(detail);
      setNodeRun(detail.nodes?.find((node) => node.id === nodeId));
    } catch (error) {
      setInstance(undefined);
      setNodeRun(undefined);
      setLoadError(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.editor.lastRun.loadFailed' }),
      );
    } finally {
      setLoading(false);
    }
  }, [definitionId, nodeId]);

  useEffect(() => {
    void load();
  }, [load]);

  const statusMeta = useMemo(() => {
    const status = nodeRun?.status || '';
    const meta = STATUS_META[status];
    return meta
      ? {
          label: intl.formatMessage({ id: meta.messageId }),
          textClassName: meta.textClassName,
          dotClassName: meta.dotClassName,
        }
      : {
          label: status || '--',
          textClassName: 'text-[#667085]',
          dotClassName: 'bg-[#98a2b3]',
        };
  }, [intl, nodeRun?.status]);

  if (loading) {
    return (
      <div className="flex h-full min-h-[360px] items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  if (!nodeRun) {
    return (
      <div className="flex h-full min-h-[360px] flex-col items-center justify-center px-8 text-center">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#f5f6f7] text-[#98a2b3]">
          <RefreshCw size={17} />
        </div>
        <div className="mt-3 text-[13px] font-semibold text-[#344054]">
          {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.empty' })}
        </div>
        <div className="mt-1 text-[11px] leading-5 text-[rgba(22,24,35,.42)]">
          {loadError || intl.formatMessage({ id: 'pages.workflow.editor.lastRun.emptyHint' })}
        </div>
        <button
          type="button"
          className="mt-4 rounded-lg border border-[#e4e7ec] bg-white px-3 py-1.5 text-[11px] font-medium text-[#475467] shadow-sm hover:bg-[#f7f7f8]"
          onClick={() => void load()}
        >
          {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.reload' })}
        </button>
      </div>
    );
  }

  const attempt = nodeRun.attempts?.[nodeRun.attempts.length - 1];

  return (
    <div className="space-y-5 px-4 pb-6 pt-4">
      <div className="flex items-center justify-between">
        <div className="text-[12px] font-semibold text-[#344054]">
          {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.latest' })}
        </div>
        <button
          type="button"
          className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#98a2b3] hover:bg-[#f2f4f7] hover:text-[#475467]"
          onClick={() => void load()}
          aria-label={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.refreshAria' })}
        >
          <RefreshCw size={14} />
        </button>
      </div>

      <div className="grid grid-cols-3 overflow-hidden rounded-xl border border-[#e4e7ec] bg-[#fafafa]">
        <div className="px-3 py-2.5">
          <div className="text-[9px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.status' })}
          </div>
          <div className={`mt-1 flex items-center gap-1.5 text-[11px] font-semibold ${statusMeta.textClassName}`}>
            <span className={`h-1.5 w-1.5 rounded-full ${statusMeta.dotClassName}`} />
            {statusMeta.label}
          </div>
        </div>
        <div className="border-l border-[#e4e7ec] px-3 py-2.5">
          <div className="text-[9px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.elapsed' })}
          </div>
          <div className="mt-1 text-[11px] font-semibold text-[#475467]">{formatElapsed(nodeRun)}</div>
        </div>
        <div className="border-l border-[#e4e7ec] px-3 py-2.5">
          <div className="text-[9px] text-[#98a2b3]">Attempt</div>
          <div className="mt-1 text-[11px] font-semibold text-[#475467]">{nodeRun.attemptCount || nodeRun.attempts?.length || 0}</div>
        </div>
      </div>

      <JsonBlock title={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.input' })} value={nodeRun.input} />
      <JsonBlock title={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.output' })} value={nodeRun.output} />

      {(nodeRun.errorMessage || nodeRun.failureReason) ? (
        <section>
          <div className="mb-2 text-[12px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.error' })}
          </div>
          <div className="flex items-start gap-2 rounded-xl border border-[#e4e7ec] bg-[#fafafa] px-3 py-2.5 text-[11px] leading-5 text-[#475467]">
            <CircleAlert size={14} className="mt-0.5 shrink-0 text-[#d92d50]" />
            <span className="min-w-0 break-words">{nodeRun.errorMessage || nodeRun.failureReason}</span>
          </div>
        </section>
      ) : null}

      <section className="border-t border-[#f0f1f3] pt-4">
        <div className="mb-2 text-[12px] font-semibold text-[#344054]">
          {intl.formatMessage({ id: 'pages.workflow.editor.lastRun.metadata' })}
        </div>
        <MetaRow label={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.nodeStatus' })} value={nodeRun.status} />
        <MetaRow label={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.workflowStatus' })} value={instance?.status} />
        <MetaRow label={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.currentAttempt' })} value={nodeRun.currentAttemptNumber || attempt?.attemptNumber} />
        <MetaRow label={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.startedAt' })} value={attempt?.startedAt ? dayjs(attempt.startedAt).format('YYYY-MM-DD HH:mm:ss') : '--'} />
        <MetaRow label={intl.formatMessage({ id: 'pages.workflow.editor.lastRun.endedAt' })} value={attempt?.endedAt ? dayjs(attempt.endedAt).format('YYYY-MM-DD HH:mm:ss') : '--'} />
      </section>
    </div>
  );
};

export default WorkflowNodeInspectorLastRun;
