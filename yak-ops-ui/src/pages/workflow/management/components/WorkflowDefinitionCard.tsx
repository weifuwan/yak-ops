import {
  EmojiIcon,
  normalizeEmojiIconValue,
} from '@/components/EmojiIconPicker';
import { YakButton } from '@/components/ui';
import type { WorkflowDefinition } from '@/services/workflow/definitions';
import { useIntl } from '@umijs/max';
import { Dropdown, type MenuProps } from 'antd';
import {
  Clock3,
  LoaderCircle,
  MoreHorizontal,
  Pause,
  Play,
  Rocket,
  RotateCcw,
} from 'lucide-react';
import { useState } from 'react';

import {
  DEFINITION_STATUS_META,
  formatWorkflowTime,
  getPublishActionMessageId,
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

const compactActionButtonClassName =
  '!h-[32px] !w-[32px] !rounded-[8px] !border-0 !bg-transparent !p-0 !text-[#747985] !shadow-none transition-colors hover:!bg-[#f5f6f8] hover:!text-[#4058c8] disabled:!bg-transparent';
const moreActionButtonClassName =
  '!h-[32px] !w-[32px] !rounded-[8px] !border-0 !bg-[#f5f6f8] !p-0 !text-[#7f8590] !shadow-none transition-colors hover:!bg-[#eef0f3] hover:!text-[#3f4652]';

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
  const intl = useIntl();
  const [moreOpen, setMoreOpen] = useState(false);
  const definitionMeta = DEFINITION_STATUS_META[record.status];
  const runtimeMeta = runtimeStatusMeta(record.latestExecutionStatus);
  const activeRuntime = isActiveRuntime(record.latestExecutionStatus);
  const definitionIcon = normalizeEmojiIconValue(record.editorMeta?.icon);
  const isListView = viewMode === 'list';
  const canDelete = record.status !== 'ONLINE' && !activeRuntime;
  const canRun =
    record.status === 'ONLINE' &&
    record.nodeCount > 0 &&
    Boolean(record.activeVersionNo) &&
    !activeRuntime;
  const showDraftChanged = record.draftChanged && record.status !== 'DRAFT';
  const publishActionLabel = intl.formatMessage({
    id: getPublishActionMessageId(record),
  });
  const definitionLabel = intl.formatMessage({ id: definitionMeta.messageId });
  const runtimeLabel = runtimeMeta.labelId
    ? intl.formatMessage({ id: runtimeMeta.labelId })
    : runtimeMeta.rawLabel || '-';

  const handlePublishAction = () => {
    if (record.status === 'ONLINE') onOffline(record);
    else onPublish(record);
  };

  const renderPrimaryAction = () => {
    const status = record.latestExecutionStatus;
    if (status === 'PAUSING' || status === 'RESUMING') {
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          disabled
          title={intl.formatMessage({
            id:
              status === 'PAUSING'
                ? 'pages.workflow.definition.card.pausing'
                : 'pages.workflow.definition.card.resuming',
          })}
          className={compactActionButtonClassName}
          icon={<LoaderCircle size={15} strokeWidth={1.9} className="animate-spin" />}
        />
      );
    }
    if (status === 'PAUSED') {
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          title={intl.formatMessage({ id: 'pages.workflow.definition.card.resume' })}
          loading={busy}
          disabled={blocked}
          className={compactActionButtonClassName}
          icon={<RotateCcw size={15} strokeWidth={1.9} />}
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
          title={intl.formatMessage({ id: 'pages.workflow.definition.card.pause' })}
          loading={busy}
          disabled={blocked}
          className={compactActionButtonClassName}
          icon={<Pause size={15} strokeWidth={1.9} />}
          onClick={() => onPause(record)}
        />
      );
    }
    if (record.status === 'ONLINE') {
      const title = canRun
        ? intl.formatMessage(
            { id: 'pages.workflow.definition.card.runVersion' },
            { version: record.activeVersionNo || 0 },
          )
        : intl.formatMessage({
            id: !record.activeVersionNo
              ? 'pages.workflow.definition.card.noActiveVersion'
              : record.nodeCount <= 0
                ? 'pages.workflow.definition.card.finishNodes'
                : 'pages.workflow.definition.card.activeExecution',
          });
      return (
        <YakButton
          type="text"
          size="small"
          iconOnly
          title={title}
          loading={busy}
          disabled={!canRun || blocked}
          className={compactActionButtonClassName}
          icon={<Play size={15} strokeWidth={1.9} />}
          onClick={() => onRun(record)}
        />
      );
    }
    return (
      <YakButton
        type="text"
        size="small"
        iconOnly
        title={publishActionLabel}
        loading={busy}
        disabled={blocked}
        className={compactActionButtonClassName}
        icon={<Rocket size={15} strokeWidth={1.9} />}
        onClick={() => onPublish(record)}
      />
    );
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'edit',
      label: intl.formatMessage({ id: 'pages.workflow.definition.card.edit' }),
      disabled: blocked,
      style: { height: 36, lineHeight: '36px', paddingInline: 12, borderRadius: 8, fontSize: 13 },
    },
    {
      key: 'schedule',
      label: intl.formatMessage({ id: 'pages.workflow.definition.card.schedule' }),
      disabled: blocked,
      style: { height: 36, lineHeight: '36px', paddingInline: 12, borderRadius: 8, fontSize: 13 },
    },
    { type: 'divider' },
    {
      key: 'publish',
      label: publishActionLabel,
      disabled: blocked || busy,
      style: { height: 36, lineHeight: '36px', paddingInline: 12, borderRadius: 8, fontSize: 13 },
    },
    ...(canDelete
      ? [
          { type: 'divider' as const },
          {
            key: 'delete',
            label: intl.formatMessage({ id: 'pages.workflow.definition.card.delete' }),
            danger: true,
            disabled: blocked,
            style: { height: 36, lineHeight: '36px', paddingInline: 12, borderRadius: 8, fontSize: 13 },
          },
        ]
      : []),
  ];

  const handleMenuClick: MenuProps['onClick'] = ({ key, domEvent }) => {
    domEvent.stopPropagation();
    if (key === 'edit') onEdit(record);
    else if (key === 'schedule') onSchedule(record);
    else if (key === 'publish') handlePublishAction();
    else if (key === 'delete') onDelete(record);
  };

  const publishedVersionText = record.activeVersionNo
    ? `${intl.formatMessage(
        { id: 'pages.workflow.definition.card.activeVersion' },
        { version: record.activeVersionNo },
      )}${
        record.latestVersionNo > record.activeVersionNo
          ? ` · ${intl.formatMessage(
              { id: 'pages.workflow.definition.card.latestVersion' },
              { version: record.latestVersionNo },
            )}`
          : ''
      }`
    : record.latestVersionNo > 0
      ? intl.formatMessage(
          { id: 'pages.workflow.definition.card.latestVersion' },
          { version: record.latestVersionNo },
        )
      : intl.formatMessage({ id: 'pages.workflow.definition.card.neverPublished' });

  const fallbackName = intl.formatMessage({ id: 'pages.workflow.definition.card.defaultName' });

  return (
    <div
      className={[
        'group relative min-w-0 overflow-hidden rounded-[16px] border border-[rgba(31,35,41,0.075)] bg-white/[0.98]',
        'shadow-[0_3px_10px_rgba(31,35,41,0.035),0_1px_2px_rgba(31,35,41,0.02)]',
        'transition-[transform,border-color,box-shadow] duration-[260ms] ease-[cubic-bezier(0.22,1,0.36,1)]',
        'hover:border-[rgba(31,35,41,0.11)] hover:shadow-[0_10px_24px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.02)]',
        isListView
          ? 'grid grid-cols-[minmax(430px,1.5fr)_minmax(430px,1fr)] max-xl:grid-cols-1'
          : '',
      ].filter(Boolean).join(' ')}
    >
      <span aria-hidden="true" className="pointer-events-none absolute inset-0 z-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100 [background-image:radial-gradient(circle,rgba(94,117,163,0.14)_0.7px,transparent_0.8px)] [background-size:8px_8px] [mask-image:linear-gradient(115deg,#000_0%,rgba(0,0,0,0.18)_40%,transparent_72%)]" />
      <span aria-hidden="true" className="pointer-events-none absolute -right-16 -top-20 z-0 h-48 w-48 rounded-full bg-[#dce7ff]/35 blur-3xl transition-transform duration-300 group-hover:scale-110" />

      <div className="relative z-[1] flex min-h-[108px] items-start gap-3 px-4 pb-4 pt-4">
        <div className="flex min-w-0 flex-1 items-start gap-3 pr-[74px]">
          <EmojiIcon
            value={definitionIcon}
            size={46}
            title={intl.formatMessage(
              { id: 'pages.workflow.definition.card.iconTitle' },
              { name: record.name || fallbackName },
            )}
            className="border border-[rgba(31,35,41,0.07)] shadow-[0_5px_14px_rgba(31,35,41,0.055)] transition-transform duration-[260ms] group-hover:scale-[1.025]"
          />
          <div className="min-w-0 flex-1 pt-0.5">
            <div className="flex min-w-0 flex-wrap items-center gap-1.5">
              <button
                type="button"
                title={record.name}
                className="m-0 max-w-[220px] cursor-pointer truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold leading-[21px] text-[#292c35] transition-colors hover:text-[#4058c8]"
                onClick={() => onEdit(record)}
              >
                {record.name || intl.formatMessage({ id: 'pages.workflow.definition.card.unnamed' })}
              </button>
              <span
                className={[
                  'inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded-full px-[7px] text-[10px] font-semibold',
                  definitionMeta.textClassName,
                  definitionMeta.backgroundClassName,
                ].join(' ')}
              >
                {definitionLabel}
              </span>
              {showDraftChanged ? (
                <span className="inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded-full bg-[#fff7e9] px-[7px] text-[10px] font-semibold text-[#b77a22]">
                  {intl.formatMessage({ id: 'pages.workflow.definition.card.draftChanged' })}
                </span>
              ) : null}
            </div>
            <p
              title={record.description || ''}
              className="mb-0 mt-1.5 max-w-full truncate rounded-[7px] bg-[#f7f8fa]/90 px-2 py-1 text-[11px] leading-[18px] text-[#858a94]"
            >
              {record.description || intl.formatMessage({ id: 'pages.workflow.definition.card.noDescription' })}
            </p>
          </div>
        </div>

        <div
          className={[
            'absolute right-3 top-3 z-[3] flex items-center rounded-[11px] border border-[#eceef2] bg-white p-[3px]',
            'shadow-[0_5px_16px_rgba(31,35,41,0.08)] transition-[opacity,transform,box-shadow] duration-200 ease-out',
            moreOpen
              ? 'translate-y-0 opacity-100'
              : '-translate-y-1 opacity-0 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100',
          ].join(' ')}
        >
          {renderPrimaryAction()}
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            open={moreOpen}
            onOpenChange={setMoreOpen}
            menu={{
              items: menuItems,
              onClick: handleMenuClick,
              style: {
                width: 196,
                padding: 6,
                borderRadius: 12,
                boxShadow:
                  '0 14px 36px rgba(31, 35, 41, 0.12), 0 2px 8px rgba(31, 35, 41, 0.05)',
              },
            }}
          >
            <span onClick={(event) => event.stopPropagation()}>
              <YakButton
                type="text"
                size="small"
                iconOnly
                title={intl.formatMessage({ id: 'pages.workflow.definition.card.more' })}
                className={moreActionButtonClassName}
                icon={<MoreHorizontal size={17} strokeWidth={2} />}
              />
            </span>
          </Dropdown>
        </div>
      </div>

      <div
        className={[
          'relative z-[1] grid grid-cols-[1fr_0.9fr_1.05fr] border-t border-[#eef0f3] bg-white/75 px-4 py-3.5',
          isListView ? 'items-center border-l border-t-0 max-xl:border-l-0 max-xl:border-t' : '',
        ].filter(Boolean).join(' ')}
      >
        <div className="flex min-w-0 flex-col gap-1.5 pr-2.5">
          <span className="text-[10px] leading-4 text-[#a0a4ad]">
            {intl.formatMessage({ id: 'pages.workflow.definition.card.publishVersion' })}
          </span>
          <strong className="truncate text-[11px] font-semibold leading-[18px] text-[#5c616b]">
            {publishedVersionText}
          </strong>
        </div>
        <div className="flex min-w-0 flex-col gap-1.5 border-l border-[#eff0f2] px-2.5">
          <span className="text-[10px] leading-4 text-[#a0a4ad]">
            {intl.formatMessage({ id: 'pages.workflow.definition.card.latestExecution' })}
          </span>
          <div className="flex min-w-0 items-center">
            <span
              className={[
                'inline-flex h-5 max-w-full shrink-0 items-center gap-1.5 truncate rounded-full px-[7px] text-[10px] font-semibold',
                runtimeMeta.textClassName,
                runtimeMeta.backgroundClassName,
              ].join(' ')}
            >
              <span className={['h-1.5 w-1.5 shrink-0 rounded-full', runtimeMeta.dotClassName].join(' ')} />
              <span className="truncate">{runtimeLabel}</span>
            </span>
          </div>
        </div>
        <div className="flex min-w-0 flex-col gap-1.5 border-l border-[#eff0f2] pl-2.5">
          <span className="text-[10px] leading-4 text-[#a0a4ad]">
            {intl.formatMessage({ id: 'pages.workflow.definition.card.updatedAt' })}
          </span>
          <strong className="flex min-w-0 items-center gap-1.5 truncate text-[11px] font-medium leading-[18px] text-[#737882]">
            <Clock3 size={11} strokeWidth={1.8} className="shrink-0 text-[#9ca0a9]" />
            <span className="truncate">{formatWorkflowTime(record.updateTime, intl.locale)}</span>
          </strong>
        </div>
      </div>
    </div>
  );
};

export default WorkflowDefinitionCard;
