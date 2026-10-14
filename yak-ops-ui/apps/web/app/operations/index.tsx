import { DataSyncInstanceRedirect } from "@/app/data-sync/redirects";

import { DataSyncOperationsDashboard } from "./dashboard";

export function OfflineTaskOperationsPage() {
  return <DataSyncOperationsDashboard syncType="OFFLINE" />;
}

export function RealtimeTaskOperationsPage() {
  return <DataSyncOperationsDashboard syncType="REALTIME" />;
}

/** Existing Operations links now open the owning Task in Data Integration. */
export function OfflineTaskOperationsInstanceDetailPage() {
  return <DataSyncInstanceRedirect />;
}

/** Existing Operations links now open the owning Task in Data Integration. */
export function RealtimeTaskOperationsInstanceDetailPage() {
  return <DataSyncInstanceRedirect />;
}
