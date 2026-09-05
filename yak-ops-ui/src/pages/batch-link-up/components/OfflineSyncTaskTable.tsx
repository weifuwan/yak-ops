import { YakButton } from '@/components/ui';
import type {
  BatchLinkUpId,
  OfflineJobDefinitionVO,
} from '@/services/batch-link-up';
import { CopyOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Empty, Table, Tooltip } from 'antd';
import type { TableColumnsType } from 'antd';
import type { TableRowSelection } from 'antd/es/table/interface';
import { useMemo } from 'react';

import { OFFLINE_SYNC_COMPACT_CONTENT_CLASS } from '../constants';
import type {
  OfflineSyncPaginationState,
  OfflineSyncSelectedRowKeys,
} from '../types';
import OfflineSyncPagination from './OfflineSyncPagination';
import ActionColumn from './SyncTaskList/components/ActionColumn';
import DataSourceSyncPlan from './SyncTaskList/components/DataSourceSyncPlan';
import ExecutionStatus from './SyncTaskList/components/ExecutionStatus';
import ScheduleInfo from './SyncTaskList/components/ScheduleInfo';
import TaskStatus from './SyncTaskList/components/TaskStatus';

interface OfflineSyncTaskTableProps {
  records: OfflineJobDefinitionVO[];
  loading: boolean;
  selectedRowKeys: OfflineSyncSelectedRowKeys;
  pagination: OfflineSyncPaginationState;
  onSelectionChange: (keys: OfflineSyncSelectedRowKeys) => void;
  onPaginationChange: (page: number, pageSize: number) => void;
  onCopyTaskId: (id: BatchLinkUpId) => void;
  onEdit: (id: BatchLinkUpId, record: OfflineJobDefinitionVO) => void;
  onRefresh: () => void;
}

const OfflineSyncTaskTable = ({
  records,
  loading,
  selectedRowKeys,
  pagination,
  onSelectionChange,
  onPaginationChange,
  onCopyTaskId,
  onEdit,
  onRefresh,
}: OfflineSyncTaskTableProps) => {
  const intl = useIntl();
  const columns = useMemo<TableColumnsType<OfflineJobDefinitionVO>>(
    () => [
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.nameId' }),
        dataIndex: 'jobName',
        width: 250,
        render: (_value, record) => (
          <div className="min-w-0 py-0.5">
            <div
              className="truncate text-[13px] font-medium leading-5 text-[#344054]"
              title={record.jobName}
            >
              {record.jobName || '-'}
            </div>

            <div className="mt-0.5 flex h-5 items-center gap-1 text-[11px] leading-5 text-[#98a2b3]">
              <span className="truncate">ID：{record.id ?? '-'}</span>
              {record.id !== undefined && record.id !== null ? (
                <Tooltip
                  title={intl.formatMessage({
                    id: 'pages.batchLinkUp.table.copyTaskId',
                  })}
                >
                  <YakButton
                    type="text"
                    size="small"
                    iconOnly
                    icon={<CopyOutlined className="text-[11px]" />}
                    className="!h-5 !w-5 !min-w-0 !p-0 !text-[#98a2b3] hover:!bg-[#f2f4f7] hover:!text-[#475467]"
                    onClick={(event) => {
                      event.stopPropagation();
                      onCopyTaskId(record.id as BatchLinkUpId);
                    }}
                  />
                </Tooltip>
              ) : null}
            </div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.syncPlan' }),
        dataIndex: 'syncPlan',
        width: 290,
        render: (_value, record) => (
          <div className={OFFLINE_SYNC_COMPACT_CONTENT_CLASS}>
            <DataSourceSyncPlan record={record} />
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.status' }),
        dataIndex: 'lastJobStatus',
        width: 100,
        align: 'center',
        render: (_value, record) => (
          <div className="flex min-h-6 items-center justify-center">
            <TaskStatus
              status={record.lastJobStatus}
              errorMessage={record.lastErrorMessage}
            />
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.execution' }),
        dataIndex: 'execution',
        width: 300,
        render: (_value, record) => (
          <div className={OFFLINE_SYNC_COMPACT_CONTENT_CLASS}>
            <ExecutionStatus record={record} />
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.schedule' }),
        dataIndex: 'schedule',
        width: 225,
        render: (_value, record) => (
          <div className={OFFLINE_SYNC_COMPACT_CONTENT_CLASS}>
            <ScheduleInfo record={record} />
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.createTime' }),
        dataIndex: 'createTime',
        width: 165,
        render: (value?: string) => (
          <span className="whitespace-nowrap text-[12px] leading-5 text-[#98a2b3]">
            {value || '-'}
          </span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.batchLinkUp.table.actions' }),
        dataIndex: 'operate',
        width: 190,
        fixed: 'right',
        render: (_value, record) => (
          <div className="flex min-h-7 items-center">
            <ActionColumn
              record={record}
              cbk={onRefresh}
              goDetail={onEdit}
            />
          </div>
        ),
      },
    ],
    [intl, onCopyTaskId, onEdit, onRefresh],
  );

  const rowSelection: TableRowSelection<OfflineJobDefinitionVO> = {
    type: 'checkbox',
    columnWidth: 48,
    selectedRowKeys,
    onChange: (keys) => onSelectionChange(keys),
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1">
        <Table<OfflineJobDefinitionVO>
          columns={columns}
          dataSource={records}
          rowKey="id"
          bordered
          size="small"
          pagination={false}
          loading={loading}
          rowSelection={rowSelection}
          scroll={{ x: 'max-content' }}
          className={[
            'compact-sync-task-table',
            '[&_.ant-table]:!text-[13px]',
            '[&_.ant-table-container]:!border-[#eaecf0]',
            '[&_.ant-table-cell]:!align-middle',
            '[&_.ant-table-thead>tr>th]:!h-10',
            '[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb]',
            '[&_.ant-table-thead>tr>th]:!px-4',
            '[&_.ant-table-thead>tr>th]:!py-2',
            '[&_.ant-table-thead>tr>th]:!text-[12px]',
            '[&_.ant-table-thead>tr>th]:!font-medium',
            '[&_.ant-table-thead>tr>th]:!text-[#667085]',
            '[&_.ant-table-thead>tr>th]:!border-[#eaecf0]',
            '[&_.ant-table-tbody>tr>td]:!px-4',
            '[&_.ant-table-tbody>tr>td]:!py-2.5',
            '[&_.ant-table-tbody>tr>td]:!border-[#f0f2f5]',
            '[&_.ant-table-tbody>tr>td]:!text-[#667085]',
            '[&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc]',
            '[&_.ant-table-cell-fix-right]:!bg-white',
            '[&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc]',
            '[&_.ant-checkbox-inner]:!h-4',
            '[&_.ant-checkbox-inner]:!w-4',
            '[&_.ant-table-placeholder>td]:!h-[240px]',
          ].join(' ')}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <span className="text-[12px] text-[#98a2b3]">
                    {intl.formatMessage({ id: 'pages.batchLinkUp.table.empty' })}
                  </span>
                }
              />
            ),
          }}
        />
      </div>

      <div className="sticky bottom-0 z-20 mt-auto flex min-h-[56px] items-center justify-end border border-t-0 border-[#e5e7eb] bg-white px-5 py-3 shadow-[0_-4px_12px_rgba(16,24,40,0.04)]">
        <OfflineSyncPagination
          total={pagination.total}
          current={pagination.current}
          pageSize={pagination.pageSize}
          onChange={onPaginationChange}
        />
      </div>
    </div>
  );
};

export default OfflineSyncTaskTable;
