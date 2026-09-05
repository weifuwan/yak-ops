import { YakButton } from '@/components/ui';
import {
  deleteOfflineSyncTask,
  executeOfflineSyncTask,
  offlineOfflineSyncTask,
  onlineOfflineSyncTask,
  stopOfflineSyncExecution,
  type BatchLinkUpId,
  type OfflineJobDefinitionVO,
} from '@/services/batch-link-up';
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  EyeOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import {
  Dropdown,
  Modal,
  Popconfirm,
  Tooltip,
  message,
  type MenuProps,
} from 'antd';
import { useState, type MouseEvent as ReactMouseEvent } from 'react';

interface ActionColumnProps {
  record: OfflineJobDefinitionVO;
  cbk: () => void | Promise<void>;
  goDetail: (value: BatchLinkUpId, item: OfflineJobDefinitionVO) => void;
}

const { confirm } = Modal;
const ACTIVE_STATUSES = new Set(['CREATED', 'SUBMITTED', 'QUEUED', 'RUNNING']);

const normalizeStatus = (value?: string) =>
  String(value || '').trim().toUpperCase();

const isReleaseOnline = (releaseState?: string | number) =>
  releaseState === 'ONLINE' || releaseState === 1;

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

const ActionColumn = ({ record, cbk, goDetail }: ActionColumnProps) => {
  const intl = useIntl();
  const [runOpen, setRunOpen] = useState(false);
  const [runLoading, setRunLoading] = useState(false);

  const isOnline = isReleaseOnline(record.releaseState);
  const isActive = ACTIVE_STATUSES.has(normalizeStatus(record.lastJobStatus));
  const canRun = isOnline && !isActive;
  const canEdit = !isOnline && !isActive;
  const canDelete = !isOnline && !isActive;
  const t = (id: string, values?: Record<string, string | number>) =>
    intl.formatMessage({ id }, values);

  const stopPropagation = (event: ReactMouseEvent<HTMLElement>) => {
    event.stopPropagation();
  };

  const openExecutionDetail = () => {
    if (record.id === undefined || record.id === null) {
      message.error(t('pages.batchLinkUp.action.definitionIdMissing'));
      return;
    }

    const params = new URLSearchParams();
    if (record.instanceId !== undefined && record.instanceId !== null) {
      params.set('instanceId', String(record.instanceId));
    }

    const search = params.toString();
    history.push(
      `/sync/batch-link-up/${encodeURIComponent(String(record.id))}/detail${
        search ? `?${search}` : ''
      }`,
    );
  };

  const handleRun = async () => {
    if (!canRun) {
      message.warning(
        t(
          isOnline
            ? 'pages.batchLinkUp.action.running'
            : 'pages.batchLinkUp.action.onlineBeforeRun',
        ),
      );
      return;
    }
    if (record.id === undefined || record.id === null) {
      message.error(t('pages.batchLinkUp.action.definitionIdMissing'));
      return;
    }

    try {
      setRunLoading(true);
      await executeOfflineSyncTask(record.id);
      message.success(t('pages.batchLinkUp.action.runSubmitted'));
      setRunOpen(false);
      void cbk();
    } catch (error) {
      message.error(
        errorMessage(error, t('pages.batchLinkUp.action.runFailed')),
      );
    } finally {
      setRunLoading(false);
    }
  };

  const handleStop = async () => {
    if (record.instanceId === undefined || record.instanceId === null) {
      message.error(t('pages.batchLinkUp.action.instanceIdMissing'));
      return;
    }

    try {
      await stopOfflineSyncExecution(record.instanceId);
      message.success(t('pages.batchLinkUp.action.stopSubmitted'));
      void cbk();
    } catch (error) {
      message.error(
        errorMessage(error, t('pages.batchLinkUp.action.stopFailed')),
      );
    }
  };

  const handleOnline = async () => {
    if (record.id === undefined || record.id === null) {
      message.error(t('pages.batchLinkUp.action.definitionIdMissing'));
      return;
    }

    try {
      await onlineOfflineSyncTask(record.id);
      message.success(t('pages.batchLinkUp.action.onlineSuccess'));
      void cbk();
    } catch (error) {
      message.error(
        errorMessage(error, t('pages.batchLinkUp.action.onlineFailed')),
      );
    }
  };

  const handleOffline = async () => {
    if (isActive) {
      message.warning(t('pages.batchLinkUp.action.stopBeforeOffline'));
      return;
    }
    if (record.id === undefined || record.id === null) {
      message.error(t('pages.batchLinkUp.action.definitionIdMissing'));
      return;
    }

    try {
      await offlineOfflineSyncTask(record.id);
      message.success(t('pages.batchLinkUp.action.offlineSuccess'));
      void cbk();
    } catch (error) {
      message.error(
        errorMessage(error, t('pages.batchLinkUp.action.offlineFailed')),
      );
    }
  };

  const showOnlineConfirm = () => {
    confirm({
      title: t('pages.batchLinkUp.action.onlineTitle'),
      centered: true,
      content: t('pages.batchLinkUp.action.onlineConfirm'),
      okText: t('pages.batchLinkUp.action.confirm'),
      cancelText: t('pages.batchLinkUp.action.cancel'),
      onOk: handleOnline,
    });
  };

  const showOfflineConfirm = () => {
    if (isActive) {
      message.warning(t('pages.batchLinkUp.action.stopBeforeOffline'));
      return;
    }
    confirm({
      title: t('pages.batchLinkUp.action.offlineTitle'),
      centered: true,
      content: t('pages.batchLinkUp.action.offlineConfirm'),
      okText: t('pages.batchLinkUp.action.confirm'),
      cancelText: t('pages.batchLinkUp.action.cancel'),
      onOk: handleOffline,
    });
  };

  const handleEdit = () => {
    if (!canEdit) {
      message.warning(
        t(
          isOnline
            ? 'pages.batchLinkUp.action.offlineBeforeEdit'
            : 'pages.batchLinkUp.action.running',
        ),
      );
      return;
    }
    if (record.id === undefined || record.id === null) {
      message.error(t('pages.batchLinkUp.action.definitionIdMissing'));
      return;
    }
    goDetail(record.id, record);
  };

  const handleDeleteTask = () => {
    if (!canDelete) {
      message.warning(
        t(
          isOnline
            ? 'pages.batchLinkUp.action.offlineBeforeDelete'
            : 'pages.batchLinkUp.action.running',
        ),
      );
      return;
    }
    if (record.id === undefined || record.id === null) {
      message.error(t('pages.batchLinkUp.action.definitionIdMissing'));
      return;
    }

    confirm({
      title: t('pages.batchLinkUp.action.deleteTitle'),
      centered: true,
      content: t('pages.batchLinkUp.action.deleteConfirm', {
        name: record.jobName || '-',
      }),
      okText: t('pages.batchLinkUp.action.delete'),
      cancelText: t('pages.batchLinkUp.action.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteOfflineSyncTask(record.id as BatchLinkUpId);
          message.success(t('pages.batchLinkUp.action.deleteSuccess'));
          void cbk();
        } catch (error) {
          message.error(
            errorMessage(error, t('pages.batchLinkUp.action.deleteFailed')),
          );
        }
      },
    });
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'view',
      icon: <EyeOutlined />,
      label: t('pages.batchLinkUp.action.viewDetail'),
    },
    {
      key: 'edit',
      icon: <EditOutlined />,
      label: t('pages.batchLinkUp.action.editConfig'),
      disabled: !canEdit,
    },
    { type: 'divider' },
    {
      key: isOnline ? 'offline' : 'online',
      icon: isOnline ? <CloudDownloadOutlined /> : <CloudUploadOutlined />,
      label: t(
        isOnline
          ? 'pages.batchLinkUp.action.offlineTask'
          : 'pages.batchLinkUp.action.onlineTask',
      ),
      disabled: isActive,
    },
    { type: 'divider' },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      label: t('pages.batchLinkUp.action.deleteTitle'),
      danger: true,
      disabled: !canDelete,
    },
  ];

  const handleMenuClick: MenuProps['onClick'] = ({ key, domEvent }) => {
    domEvent.stopPropagation();
    if (key === 'view') openExecutionDetail();
    if (key === 'edit') handleEdit();
    if (key === 'online') showOnlineConfirm();
    if (key === 'offline') showOfflineConfirm();
    if (key === 'delete') handleDeleteTask();
  };

  return (
    <div className="flex items-center gap-1 whitespace-nowrap">
      {isActive ? (
        <Popconfirm
          title={t('pages.batchLinkUp.action.stopTitle')}
          description={t('pages.batchLinkUp.action.stopConfirm')}
          okText={t('pages.batchLinkUp.action.confirm')}
          cancelText={t('pages.batchLinkUp.action.cancel')}
          onConfirm={handleStop}
        >
          <YakButton
            size="small"
            type="text"
            danger
            icon={<PauseCircleOutlined />}
            className="!h-7 !rounded-md !px-2.5 !text-xs !text-[#667085]"
            onClick={stopPropagation}
          >
            {t('pages.batchLinkUp.action.stop')}
          </YakButton>
        </Popconfirm>
      ) : (
        <Tooltip
          title={canRun ? undefined : t('pages.batchLinkUp.action.onlineFirst')}
        >
          <Popconfirm
            title={t('pages.batchLinkUp.action.runTitle')}
            description={t('pages.batchLinkUp.action.runConfirm')}
            open={canRun && runOpen}
            okText={t('pages.batchLinkUp.action.confirm')}
            cancelText={t('pages.batchLinkUp.action.cancel')}
            okButtonProps={{ loading: runLoading }}
            onConfirm={handleRun}
            onOpenChange={(open) => {
              if (!canRun) {
                if (open) {
                  message.warning(t('pages.batchLinkUp.action.onlineBeforeRun'));
                }
                return;
              }
              if (!runLoading) setRunOpen(open);
            }}
          >
            <YakButton
              size="small"
              type="text"
              loading={runLoading}
              aria-disabled={!canRun}
              icon={<PlayCircleOutlined />}
              className={[
                '!h-7 !rounded-md !px-2.5 !text-xs !text-[#667085]',
                !canRun ? '!cursor-not-allowed !text-[#98a2b3]' : '',
              ].join(' ')}
              onClick={stopPropagation}
            >
              {t('pages.batchLinkUp.action.run')}
            </YakButton>
          </Popconfirm>
        </Tooltip>
      )}

      <Dropdown
        trigger={['click']}
        placement="bottomRight"
        menu={{ items: menuItems, onClick: handleMenuClick }}
      >
        <YakButton
          size="small"
          type="text"
          className="!h-7 !rounded-md !px-2 !text-xs !text-[#667085]"
          onClick={stopPropagation}
        >
          {t('pages.batchLinkUp.action.more')}
          <DownOutlined className="text-[9px]" />
        </YakButton>
      </Dropdown>
    </div>
  );
};

export default ActionColumn;
