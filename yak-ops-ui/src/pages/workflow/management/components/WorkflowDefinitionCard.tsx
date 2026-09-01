import { YakButton } from '@/components/ui';
import type { WorkflowDefinition } from '@/services/workflow/definitions';
import { motion } from 'framer-motion';
import {
  CalendarClock,
  Clock3,
  GitBranch,
  LoaderCircle,
  Pause,
  Pencil,
  Play,
  Power,
  Rocket,
  RotateCcw,
  Trash2,
} from 'lucide-react';

import {
  DEFINITION_STATUS_META,
  WORKFLOW_PAGE_ANIMATION,
  formatWorkflowDuration,
  formatWorkflowTime,
  getPublishActionLabel,
  isActiveRuntime,
  isRunningRuntime,
  runtimeStatusMeta,
  type WorkflowViewMode,
} from '../model';

interface WorkflowDefinitionCardProps {
  record: WorkflowDefinition;
  viewMode: WorkflowViewMode;
  busy: boolean;
  blocked: boolean;
  onEdit: (record: WorkflowDefinition) => void;
  onSchedule: (record: WorkflowDefinition) => void;
  onDelete: (record: WorkflowDefinition) => void;
  onPublish: (record: WorkflowDefinition) => void;
  onOffline: (record: WorkflowDefinition) => void;
  onRun: (record: WorkflowDefinition) => void;
  onPause: (record: WorkflowDefinition) => void;
  onResume: (record: WorkflowDefinition) => void;
}

const actionButtonClassName =
  '!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/95 !p-0 !text-[#7e838d] !shadow-[0_1px_3px_rgba(31,35,41,0.035)] hover:!text-[#4058c8]';

