import { useEffect, useState } from "react";
import { Navigate, useParams } from "react-router-dom";

import { getDataSyncInstance } from "@/service/data-sync";

/** Resolve an Operations instance through its authorized API before linking its task. */
export function DataSyncInstanceRedirect() {
  const { id } = useParams<{ id: string }>();
  const [destination, setDestination] = useState<{ id: string; path: string }>();
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    void getDataSyncInstance(id)
      .then((instance) => {
        if (controller.signal.aborted) return;
        setDestination({
          id,
          path: `/data-sync/${encodeURIComponent(instance.taskId)}/detail?executionId=${encodeURIComponent(instance.id)}`,
        });
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
