import { DataSyncLegacyInstanceDetailRedirect } from "@/app/data-sync/task-detail";

import { DataSyncOperationsDashboard } from "./dashboard";

export function OfflineTaskOperationsPage() {
  return <DataSyncOperationsDashboard syncType="OFFLINE" />;
}

export function RealtimeTaskOperationsPage() {
  return <DataSyncOperationsDashboard syncType="REALTIME" />;
}

/** Existing Operations links now open the owning Task in Data Integration. */
export function OfflineTaskOperationsInstanceDetailPage() {
  return <DataSyncLegacyInstanceDetailRedirect syncType="OFFLINE" basePath="/offline-sync" />;
}

/** Existing Operations links now open the owning Task in Data Integration. */
export function RealtimeTaskOperationsInstanceDetailPage() {
  return <DataSyncLegacyInstanceDetailRedirect syncType="REALTIME" basePath="/realtime-sync" />;
}
