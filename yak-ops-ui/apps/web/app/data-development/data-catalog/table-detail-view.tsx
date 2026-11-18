import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  getDataSourceCatalogCapabilities,
  getDataSourceCatalogObject,
  listDataSourceCatalogColumns,
  listDataSourceCatalogIndexes,
  type CatalogObjectType,
} from "@/service/datasource";

import { ColumnsTab } from "./tabs/columns-tab";
import { IndexesTab } from "./tabs/indexes-tab";
import { ObjectHeader } from "./components/object-header";

type DetailTab = "columns" | "indexes";

export function TableDetailView({
  dataSourceId,
  dataSourceName,
  database,
  schema,
  tableName,
  objectType = "TABLE",
}: {
  dataSourceId: string;
  dataSourceName: string;
  database: string;
  schema?: string;
  tableName: string;
  objectType?: string;
}) {
  const [requestedTab, setRequestedTab] = useState<DetailTab>("columns");
  const path = {
    database: database || undefined,
    schema,
    objectName: tableName,
    objectType: objectType as CatalogObjectType,
  };

  const capabilitiesQuery = useQuery({
    queryKey: ["data-catalog", "capabilities", dataSourceId],
    queryFn: ({ signal }) => getDataSourceCatalogCapabilities(dataSourceId, signal),
  });
  const supportsIndexes = capabilitiesQuery.data?.features.includes("INDEXES") ?? false;
  const canShowIndexes =
    supportsIndexes && ["TABLE", "MATERIALIZED_VIEW"].includes(objectType);
  const activeTab = canShowIndexes && requestedTab === "indexes" ? "indexes" : "columns";

  const metadataQuery = useQuery({
    queryKey: ["data-catalog", "object", dataSourceId, database, schema, tableName, objectType],
    queryFn: ({ signal }) => getDataSourceCatalogObject(dataSourceId, path, signal),
  });
  const columnsQuery = useQuery({
    queryKey: ["data-catalog", "columns", dataSourceId, database, schema, tableName, objectType],
    queryFn: ({ signal }) => listDataSourceCatalogColumns(dataSourceId, path, signal),
  });
  const indexesQuery = useQuery({
    queryKey: ["data-catalog", "indexes", dataSourceId, database, schema, tableName, objectType],
    queryFn: ({ signal }) => listDataSourceCatalogIndexes(dataSourceId, path, signal),
    enabled: canShowIndexes && activeTab === "indexes",
  });

  const columns = columnsQuery.data ?? [];
  const indexes = indexesQuery.data ?? [];
  const primaryKeys = metadataQuery.data?.primaryKeys ?? [];

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col bg-white">
      <ObjectHeader
        dataSourceName={dataSourceName}
        database={database}
        schema={schema}
        objectName={tableName}
        objectType={objectType}
        metadata={metadataQuery.data}
        isLoading={metadataQuery.isLoading}
        isError={metadataQuery.isError}
        onRetry={() => void metadataQuery.refetch()}
      />

      <div
        role="tablist"
        aria-label="数据对象元数据"
        className="flex h-11 shrink-0 items-center gap-6 border-b border-[#e8eaee] bg-white px-5"
      >
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === "columns"}
          onClick={() => setRequestedTab("columns")}
          className={[
            "relative flex h-full cursor-pointer items-center gap-2 text-[12px] transition-colors",
            activeTab === "columns"
              ? "font-semibold text-[#2864c5]"
              : "text-[#667085] hover:text-[#344054]",
          ].join(" ")}
        >
          字段
          <span className="rounded-[3px] bg-[#f1f3f6] px-1.5 py-0.5 text-[10px] font-medium text-[#667085]">
            {columnsQuery.data ? columns.length : "—"}
          </span>
          {activeTab === "columns" ? (
            <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-[#3978e8]" />
          ) : null}
        </button>

        {canShowIndexes ? (
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "indexes"}
            onClick={() => setRequestedTab("indexes")}
            className={[
              "relative flex h-full cursor-pointer items-center gap-2 text-[12px] transition-colors",
              activeTab === "indexes"
                ? "font-semibold text-[#2864c5]"
                : "text-[#667085] hover:text-[#344054]",
            ].join(" ")}
          >
            索引
            <span className="rounded-[3px] bg-[#f1f3f6] px-1.5 py-0.5 text-[10px] font-medium text-[#667085]">
              {indexesQuery.data ? indexes.length : "—"}
            </span>
            {activeTab === "indexes" ? (
              <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-[#3978e8]" />
            ) : null}
          </button>
        ) : null}

        {capabilitiesQuery.isError && ["TABLE", "MATERIALIZED_VIEW"].includes(objectType) ? (
          <div className="ml-auto flex items-center gap-2 text-[11px] text-[#a0a6af]">
            <span>索引能力读取失败</span>
            <button
              type="button"
              onClick={() => void capabilitiesQuery.refetch()}
              className="cursor-pointer text-[#5677a8] hover:text-[#315b91]"
            >
              重试
            </button>
          </div>
        ) : null}
      </div>

      <div className="flex min-h-0 flex-1 flex-col">
        {activeTab === "columns" ? (
          <ColumnsTab
            columns={columns}
            primaryKeys={primaryKeys}
            isLoading={columnsQuery.isLoading}
            isError={columnsQuery.isError}
            onRetry={() => void columnsQuery.refetch()}
          />
        ) : (
          <IndexesTab
            indexes={indexes}
            isLoading={indexesQuery.isLoading}
            isError={indexesQuery.isError}
            onRetry={() => void indexesQuery.refetch()}
          />
        )}
      </div>
    </div>
  );
}
