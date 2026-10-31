import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { pageDataSourceTables, type DataSourceCatalogTable } from "@/service/datasource";
import type {
  DataSyncMappingTemplate,
  DataSyncTableMapping,
  DataSyncTableSelection,
} from "@/service/data-sync";
import { resolveMatchedMappings } from "./table-selection";

export function useResolveTableSelection(
  dataSourceId: string,
  maxTables: number,
  ruleIdentity: string,
  currentMappings: DataSyncTableMapping[],
) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const client = useQueryClient();
  const controller = useRef<AbortController>();
  const latest = useRef({ ruleIdentity, dataSourceId, mappings: currentMappings });
  latest.current = { ruleIdentity, dataSourceId, mappings: currentMappings };
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    setLoading(false);
    setError("");
    return () => controller.current?.abort();
  }, [dataSourceId, ruleIdentity]);
  const resolve = async (
    selection: DataSyncTableSelection,
    template: DataSyncMappingTemplate,
    apply: (mappings: DataSyncTableMapping[]) => void,
  ) => {
    if (!dataSourceId || !currentUser?.id || !currentWorkspace?.id || !maxTables) return;
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    setLoading(true);
    setError("");
    try {
      if (!selection.databasePattern.trim() || !selection.tablePattern.trim())
        throw new Error("请填写库名和表名正则");
      const tables = await client.fetchQuery({
        queryKey: [
          "data-sync-regex-tables",
          currentUser.id,
          currentWorkspace.id,
          dataSourceId,
          selection.databasePattern,
          selection.tablePattern,
        ],
        queryFn: async ({ signal }) => {
          const all: DataSourceCatalogTable[] = [];
          let offset: number | null = 0;
          while (offset !== null) {
            const page = await pageDataSourceTables(
              dataSourceId,
              {
                databasePattern: selection.databasePattern,
                tablePattern: selection.tablePattern,
                offset,
                limit: Math.min(maxTables + 1, 500),
              },
              AbortSignal.any([signal, request.signal]),
            );
            all.push(...page.items);
            if (all.length > maxTables)
              throw new Error(`匹配表数超过 ${maxTables}，请缩小正则范围`);
            if (page.nextOffset !== null && page.nextOffset <= offset)
              throw new Error("目录分页响应无效");
            offset = page.nextOffset;
          }
          return all;
        },
        staleTime: 0,
      });
      if (
        request.signal.aborted ||
        latest.current.ruleIdentity !== ruleIdentity ||
        latest.current.dataSourceId !== dataSourceId
      )
        return;
      apply(resolveMatchedMappings(latest.current.mappings, tables, selection, template));
    } catch (failure) {
      if (!request.signal.aborted)
        setError(failure instanceof Error ? failure.message : "匹配库表失败");
    } finally {
      if (!request.signal.aborted) setLoading(false);
    }
  };
  return { resolve, loading, error };
}
