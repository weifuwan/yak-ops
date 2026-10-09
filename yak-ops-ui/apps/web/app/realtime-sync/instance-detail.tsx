import { DataSyncLegacyInstanceDetailRedirect } from "@/app/data-sync/task-detail";

export function RealtimeSyncInstanceDetailPage() {
  return <DataSyncLegacyInstanceDetailRedirect syncType="REALTIME" basePath="/realtime-sync" />;
}

export default RealtimeSyncInstanceDetailPage;
