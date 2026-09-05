import { useIntl } from '@umijs/max';
import {
  Ban,
  CheckCircle2,
  Clock3,
  LoaderCircle,
  PauseCircle,
  TimerOff,
  TriangleAlert,
  XCircle,
} from 'lucide-react';
import type { ReactNode } from 'react';
import type { NodeProps } from 'reactflow';
import {
  formatRuntimeDuration,
  isWorkflowNodeActive,
  runtimeStatusMessageId,
} from '../runtime';
import type { WorkflowNodeData } from '../types';
import WorkflowNodeControl from './WorkflowNodeControl';
import WorkflowNodeHandle from './WorkflowNodeHandle';
import WorkflowNodeRetry from './WorkflowNodeRetry';
import WorkflowNodeIcon from './icons/WorkflowNodeIcon';

interface RuntimeVisual {
  borderClassName: string;
  badgeClassName: string;
  icon: ReactNode;
}

const COMPACT_TASK_TYPES = new Set([
  'SYNC',
  'SQL',
  'SHELL',
  'PYTHON',
  'JAVA',
]);

const runtimeVisual = (status?: string): RuntimeVisual | undefined => {
  switch (status) {
    case 'WAITING':
      return {
        borderClassName: 'border-[#e4e7ec]',
        badgeClassName: 'bg-[#f5f6f7] text-[#98a2b3]',
        icon: <Clock3 size={11} />,
      };
    case 'READY':
    case 'SUBMITTED':
      return {
        borderClassName: 'border-[#f0b429] shadow-[0_0_0_2px_rgba(240,180,41,.08)]',
        badgeClassName: 'bg-[#fff8e6] text-[#946200]',
        icon: <Clock3 size={11} />,
      };
    case 'RUNNING':
    case 'RESUMING':
      return {
        borderClassName: 'border-[#6172f3] shadow-[0_0_0_3px_rgba(97,114,243,.10)]',
        badgeClassName: 'bg-[#eef0ff] text-[#4754c8]',
        icon: <LoaderCircle size={11} className="animate-spin" />,
      };
    case 'PAUSING':
    case 'PAUSED':
      return {
        borderClassName: 'border-[#f79009] shadow-[0_0_0_2px_rgba(247,144,9,.08)]',
        badgeClassName: 'bg-[#fff7e8] text-[#b54708]',
        icon: <PauseCircle size={11} />,
      };
    case 'SUCCESS':
      return {
        borderClassName: 'border-[#12b76a] shadow-[0_0_0_2px_rgba(18,183,106,.07)]',
        badgeClassName: 'bg-[#ecfdf3] text-[#067647]',
        icon: <CheckCircle2 size={11} />,
      };
    case 'SUCCESS_WITH_WARNINGS':
    case 'WARNING':
      return {
        borderClassName: 'border-[#f79009] shadow-[0_0_0_2px_rgba(247,144,9,.07)]',
        badgeClassName: 'bg-[#fff7e8] text-[#b54708]',
        icon: <TriangleAlert size={11} />,
      };
    case 'FAILED':
    case 'UPSTREAM_FAILED':
      return {
        borderClassName: 'border-[#f04438] shadow-[0_0_0_2px_rgba(240,68,56,.08)]',
        badgeClassName: 'bg-[#fef3f2] text-[#b42318]',
        icon: <XCircle size={11} />,
      };
    case 'TIMED_OUT':
      return {
        borderClassName: 'border-[#f04438] shadow-[0_0_0_2px_rgba(240,68,56,.08)]',
        badgeClassName: 'bg-[#fef3f2] text-[#b42318]',
        icon: <TimerOff size={11} />,
      };
    case 'CANCELED':
    case 'SKIPPED':
      return {
        borderClassName: 'border-[#cfd2d7]',
        badgeClassName: 'bg-[#f5f6f7] text-[#667085]',
        icon: <Ban size={11} />,
      };
    default:
      return undefined;
  }
};

