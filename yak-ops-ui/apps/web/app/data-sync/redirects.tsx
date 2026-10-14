import { useEffect, useState } from "react";
import { Navigate, useLocation, useParams } from "react-router-dom";

import { getTaskInstance } from "@/service/task-execution";

/** Convert supported legacy locations to the one data synchronization route family. */
export function DataSyncRouteRedirect({ kind }: { kind: "list" | "new" | "edit" | "detail" }) {
  const location = useLocation();
  const { id, taskId } = useParams<{ id: string; taskId: string }>();
  const incoming = new URLSearchParams(location.search);
  const query = new URLSearchParams();
  let path = "/data-sync";
  if (kind === "new") query.set("create", "1");
  else if (kind === "list") {
    const historyTask = incoming.get("taskId");
    if (incoming.get("tab") === "instances" && historyTask)
      path += `/${encodeURIComponent(historyTask)}/detail`;
    else
      query.set(
        "solutionType",
        location.pathname.startsWith("/realtime-sync")
          ? "SINGLE_TABLE_REALTIME"
          : "SINGLE_TABLE_OFFLINE",
      );
  } else {
    const identity = id || taskId;
    if (identity) path += `/${encodeURIComponent(identity)}/${kind}`;
  }
  if (kind === "detail" || path.endsWith("/detail")) {
    const execution = incoming.get("executionId");
    if (execution) query.set("executionId", execution);
    if (incoming.get("tab") === "log") query.set("tab", "log");
  }
  return <Navigate replace to={path + (query.size ? `?${query}` : "")} />;
}

/** Resolve a historical instance through its authorized API before linking its definition. */
export function DataSyncInstanceRedirect() {
  const { id } = useParams<{ id: string }>();
  const [destination, setDestination] = useState<{ id: string; path: string }>();
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    void getTaskInstance(id, controller.signal)
      .then((instance) => {
        if (controller.signal.aborted) return;
        const path =
          instance.taskType === "DATA_SYNC"
            ? `/data-sync/${encodeURIComponent(instance.definitionId)}/detail?executionId=${encodeURIComponent(instance.id)}`
            : "/data-sync";
        setDestination({ id, path });
      })
      .catch(() => {
        if (!controller.signal.aborted) setDestination({ id, path: "/data-sync" });
      });
    return () => controller.abort();
  }, [id]);
  if (!id) return <Navigate replace to="/data-sync" />;
  if (destination?.id === id) return <Navigate replace to={destination.path} />;
  return <div className="p-6 text-sm text-[#667085]">加载中...</div>;
}
