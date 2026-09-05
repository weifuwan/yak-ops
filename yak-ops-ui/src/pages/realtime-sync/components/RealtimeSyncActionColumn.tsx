import { YakButton } from '@/components/ui';
import type {
  ComputeEnvironmentOption,
  RealtimeAction,
  RealtimeJob,
} from '@/services/realtime-sync';
import { MoreOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Dropdown, Modal, Tooltip, message } from 'antd';
import type { MenuProps } from 'antd';

import {
  getRealtimeStartAvailability,
  isRealtimeReconciliationState,
  isRealtimeStableRunning,
  type RealtimeStartUnavailableReason,
} from '../utils';

interface RealtimeSyncActionColumnProps {
  job: RealtimeJob;
  environment?: ComputeEnvironmentOption;
  onEdit: (job: RealtimeJob) => void;
  onDetail: (job: RealtimeJob) => void;
  onDelete: (job: RealtimeJob) => Promise<void>;
  onAction: (job: RealtimeJob, action: RealtimeAction) => Promise<void>;
}

const RealtimeSyncActionColumn = ({
  job,
  environment,
  onEdit,
  onDetail,
  onDelete,
  onAction,
}: RealtimeSyncActionColumnProps) => {
  const intl = useIntl();
  const running = job.desiredState === 'RUNNING';
  const stableRunning = isRealtimeStableRunning(job);
  const startAvailability = getRealtimeStartAvailability(job, environment);

  const startTooltip = (() => {
    const keyMap: Record<RealtimeStartUnavailableReason, string> = {
      ENVIRONMENT_DISABLED: 'pages.realtimeSync.start.environmentDisabled',
      NO_PUBLISHED_VERSION: 'pages.realtimeSync.start.noPublishedVersion',
      ALREADY_RUNNING: 'pages.realtimeSync.start.alreadyRunning',
      DRAFT_NOT_PUBLISHED: 'pages.realtimeSync.start.draftNotPublished',
    };
    if (!startAvailability.reason) return undefined;
    return intl.formatMessage(
      { id: keyMap[startAvailability.reason] },
      {
        name: startAvailability.environmentName || '-',
        version: startAvailability.publishedVersion || '-',
      },
    );
  })();

  const confirmDelete = () => {
    Modal.confirm({
      title: intl.formatMessage({ id: 'pages.realtimeSync.action.deleteTitle' }),
      content: intl.formatMessage(
        { id: 'pages.realtimeSync.action.deleteConfirm' },
        { name: job.name },
      ),
      okText: intl.formatMessage({ id: 'pages.realtimeSync.action.delete' }),
      okButtonProps: { danger: true },
      cancelText: intl.formatMessage({ id: 'pages.realtimeSync.action.cancel' }),
      onOk: async () => {
        try {
          await onDelete(job);
        } catch (error) {
          message.error(
            error instanceof Error
              ? error.message
              : intl.formatMessage({ id: 'pages.realtimeSync.action.deleteFailed' }),
          );
          throw error;
        }
      },
    });
  };

  const buildMoreItems = (): NonNullable<MenuProps['items']> => {
    const items: NonNullable<MenuProps['items']> = [
      {
        key: 'detail',
        label: intl.formatMessage({ id: 'pages.realtimeSync.action.detail' }),
      },
      {
        key: 'validate',
        label: intl.formatMessage({ id: 'pages.realtimeSync.action.validate' }),
        disabled: job.releaseState === 'PUBLISHED',
      },
      {
        key: 'publish',
        label: intl.formatMessage({
          id: running
            ? 'pages.realtimeSync.action.publishRunning'
            : 'pages.realtimeSync.action.publish',
        }),
        disabled: job.releaseState === 'PUBLISHED',
      },
      {
        key: 'restart-execution',
        label: intl.formatMessage({ id: 'pages.realtimeSync.action.restart' }),
        disabled: !stableRunning,
      },
    ];

    if (job.publishedUpdateAvailable) {
      items.push({
        key: 'apply-published-version',
        label: intl.formatMessage({ id: 'pages.realtimeSync.action.applyPublished' }),
        disabled: !stableRunning,
      });
    }

    items.push(
      {
        key: 'reconcile',
        label: intl.formatMessage({ id: 'pages.realtimeSync.action.reconcile' }),
        disabled: !isRealtimeReconciliationState(job.observedState),
      },
      { type: 'divider' },
      {
        key: 'delete',
        label: (
          <span className="text-[#d92d20]">
            {intl.formatMessage({ id: 'pages.realtimeSync.action.deleteTask' })}
          </span>
        ),
        disabled: job.desiredState !== 'STOPPED',
      },
    );
    return items;
  };

  const handleMoreAction: MenuProps['onClick'] = ({ key, domEvent }) => {
    domEvent.stopPropagation();
    if (key === 'detail') {
      onDetail(job);
      return;
    }
    if (key === 'delete') {
      confirmDelete();
      return;
    }
    void onAction(job, key as RealtimeAction);
  };

  return (
    <div className="flex min-h-7 items-center gap-0.5 whitespace-nowrap">
      <Tooltip
        title={
          running
            ? intl.formatMessage({
                id: 'pages.realtimeSync.action.editRunningTooltip',
              })
            : undefined
        }
      >
        <YakButton
          type="text"
          size="small"
          className="!h-7 !px-1.5 !text-[12px]"
          onClick={() => onEdit(job)}
        >
          {intl.formatMessage({ id: 'pages.realtimeSync.action.edit' })}
        </YakButton>
      </Tooltip>

      {running ? (
        <YakButton
          type="text"
          danger
          size="small"
          className="!h-7 !px-1.5 !text-[12px]"
          onClick={() => void onAction(job, 'stop')}
        >
          {intl.formatMessage({ id: 'pages.realtimeSync.action.stop' })}
        </YakButton>
      ) : (
        <Tooltip title={startTooltip}>
          <span>
            <YakButton
              type="text"
              danger
              size="small"
              className="!h-7 !px-1.5 !text-[12px]"
              disabled={startAvailability.disabled}
              onClick={() => void onAction(job, 'start')}
            >
              {intl.formatMessage({ id: 'pages.realtimeSync.action.start' })}
            </YakButton>
          </span>
        </Tooltip>
      )}

      <Dropdown
        trigger={['click']}
        menu={{ items: buildMoreItems(), onClick: handleMoreAction }}
      >
        <YakButton
          type="text"
          size="small"
          iconOnly
          aria-label={intl.formatMessage({ id: 'pages.realtimeSync.action.moreAria' })}
          icon={<MoreOutlined />}
          className="!h-7 !w-7 !min-w-0 !p-0 !text-[#667085] hover:!bg-[#f2f4f7]"
        />
      </Dropdown>
    </div>
  );
};

export default RealtimeSyncActionColumn;
