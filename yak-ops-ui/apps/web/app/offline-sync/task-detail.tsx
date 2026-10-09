import { DataSyncTaskDetailPage } from "@/app/data-sync/task-detail";

export function OfflineSyncTaskDetailPage() {
  return (
    <DataSyncTaskDetailPage
      syncType="OFFLINE"
      basePath="/offline-sync"
      title="离线同步任务详情"
      localScroll
    />
  );
}

export default OfflineSyncTaskDetailPage;
