import { useCallback, useEffect, useState } from "react";

import { listDataSyncSolutions, type DataSyncSolution } from "@/service/data-sync";

/** Type-level discovery has no connection dependency; aborts protect rapidly changed selections. */
export function useSolutions(sourceType?: string, targetType?: string) {
  const [result, setResult] = useState<{
    key: string;
    choices: DataSyncSolution[];
    loading: boolean;
    error: string;
  }>({ key: "", choices: [], loading: false, error: "" });
  const [reload, setReload] = useState(0);
  const key = `${sourceType || ""}:${targetType || ""}`;
  const refresh = useCallback(() => setReload((value) => value + 1), []);
  useEffect(() => {
    if (!sourceType || !targetType) return;
    const controller = new AbortController();
    setResult({ key, choices: [], loading: true, error: "" });
    void listDataSyncSolutions(sourceType, targetType, controller.signal)
      .then((choices) => {
        if (!controller.signal.aborted) setResult({ key, choices, loading: false, error: "" });
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setResult({ key, choices: [], loading: false, error: "同步方案加载失败，请重试" });
        }
      });
    return () => controller.abort();
  }, [key, reload, sourceType, targetType]);
  const current = result.key === key;
  return {
    choices: current ? result.choices : [],
    loading: Boolean(sourceType && targetType) && (!current || result.loading),
    error: current ? result.error : "",
    refresh,
  };
}
