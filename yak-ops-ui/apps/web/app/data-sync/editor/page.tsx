import { useQuery } from "@tanstack/react-query";
import { Alert, Button } from "@yak-ops/yak-ui";
import { Navigate, useParams } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";
import type { DataSyncScope, TaskDefinitionRecord } from "@/service/data-sync";
import {
  nativeDraftKey,
  readNativeDraft,
} from "@/service/data-sync/native-draft";
import {
  readPipelineDefinition,
  type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import { getDataSource, type DataSourceRecord } from "@/service/datasource";
import { getTaskDefinition } from "@/service/data-sync";
import { listLatestTaskInstances } from "@/service/task-execution";
import {
  DatabaseTaskEditor,
  SingleTableTaskEditor,
  TaskEditor,
} from "./index";
import { ReadonlyTaskEditor } from "./readonly-editor";

interface EditPayload {
  kind: "editable";
  task: TaskDefinitionRecord;
  definition: PipelineDefinition;
  connections: DataSourceRecord[];
}

interface ReadonlyPayload {
  kind: "readonly";
  definition: TaskDefinitionRecord;
  error: string;
}

const newEditorPath = (scope: DataSyncScope) =>
  scope === "DATABASE"
    ? "/data-sync/new/database"
    : "/data-sync/new/single-table";

function NewDataSyncTaskEditor({ scope }: { scope: DataSyncScope }) {
  const { currentUser } = useAuth();
  const key = nativeDraftKey(currentUser!.id);
  const draft = readNativeDraft(key);

  if (!draft) {
    return <Navigate replace to="/data-sync?create=1" />;
  }
  if (draft.scope !== scope) {
    return <Navigate replace to={newEditorPath(draft.scope)} />;
  }

  return scope === "DATABASE" ? (
    <DatabaseTaskEditor key={"new:" + key + ":database"} initialDraft={draft} />
  ) : (
    <SingleTableTaskEditor
      key={"new:" + key + ":single-table"}
      initialDraft={draft}
    />
  );
}

export function NewDataSyncTaskEditorRedirect() {
  const { currentUser } = useAuth();
  const key = nativeDraftKey(currentUser!.id);
  const draft = readNativeDraft(key);

  if (!draft) {
    return <Navigate replace to="/data-sync?create=1" />;
  }
  return <Navigate replace to={newEditorPath(draft.scope)} />;
}

export function DatabaseSyncTaskEditorPage() {
  return <NewDataSyncTaskEditor scope="DATABASE" />;
}

export function SingleTableSyncTaskEditorPage() {
  return <NewDataSyncTaskEditor scope="SINGLE_TABLE" />;
}

export function DataSyncTaskEditorPage() {
  const { id } = useParams<{ id: string }>();
  const { currentUser } = useAuth();

  const activeQuery = useQuery({
    queryKey: ["data-sync-editor-active", currentUser?.id, id],
    queryFn: ({ signal }) => listLatestTaskInstances([id!], true, signal),
    enabled: Boolean(id && currentUser?.id),
    refetchInterval: (query) => (query.state.data?.length ? 2000 : 5000),
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  });
  const executionLocked =
    Boolean(id) &&
    (!activeQuery.isSuccess ||
      Boolean(
        activeQuery.data?.some((instance) => instance.definitionId === id),
      ));
  const executionLockMessage = activeQuery.data?.some(
    (instance) => instance.definitionId === id,
  )
    ? "任务正在执行，完成或停止后才能修改配置。"
    : "正在确认任务运行状态，暂不可修改配置。";

  const query = useQuery<EditPayload | ReadonlyPayload>({
    queryKey: ["data-sync-editor", currentUser?.id, id],
    enabled: Boolean(id && currentUser?.id),
    gcTime: 0,
    refetchOnWindowFocus: false,
    queryFn: async ({ signal }) => {
      const task = await getTaskDefinition(id!, signal);
      let definition: PipelineDefinition;
      try {
        definition = readPipelineDefinition(task.parameters);
      } catch (error) {
        return {
          kind: "readonly",
          definition: task,
          error: error instanceof Error ? error.message : "无法解析 Pipeline",
        };
      }
      const connections = await Promise.all([
        getDataSource(definition.source["datasource-id"], signal),
        getDataSource(definition.sink["datasource-id"], signal),
      ]);
      return { kind: "editable", task, definition, connections };
    },
  });

  if (!id) {
    return <Navigate replace to="/data-sync/new" />;
  }
  if (query.isError) {
    return (
      <div className="p-6">
        <Alert>
          {query.error instanceof Error ? query.error.message : "任务加载失败"}
          <Button
            variant="secondary"
            size="sm"
            onClick={() => void query.refetch()}
          >
            重试
          </Button>
        </Alert>
      </div>
    );
  }
  if (!query.data) {
    return <div className="p-8 text-sm">正在加载同步任务...</div>;
  }
  if (query.data.kind === "readonly") {
    return (
      <ReadonlyTaskEditor
        key={id}
        definition={query.data.definition}
        error={query.data.error}
      />
    );
  }

  return (
    <TaskEditor
      key={id}
      initialTask={query.data.task}
      initialDefinition={query.data.definition}
      connections={query.data.connections}
      executionLocked={executionLocked}
      executionLockMessage={executionLockMessage}
    />
  );
}

export default DataSyncTaskEditorPage;
