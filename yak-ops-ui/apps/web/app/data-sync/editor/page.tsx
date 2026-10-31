import { useQuery } from "@tanstack/react-query";
import { Alert, Button } from "@yak-ops/yak-ui";
import { Navigate, useLocation, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { readTaskDraft, taskDraftKey } from "@/service/data-sync/draft";
import { getDataSource } from "@/service/datasource";
import { getDataSyncTask, type DataSyncDraft } from "@/service/data-sync";
import { TaskEditor } from "./index";

/** Resolve the workflow from its saved definition and isolate reads by identity and workspace. */
export function DataSyncTaskEditorPage() {
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const key = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const query = useQuery({
    queryKey: ["data-sync-editor", currentUser?.id, currentWorkspace?.id, id],
    enabled: Boolean(id && currentUser?.id && currentWorkspace?.id),
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    gcTime: 0,
    queryFn: async ({ signal }) => {
      const task = await getDataSyncTask(id!, signal);
      const connections = await Promise.all([
        getDataSource(task.sourceDataSourceId, signal),
        getDataSource(task.targetDataSourceId, signal),
      ]);
      const draft: DataSyncDraft = {
        name: task.name,
        sourceConnector: task.sourceConnector,
        sinkConnector: task.sinkConnector,
        sourceProducesChangelog: task.sourceProducesChangelog,
        productType: task.productType,
        steps: task.steps,
        sourceType: connections[0].dbType || "",
        targetType: connections[1].dbType || "",
      };
      return { task, connections, draft };
    },
  });
  if (!id) {
    const state = location.state as { draftKey?: string; draft?: DataSyncDraft } | null;
    const draft = readTaskDraft(key) || (state?.draftKey === key ? state.draft : undefined);
    if (!draft) return <Navigate replace to="/data-sync?create=1" />;
    return <TaskEditor key={`new:${key}`} draft={draft} />;
  }
  if (query.isError)
    return (
      <div className="p-6">
        <Alert>
          {query.error instanceof Error ? query.error.message : "任务加载失败"}
          <Button size="small" onClick={() => void query.refetch()}>
            重试
          </Button>
        </Alert>
      </div>
    );
  if (!query.data)
    return (
      <div className="p-8 text-sm text-[var(--yak-components-collapse-section-icon)]">
        正在加载同步任务...
      </div>
    );
  return <TaskEditor key={`${key}:${id}`} {...query.data} />;
}

export default DataSyncTaskEditorPage;
