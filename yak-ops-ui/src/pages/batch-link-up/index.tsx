import type {
  BatchLinkUpId,
  OfflineJobDefinitionVO,
} from '@/services/batch-link-up';
import { history, useIntl } from '@umijs/max';
import { ConfigProvider, Divider, message } from 'antd';
import { useMemo } from 'react';

import CreateSyncTaskDrawer from './components/CreateSyncTaskDrawer';
import OfflineSyncFilterBar from './components/OfflineSyncFilterBar';
import OfflineSyncPageHeader from './components/OfflineSyncPageHeader';
import OfflineSyncTaskTable from './components/OfflineSyncTaskTable';
import { OFFLINE_SYNC_PAGE_THEME } from './constants';
import { generateDataSourceOptions } from './DataSourceSelect';
import { useOfflineSyncTasks } from './hooks/useOfflineSyncTasks';
import type { OfflineSyncConnectorOption } from './types';
import { getOfflineSyncEditPath } from './utils';

const OfflineSyncPage = () => {
  const intl = useIntl();
  const connectorOptions = useMemo(
    () => generateDataSourceOptions() as OfflineSyncConnectorOption[],
    [],
  );
  const {
    records,
    filterDraft,
    pagination,
    loading,
    createOpen,
    selectedRowKeys,
    currentStatus,
    advancedFilterCount,
    setCreateOpen,
    setSelectedRowKeys,
    updateFilterDraft,
    searchTasks,
    resetFilters,
    changeStatus,
    changeQuickFilter,
    resetAdvancedFilters,
    changePagination,
    handleCreated,
    copyTaskId,
    refresh,
  } = useOfflineSyncTasks();

  const handleEdit = (
    id: BatchLinkUpId,
    record: OfflineJobDefinitionVO,
  ) => {
    const editableRecord =
      record.id === undefined || record.id === null
        ? { ...record, id }
        : record;
    const path = getOfflineSyncEditPath(editableRecord);
    if (!path) {
      message.warning(
        intl.formatMessage({ id: 'pages.batchLinkUp.page.unsupportedEdit' }),
      );
      return;
    }
    history.push(path);
  };

  return (
    <ConfigProvider theme={OFFLINE_SYNC_PAGE_THEME}>
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <div className="mx-auto flex w-full max-w-full flex-1 flex-col">
          <OfflineSyncPageHeader onCreate={() => setCreateOpen(true)} />

          <div className="mt-3">
            <OfflineSyncFilterBar
              filterDraft={filterDraft}
              currentStatus={currentStatus}
              connectorOptions={connectorOptions}
              advancedFilterCount={advancedFilterCount}
              onDraftChange={updateFilterDraft}
              onQuickFilterChange={changeQuickFilter}
              onStatusChange={changeStatus}
              onSearch={searchTasks}
              onReset={resetFilters}
              onAdvancedReset={resetAdvancedFilters}
            />
          </div>

          <Divider style={{ padding: 0, margin: '8px 0' }} />

          <div className="mt-4 flex min-h-0 flex-1 flex-col">
            <OfflineSyncTaskTable
              records={records}
              loading={loading}
              selectedRowKeys={selectedRowKeys}
              pagination={pagination}
              onSelectionChange={setSelectedRowKeys}
              onPaginationChange={changePagination}
              onCopyTaskId={(id) => void copyTaskId(id)}
              onEdit={handleEdit}
              onRefresh={refresh}
            />
          </div>
        </div>

        <CreateSyncTaskDrawer
          open={createOpen}
          onCancel={() => setCreateOpen(false)}
          onCreated={handleCreated}
        />
      </div>
    </ConfigProvider>
  );
};

export default OfflineSyncPage;
