import { PageHeader } from "@yak-ops/yak-ui";

import { DataSyncInstanceDetailPage, DataSyncInstances } from "@/app/data-sync/instance-runtime";

export function OfflineTaskOperationsPage() {
  return (
    <div className="min-h-full bg-[#f6f6f6]">
      <PageHeader title="离线同步运维" bordered className="bg-white px-6 max-md:px-4" />
      <div className="px-6 pb-8 pt-5 max-md:px-4">
        <DataSyncInstances syncType="OFFLINE" basePath="/operations/offline-tasks" />
      </div>
    </div>
  );
}

export function RealtimeTaskOperationsPage() {
  return (
    <div className="min-h-full bg-[#f6f6f6]">
      <PageHeader title="实时同步运维" bordered className="bg-white px-6 max-md:px-4" />
      <div className="px-6 pb-8 pt-5 max-md:px-4">
        <DataSyncInstances syncType="REALTIME" basePath="/operations/realtime-tasks" />
      </div>
    </div>
  );
}

export function OfflineTaskOperationsInstanceDetailPage() {
  return (
    <DataSyncInstanceDetailPage
      syncType="OFFLINE"
      basePath="/operations/offline-tasks"
      listPath="/operations/offline-tasks"
      backLabel="返回运维中心"
    />
  );
}

export function RealtimeTaskOperationsInstanceDetailPage() {
  return (
    <DataSyncInstanceDetailPage
      syncType="REALTIME"
      basePath="/operations/realtime-tasks"
      listPath="/operations/realtime-tasks"
      backLabel="返回运维中心"
    />
  );
}
