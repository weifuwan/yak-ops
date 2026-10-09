import { DataSyncInstances } from "@/app/data-sync/instance-runtime";

interface OfflineSyncInstancesProps {
  taskId?: string;
}

export function OfflineSyncInstances({ taskId }: OfflineSyncInstancesProps) {
  return <DataSyncInstances syncType="OFFLINE" basePath="/offline-sync" taskId={taskId} />;
}

export default OfflineSyncInstances;
