import { useState } from "react";
import { ChevronRight, Database, FolderTree, Search, Table2 } from "lucide-react";
import { PanelHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import {
  listDataSourceDatabases,
  listDataSources,
  listDataSourceTables,
  type DataSourceCatalogTable,
  type DataSourceRecord,
} from "@/service/datasource";
import { useDataDevelopmentWorkbench } from "../workbench-context";

export function DataCatalogPanel({ width }: { width: number }) {
  const [keyword, setKeyword] = useState("");
  const [expandedSource, setExpandedSource] = useState<string | null>(null);
  const [expandedDatabase, setExpandedDatabase] = useState<string | null>(null);
  const { openCatalogTable } = useDataDevelopmentWorkbench();

  const sourcesQuery = useQuery({
    queryKey: ["data-development", "catalog", "sources", keyword],
    queryFn: ({ signal }) =>
      listDataSources({ pageNo: 1, pageSize: 100, keyword: keyword.trim() || undefined }, signal),
  });

  const sourceId = expandedSource;
  const databasesQuery = useQuery({
    queryKey: ["data-development", "catalog", "databases", sourceId],
    queryFn: () => listDataSourceDatabases(sourceId!),
    enabled: Boolean(sourceId),
  });

  const [databaseSourceId, databaseName] = (expandedDatabase ?? "").split("::");
  const tablesQuery = useQuery({
    queryKey: ["data-development", "catalog", "tables", databaseSourceId, databaseName],
    queryFn: ({ signal }) =>
      listDataSourceTables(
        databaseSourceId,
        { database: databaseName, limit: 100 },
        signal,
      ),
    enabled: Boolean(databaseSourceId && databaseName),
  });

  const sources: DataSourceRecord[] = sourcesQuery.data?.totalList ?? [];
  const tables: DataSourceCatalogTable[] = tablesQuery.data ?? [];

  return (
    <aside
      aria-label="数据目录"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="数据目录" />
      <div className="border-b border-[#ececec] px-3 py-2">
        <div className="flex h-8 items-center gap-2 rounded-[4px] border border-[#dcdfe5] bg-white px-2 text-[#8a8f98]">
          <Search size={14} />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索数据源"
            className="min-w-0 flex-1 bg-transparent text-[12px] text-[#30343a] outline-none placeholder:text-[#a5aab2]"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto py-1">
        {sourcesQuery.isLoading ? (
          <div className="flex h-24 items-center justify-center text-[12px] text-[#9aa0a8]">加载中...</div>
        ) : sources.length === 0 ? (
          <div className="px-4 py-10 text-center text-[12px] text-[#9aa0a8]">暂无数据源</div>
        ) : (
          sources.map((source) => {
            const sourceKey = source.id ?? "";
            const sourceOpen = Boolean(sourceKey) && expandedSource === sourceKey;
            return (
              <div key={sourceKey}>
                <button
                  type="button"
                  className="flex w-full cursor-pointer items-center gap-1.5 px-3 py-2 text-left hover:bg-[#f7f8fa]"
                  onClick={() => {
                    setExpandedSource(sourceOpen ? null : sourceKey);
                    setExpandedDatabase(null);
                  }}
                >
                  <ChevronRight
                    size={13}
                    className={sourceOpen ? "rotate-90 transition-transform" : "transition-transform"}
                  />
                  <Database size={14} className="text-[#5d6570]" />
                  <span className="min-w-0 flex-1 truncate text-[12px] text-[#30343a]">
                    {source.name}
                  </span>
                </button>

                {sourceOpen ? (
                  <div className="pl-5">
                    {databasesQuery.isLoading ? (
                      <div className="px-3 py-2 text-[11px] text-[#9aa0a8]">加载数据库...</div>
                    ) : (databasesQuery.data ?? []).length === 0 ? (
                      <div className="px-3 py-2 text-[11px] text-[#9aa0a8]">暂无数据库</div>
                    ) : (
                      (databasesQuery.data ?? []).map((database) => {
                        const databaseKey = sourceKey + "::" + database;
                        const open = expandedDatabase === databaseKey;
                        return (
                          <div key={databaseKey}>
                            <button
                              type="button"
                              className="flex w-full cursor-pointer items-center gap-1.5 px-2 py-1.5 text-left hover:bg-[#f7f8fa]"
                              onClick={() => setExpandedDatabase(open ? null : databaseKey)}
                            >
                              <ChevronRight
                                size={12}
                                className={open ? "rotate-90 transition-transform" : "transition-transform"}
                              />
                              <FolderTree size={13} className="text-[#7b8490]" />
                              <span className="min-w-0 flex-1 truncate text-[11px] text-[#444b55]">{database}</span>
                            </button>

                            {open ? (
                              <div className="pl-5">
                                {tablesQuery.isLoading ? (
                                  <div className="px-2 py-2 text-[11px] text-[#9aa0a8]">加载表...</div>
                                ) : tables.length === 0 ? (
                                  <div className="px-2 py-2 text-[11px] text-[#9aa0a8]">暂无表</div>
                                ) : (
                                  tables.map((table) => (
                                    <button
                                      key={table.name}
                                      type="button"
                                      className="flex w-full cursor-pointer items-center gap-1.5 px-2 py-1.5 text-left hover:bg-[#eef3ff]"
                                      onClick={() =>
                                        openCatalogTable(
                                          sourceKey,
                                          source.name || "数据源",
                                          database,
                                          table.name,
                                        )
                                      }
                                    >
                                      <Table2 size={13} className="text-[#6d7682]" />
                                      <span className="min-w-0 flex-1 truncate text-[11px] text-[#3e454f]">
                                        {table.name}
                                      </span>
                                    </button>
                                  ))
                                )}
                              </div>
                            ) : null}
                          </div>
                        );
                      })
                    )}
                  </div>
                ) : null}
              </div>
            );
          })
        )}
      </div>
    </aside>
  );
}
