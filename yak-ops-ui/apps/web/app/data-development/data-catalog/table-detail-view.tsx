import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Tabs, TabsList, TabsPanel, TabsTab } from "@yak-ops/yak-ui";

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

      <Tabs
        value={activeTab}
        onValueChange={(value) => {
          if (value === "columns" || value === "indexes") {
            setRequestedTab(value);
          }
        }}
        className="flex min-h-0 flex-1 flex-col"
      >
        <div className="flex h-11 shrink-0 items-stretch justify-between border-b border-[#e8eaee] bg-white">
          <TabsList className="!h-full !min-w-0 !flex-1 !gap-6 !border-b-0 !px-5">
            <TabsTab value="columns" className="!gap-1 !px-0 !pb-2.5 !text-xs data-active:!text-[var(--yak-components-tab-text)]">
              <span>字段</span>
              <span
                className="rounded-[3px] bg-[#f1f3f6] px-1.5 py-0.5 text-[10px] font-medium text-[#667085]"
              >
                {columnsQuery.data ? columns.length : "—"}
              </span>
            </TabsTab>

            {canShowIndexes ? (
              <TabsTab value="indexes" className="!gap-1 !px-0 !pb-2.5 !text-xs data-active:!text-[var(--yak-components-tab-text)]">
                <span>索引</span>
                <span
                  className="rounded-[3px] bg-[#f1f3f6] px-1.5 py-0.5 text-[10px] font-medium text-[#667085]"
                >
                  {indexesQuery.data ? indexes.length : "—"}
                </span>
              </TabsTab>
            ) : null}
          </TabsList>

          {capabilitiesQuery.isError && ["TABLE", "MATERIALIZED_VIEW"].includes(objectType) ? (
            <div className="flex shrink-0 items-center gap-2 pr-5 text-[11px] text-[#a0a6af]">
              <span>索引能力读取失败</span>
              <button
                type="button"
                onClick={() => void capabilitiesQuery.refetch()}
                className="cursor-pointer text-[var(--yak-color-primary)] hover:opacity-80"
              >
                重试
              </button>
            </div>
          ) : null}
        </div>

        <TabsPanel value="columns" className="flex min-h-0 flex-1 flex-col">
          <ColumnsTab
            columns={columns}
            primaryKeys={primaryKeys}
            isLoading={columnsQuery.isLoading}
            isError={columnsQuery.isError}
            onRetry={() => void columnsQuery.refetch()}
          />
        </TabsPanel>

        {canShowIndexes ? (
          <TabsPanel value="indexes" className="flex min-h-0 flex-1 flex-col">
            <IndexesTab
              indexes={indexes}
              isLoading={indexesQuery.isLoading}
              isError={indexesQuery.isError}
              onRetry={() => void indexesQuery.refetch()}
            />
          </TabsPanel>
        ) : null}
      </Tabs>
    </div>
  );
}
