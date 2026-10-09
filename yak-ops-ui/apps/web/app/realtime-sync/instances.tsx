import { DataSyncInstances } from "@/app/data-sync/instance-runtime";

interface RealtimeSyncInstancesProps {
  taskId?: string;
}

export function RealtimeSyncInstances({ taskId }: RealtimeSyncInstancesProps) {
  return <DataSyncInstances syncType="REALTIME" basePath="/realtime-sync" taskId={taskId} />;
}

export default RealtimeSyncInstances;
