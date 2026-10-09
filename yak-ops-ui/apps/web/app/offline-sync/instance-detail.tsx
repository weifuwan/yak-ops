import { DataSyncLegacyInstanceDetailRedirect } from "@/app/data-sync/task-detail";

export function OfflineSyncInstanceDetailPage() {
  return <DataSyncLegacyInstanceDetailRedirect syncType="OFFLINE" basePath="/offline-sync" />;
}

export default OfflineSyncInstanceDetailPage;
