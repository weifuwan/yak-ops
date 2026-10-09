import { DataSyncTaskDetailPage } from "@/app/data-sync/task-detail";

export function RealtimeSyncTaskDetailPage() {
  return (
    <DataSyncTaskDetailPage
      syncType="REALTIME"
      basePath="/realtime-sync"
      title="实时同步任务详情"
      localScroll
    />
  );
}

export default RealtimeSyncTaskDetailPage;
