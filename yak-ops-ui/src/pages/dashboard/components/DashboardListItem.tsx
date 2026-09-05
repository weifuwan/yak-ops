import { YakButton } from '@/components/ui';
import type { DashboardSummary } from '@/services/dashboard';
import { useIntl } from '@umijs/max';
import { Popconfirm } from 'antd';
import { Pencil, Trash2 } from 'lucide-react';

import {
  dashboardLifecycleClassName,
  dashboardLifecycleMessage,
  formatDashboardDate,
  formatDashboardTime,
  getDashboardLifecycle,
} from '../utils';

interface DashboardListItemProps {
  dashboard: DashboardSummary;
  deleting: boolean;
  onOpen: (dashboard: DashboardSummary) => void;
  onEdit: (dashboard: DashboardSummary) => void;
  onRename: (dashboard: DashboardSummary) => void;
  onDelete: (dashboard: DashboardSummary) => Promise<void>;
}

const DashboardPreview = ({ dashboard }: { dashboard: DashboardSummary }) => (
  <div className="relative h-[160px] w-[120px] shrink-0 overflow-hidden rounded-[6px] border border-[#e6e8eb] bg-gradient-to-b from-[#fafafa] to-[#eceef1] text-left transition-[border-color,box-shadow] duration-150 ease-out group-hover:border-[#d9dce1] group-hover:shadow-[0_2px_8px_rgba(22,24,35,0.05)]">
    <div className="absolute left-3 right-3 top-4 flex h-[62px] items-end gap-1.5">
      <span className="h-8 flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
      <span className="h-[46px] flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
      <span className="h-7 flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
      <span className="h-[54px] flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
    </div>

    <div className="absolute bottom-4 left-3 right-3 rounded-[5px] bg-white px-2 py-[10px] shadow-[0_1px_4px_rgba(0,0,0,0.04)]">
      <div className="h-[4px] w-[72%] rounded-full bg-[#d5d9de]" />
      <div className="mt-2 h-[4px] w-[48%] rounded-full bg-[#e1e4e8]" />
    </div>

    <div className="absolute right-2 top-2 flex h-[18px] min-w-[24px] items-center justify-center rounded-[3px] bg-[rgba(22,24,35,0.62)] px-1.5 text-[10px] font-medium text-white">
      V{dashboard.currentVersionNo || 0}
    </div>
  </div>
);

const DashboardListItem = ({
  dashboard,
  deleting,
  onOpen,
  onEdit,
  onRename,
  onDelete,
}: DashboardListItemProps) => {
  const intl = useIntl();
  const lifecycle = getDashboardLifecycle(dashboard);
  const lifecycleMessage = dashboardLifecycleMessage(dashboard);

  return (
    <article className="group relative -mx-3 flex min-h-[200px] gap-4 rounded-[8px] border-b border-[#f0f1f2] px-3 py-5 transition-[background-color,box-shadow] duration-150 ease-out hover:z-[1] hover:bg-[#f8f9fa] hover:shadow-[inset_0_0_0_1px_rgba(22,24,35,0.035)] last:border-b-0">
      <button
        type="button"
        onClick={() => onOpen(dashboard)}
        className="shrink-0 border-0 bg-transparent p-0"
        aria-label={intl.formatMessage(
          { id: 'pages.dashboard.list.openAria' },
          { name: dashboard.name },
        )}
      >
        <DashboardPreview dashboard={dashboard} />
      </button>

      <div className="flex min-h-[160px] min-w-0 flex-1 flex-col pr-[300px] max-xl:pr-0">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onOpen(dashboard)}
            className="max-w-[620px] truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold leading-5 text-[#161823] transition-colors hover:text-[#111318] hover:underline"
          >
            {dashboard.name}
          </button>

          <span
            className={`shrink-0 text-[12px] leading-5 ${dashboardLifecycleClassName(dashboard)}`}
          >
            {intl.formatMessage(
              { id: lifecycleMessage.id },
              lifecycleMessage.values,
            )}
          </span>
        </div>

        <div className="mt-1 flex min-w-0 items-center gap-2 text-[12px] leading-5 text-[#8a9099]">
          <span>{formatDashboardTime(dashboard.updateTime)}</span>
          <span className="text-[#d7dade]">|</span>
          <span className="max-w-[560px] truncate">
            {dashboard.description || intl.formatMessage({ id: 'pages.dashboard.common.noDescription' })}
          </span>
        </div>

        <div className="mt-auto grid min-h-[44px] grid-cols-[116px_122px_122px_minmax(0,1fr)] gap-10 max-xl:grid-cols-2 max-xl:gap-4">
          <div>
            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
              {intl.formatMessage({ id: 'pages.dashboard.list.draftVersion' })}
            </div>
            <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
              {dashboard.currentVersionNo ? `V${dashboard.currentVersionNo}` : '-'}
            </div>
          </div>

          <div>
            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
              {intl.formatMessage({ id: 'pages.dashboard.list.publishedVersion' })}
            </div>
            <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
              {lifecycle.published ? `V${dashboard.publishedVersionNo}` : '-'}
            </div>
          </div>

          <div>
            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
              {intl.formatMessage({ id: 'pages.dashboard.list.createdAt' })}
            </div>
            <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
              {formatDashboardDate(dashboard.createTime)}
            </div>
          </div>

          <div className="min-w-0">
            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
              {intl.formatMessage({ id: 'pages.dashboard.list.dashboardId' })}
            </div>
            <div
              className="mt-[2px] max-w-[220px] truncate text-[14px] font-semibold leading-5 text-[#161823]"
              title={dashboard.id}
            >
              {dashboard.id}
            </div>
          </div>
        </div>
      </div>

      <div className="absolute right-3 top-5 flex items-center gap-0 text-[12px] opacity-80 transition-opacity duration-150 group-hover:opacity-100 max-xl:static max-xl:ml-auto max-xl:self-start">
        <YakButton
          type="text"
          size="small"
          icon={<Pencil size={12} />}
          className="!h-7 !px-1.5 !text-[#667085]"
          onClick={() => onEdit(dashboard)}
        >
          {intl.formatMessage({ id: 'pages.dashboard.list.edit' })}
        </YakButton>

        <YakButton
          type="text"
          size="small"
          className="!h-7 !px-1.5 !text-[#667085]"
          onClick={() => onRename(dashboard)}
        >
          {intl.formatMessage({ id: 'pages.dashboard.common.rename' })}
        </YakButton>

        <Popconfirm
          title={intl.formatMessage({ id: 'pages.dashboard.list.deleteTitle' })}
          description={intl.formatMessage(
            { id: 'pages.dashboard.list.deleteDescription' },
            { name: dashboard.name },
          )}
          okText={intl.formatMessage({ id: 'pages.dashboard.common.delete' })}
          cancelText={intl.formatMessage({ id: 'pages.dashboard.common.cancel' })}
          okButtonProps={{ danger: true, loading: deleting }}
          onConfirm={() => onDelete(dashboard)}
        >
          <YakButton
            type="text"
            size="small"
            danger
            loading={deleting}
            icon={<Trash2 size={12} />}
            className="!h-7 !px-1.5"
          >
            {intl.formatMessage({ id: 'pages.dashboard.common.delete' })}
          </YakButton>
        </Popconfirm>
      </div>
    </article>
  );
};

export default DashboardListItem;