const WorkflowDefinitionCard = ({
  record,
  viewMode,
  busy,
  blocked,
  onEdit,
  onSchedule,
  onDelete,
  onPublish,
  onOffline,
  onRun,
  onPause,
  onResume,
}: WorkflowDefinitionCardProps) => {
  const definitionMeta = DEFINITION_STATUS_META[record.status];
  const runtimeMeta = runtimeStatusMeta(record.latestExecutionStatus);
  const activeRuntime = isActiveRuntime(record.latestExecutionStatus);
  const isListView = viewMode === 'list';
  const canDelete = record.status !== 'ONLINE' && !activeRuntime;
  const canRun =
    record.status === 'ONLINE' &&
    record.nodeCount > 0 &&
    Boolean(record.activeVersionNo) &&
    !activeRuntime;
  const showDraftChanged = record.draftChanged && record.status !== 'DRAFT';

  const renderRuntimeAction = () => {
    const status = record.latestExecutionStatus;

    if (status === 'PAUSING' || status === 'RESUMING') {
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          disabled
          title={status === 'PAUSING' ? '最近执行暂停中' : '最近执行恢复中'}
          className={actionButtonClassName}
          icon={<LoaderCircle size={14} strokeWidth={1.9} className="animate-spin" />}
        />
      );
    }

    if (status === 'PAUSED') {
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          title="恢复最近执行"
          loading={busy}
          disabled={blocked}
          className={actionButtonClassName}
          icon={<RotateCcw size={14} strokeWidth={1.9} />}
          onClick={() => onResume(record)}
        />
      );
    }

    if (isRunningRuntime(status)) {
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          title="暂停最近执行"
          loading={busy}
          disabled={blocked}
          className={actionButtonClassName}
          icon={<Pause size={14} strokeWidth={1.9} />}
          onClick={() => onPause(record)}
        />
      );
    }

    if (record.status === 'ONLINE') {
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          title={
            canRun
              ? `运行已上线 v${record.activeVersionNo}`
              : !record.activeVersionNo
                ? '当前没有生效版本'
                : record.nodeCount <= 0
                  ? '请先完成节点编排'
                  : '当前已有活动执行'
          }
          loading={busy}
          disabled={!canRun || blocked}
          className={actionButtonClassName}
          icon={<Play size={14} strokeWidth={1.9} />}
          onClick={() => onRun(record)}
        />
      );
    }

    return null;
  };

  return (
    <motion.article
      variants={WORKFLOW_PAGE_ANIMATION.fadeUp}
      className={[
        'group relative min-w-0 overflow-hidden rounded-[16px] border border-[rgba(31,35,41,0.075)] bg-white/[0.98]',
        'shadow-[0_3px_10px_rgba(31,35,41,0.035),0_1px_2px_rgba(31,35,41,0.02)]',
        'transition-[transform,border-color,box-shadow] duration-[260ms] ease-[cubic-bezier(0.22,1,0.36,1)]',
        'hover:-translate-y-px hover:border-[rgba(31,35,41,0.11)] hover:shadow-[0_10px_24px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.02)]',
        isListView
          ? 'grid grid-cols-[minmax(430px,1.5fr)_minmax(430px,1fr)] max-xl:grid-cols-1'
          : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100 [background-image:radial-gradient(circle,rgba(94,117,163,0.14)_0.7px,transparent_0.8px)] [background-size:8px_8px] [mask-image:linear-gradient(115deg,#000_0%,rgba(0,0,0,0.18)_40%,transparent_72%)]"
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -right-16 -top-20 z-0 h-48 w-48 rounded-full bg-[#dce7ff]/35 blur-3xl transition-transform duration-300 group-hover:scale-110"
      />

      <div className="relative z-[1] flex min-h-[108px] items-start gap-3 px-4 pb-4 pt-4">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <div className="flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-[13px] border border-[rgba(31,35,41,0.07)] bg-[linear-gradient(145deg,#ffffff_0%,#f5f7fa_100%)] text-[#566071] shadow-[0_5px_14px_rgba(31,35,41,0.055)] transition-transform duration-[260ms] group-hover:scale-[1.025]">
            <GitBranch size={24} strokeWidth={1.75} />
          </div>

          <div className="min-w-0 flex-1 pt-0.5">
            <div className="flex min-w-0 flex-wrap items-center gap-1.5">
              <button
                type="button"
                title={record.name}
                className="m-0 max-w-[220px] cursor-pointer truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold leading-[21px] text-[#292c35] transition-colors hover:text-[#4058c8]"
                onClick={() => onEdit(record)}
              >
                {record.name || '未命名工作流'}
              </button>

              <span
                className={[
                  'inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded-full px-[7px] text-[10px] font-semibold',
                  definitionMeta.textClassName,
                  definitionMeta.backgroundClassName,
                ].join(' ')}
              >
                {definitionMeta.label}
              </span>

              {showDraftChanged ? (
                <span className="inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded-full bg-[#fff7e9] px-[7px] text-[10px] font-semibold text-[#b77a22]">
                  有草稿修改
                </span>
              ) : null}
            </div>

            <p
              title={record.description || ''}
              className="mb-0 mt-1.5 max-w-full truncate rounded-[7px] bg-[#f7f8fa]/90 px-2 py-1 text-[11px] leading-[18px] text-[#858a94]"
            >
              {record.description || '暂无工作流描述'}
            </p>

            <div className="mt-1.5 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-[10px] leading-4 text-[#9a9fa8]">
              <span>{record.nodeCount} 个节点 · {record.edgeCount} 条依赖</span>
              {record.workflowTimeoutSeconds > 0 ? (
                <span>超时 {formatWorkflowDuration(record.workflowTimeoutSeconds)}</span>
              ) : null}
            </div>
          </div>
        </div>

        <div className="absolute right-3 top-3 z-[3] flex -translate-y-1 gap-1 rounded-[10px] bg-white/90 p-1 opacity-0 shadow-[0_6px_18px_rgba(31,35,41,0.08)] backdrop-blur-sm transition-all duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100">
          {renderRuntimeAction()}

          <YakButton
            type="text"
            size="small"
            iconOnly
            title="编辑工作流"
            disabled={blocked}
            className={actionButtonClassName}
            icon={<Pencil size={14} strokeWidth={1.9} />}
            onClick={() => onEdit(record)}
          />

          <YakButton
            type="text"
            size="small"
            iconOnly
            title="调度配置"
            disabled={blocked}
            className={actionButtonClassName}
            icon={<CalendarClock size={14} strokeWidth={1.9} />}
            onClick={() => onSchedule(record)}
          />

          <YakButton
            type="text"
            size="small"
            iconOnly
            title={getPublishActionLabel(record)}
            loading={busy}
            disabled={blocked}
            className={actionButtonClassName}
            icon={
              record.status === 'ONLINE' ? (
                <Power size={14} strokeWidth={1.9} />
              ) : (
                <Rocket size={14} strokeWidth={1.9} />
              )
            }
            onClick={() =>
              record.status === 'ONLINE' ? onOffline(record) : onPublish(record)
            }
          />

          {canDelete ? (
            <YakButton
              type="text"
              size="small"
              danger
              iconOnly
              title="删除工作流"
              disabled={blocked}
              className="!h-[30px] !w-[30px] !rounded-[8px] !border !border-[#e9ebef] !bg-white/95 !p-0 !shadow-[0_1px_3px_rgba(31,35,41,0.035)]"
              icon={<Trash2 size={14} strokeWidth={1.9} />}
              onClick={() => onDelete(record)}
            />
          ) : null}
        </div>
      </div>

      <div
        className={[
          'relative z-[1] grid grid-cols-[1fr_0.9fr_1.05fr] border-t border-[#eef0f3] bg-white/75 px-4 py-3.5',
          isListView
            ? 'items-center border-l border-t-0 max-xl:border-l-0 max-xl:border-t'
            : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <div className="flex min-w-0 flex-col gap-1.5 pr-2.5">
          <span className="text-[10px] leading-4 text-[#a0a4ad]">发布版本</span>
          <strong className="truncate text-[11px] font-semibold leading-[18px] text-[#5c616b]">
            {record.activeVersionNo
              ? `生效 v${record.activeVersionNo}${
                  record.latestVersionNo > record.activeVersionNo
                    ? ` · 最新 v${record.latestVersionNo}`
                    : ''
                }`
              : record.latestVersionNo > 0
                ? `最新 v${record.latestVersionNo}`
                : '尚未发布'}
          </strong>
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 border-l border-[#eff0f2] px-2.5">
          <span className="text-[10px] leading-4 text-[#a0a4ad]">最近执行</span>
          <div className="flex min-w-0 items-center">
            <span
              className={[
                'inline-flex h-5 max-w-full shrink-0 items-center gap-1.5 truncate rounded-full px-[7px] text-[10px] font-semibold',
                runtimeMeta.textClassName,
                runtimeMeta.backgroundClassName,
              ].join(' ')}
            >
              <span className={['h-1.5 w-1.5 shrink-0 rounded-full', runtimeMeta.dotClassName].join(' ')} />
              <span className="truncate">{runtimeMeta.label}</span>
            </span>
          </div>
        </div>

        <div className="flex min-w-0 flex-col gap-1.5 border-l border-[#eff0f2] pl-2.5">
          <span className="text-[10px] leading-4 text-[#a0a4ad]">最近更新</span>
          <strong className="flex min-w-0 items-center gap-1.5 truncate text-[11px] font-medium leading-[18px] text-[#737882]">
            <Clock3 size={11} strokeWidth={1.8} className="shrink-0 text-[#9ca0a9]" />
            <span className="truncate">{formatWorkflowTime(record.updateTime)}</span>
          </strong>
        </div>
      </div>
    </motion.article>
  );
};

export default WorkflowDefinitionCard;