const WorkflowNode = ({ id, data, selected }: NodeProps<WorkflowNodeData>) => {
  const intl = useIntl();
  const runtime = data.runtime;
  const visual = runtimeVisual(runtime?.status);
  const duration = formatRuntimeDuration(runtime?.elapsedMillis);
  const runtimeActive = isWorkflowNodeActive(runtime?.status);
  const errorTitle = runtime?.errorMessage || runtime?.failureReason;
  const normalizedTaskType = (data.taskType || '').trim().toUpperCase();
  const compactNode = COMPACT_TASK_TYPES.has(normalizedTaskType);
  const statusMessageId = runtimeStatusMessageId(runtime?.status);
  const statusLabel = statusMessageId
    ? intl.formatMessage({ id: statusMessageId })
    : runtime?.status || '';

  return (
    <div className="group relative w-60">
      {!runtimeActive ? (
        <WorkflowNodeControl
          nodeId={id}
          selected={selected}
          locked={data.locked}
          onDuplicate={data.onDuplicate}
          onDelete={data.onDelete}
        />
      ) : null}

      <WorkflowNodeHandle nodeId={id} type="target" selected={selected} locked={data.locked} />

      <div
        title={errorTitle}
        className={[
          'relative rounded-[15px] border bg-white px-3',
          compactNode ? 'py-2' : 'py-3',
          'shadow-[0_1px_2px_rgba(22,24,35,.06)]',
          'transition-[border-color,box-shadow,opacity] duration-200',
          runtimeActive ? '' : 'group-hover:shadow-[0_6px_18px_rgba(22,24,35,.10)]',
          visual?.borderClassName
            || (selected
              ? 'border-[#6172f3] shadow-[0_0_0_2px_rgba(97,114,243,.10)]'
              : 'border-[#e8e9ec] group-hover:border-[#d7d9de]'),
          selected && visual ? 'ring-1 ring-[rgba(97,114,243,.22)]' : '',
        ].join(' ')}
      >
        <div className={['flex items-center gap-2.5', compactNode ? 'min-h-8' : 'min-h-9'].join(' ')}>
          <WorkflowNodeIcon taskType={data.taskType} />
          <div className="min-w-0 flex-1 truncate text-[14px] font-semibold leading-5 text-[#161823]">
            {data.label}
          </div>
          {runtime && visual ? (
            <span
              className={[
                'inline-flex h-6 shrink-0 items-center gap-1 rounded-md px-1.5',
                'text-[10px] font-medium leading-none',
                visual.badgeClassName,
              ].join(' ')}
            >
              {visual.icon}
              {statusLabel}
            </span>
          ) : null}
        </div>

        <WorkflowNodeRetry data={data} />

        {runtime && visual ? (
          <div className="mt-2 flex min-h-5 items-center justify-between border-t border-[#f1f2f4] pt-2 text-[9px] text-[#98a2b3]">
            <div className="min-w-0 flex-1 truncate">
              {runtime.errorMessage
                ? runtime.errorMessage
                : runtime.attemptCount > 1
                  ? intl.formatMessage(
                      { id: 'pages.workflow.editor.runtime.attempt' },
                      { count: runtime.currentAttemptNumber || runtime.attemptCount },
                    )
                  : runtimeActive
                    ? intl.formatMessage({ id: 'pages.workflow.editor.runtime.executingTask' })
                    : intl.formatMessage({ id: 'pages.workflow.editor.runtime.testRun' })}
            </div>
            {duration ? <span className="ml-2 shrink-0 font-medium text-[#667085]">{duration}</span> : null}
          </div>
        ) : null}
      </div>

      <WorkflowNodeHandle
        nodeId={id}
        type="source"
        selected={selected}
        locked={data.locked}
        appendOptions={data.appendOptions}
        onAppend={data.onAppend}
      />
    </div>
  );
};

export default WorkflowNode;
