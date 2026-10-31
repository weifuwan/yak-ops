import { Alert, Button } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import { Navigate, useLocation, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { readTaskDraft, taskDraftKey } from "@/service/data-sync/draft";
import { getDataSource } from "@/service/datasource";
import { getDataSyncTask, type DataSyncDraft } from "@/service/data-sync";
import { TaskEditor } from "./index";
import type { TaskEditorInput } from "./types";

/** Resolve a saved solution from its definition, never from the route used to open it. */
export function DataSyncTaskEditorPage() {
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const key = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const [input, setInput] = useState<TaskEditorInput & { id: string }>();
  const [error, setError] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    setInput(undefined);
    setError(false);
    void getDataSyncTask(id, controller.signal)
      .then(async (task) => {
        const connections = await Promise.all([
          getDataSource(task.sourceDataSourceId, controller.signal),
          getDataSource(task.targetDataSourceId, controller.signal),
        ]);
        if (controller.signal.aborted) return;
        setInput({
          id,
          task,
          connections,
          draft: {
            name: task.name,
            sourceConnector: task.sourceConnector,
            sinkConnector: task.sinkConnector,
            sourceProducesChangelog: task.sourceProducesChangelog,
            solutionType: task.solutionType,
            steps: task.steps,
            sourceType: connections[0].dbType || "",
            targetType: connections[1].dbType || "",
          },
        });
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      });
    return () => controller.abort();
  }, [id, reload]);

  if (!id) {
    const state = location.state as { draftKey?: string; draft?: DataSyncDraft } | null;
    const draft = readTaskDraft(key) || (state?.draftKey === key ? state.draft : undefined);
    if (!draft) return <Navigate replace to="/data-sync?create=1" />;
    return <TaskEditor key="new" draft={draft} />;
  }
  if (error)
    return (
      <div className="p-6">
        <Alert>
          任务加载失败
          <Button size="small" onClick={() => setReload((value) => value + 1)}>
            重试
          </Button>
        </Alert>
      </div>
    );
  if (!input || input.id !== id)
    return (
      <div className="p-8 text-sm text-[var(--yak-components-collapse-section-icon)]">
        正在加载同步任务...
      </div>
    );
  return <TaskEditor key={id} {...input} />;
}

export default DataSyncTaskEditorPage;
