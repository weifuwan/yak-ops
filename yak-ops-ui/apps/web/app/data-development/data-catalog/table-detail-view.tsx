import { useQuery } from "@tanstack/react-query";
import { Database, KeyRound, Table2 } from "lucide-react";

import {
  getDataSourceCatalogCapabilities,
  getDataSourceCatalogObject,
  listDataSourceCatalogColumns,
  listDataSourceCatalogIndexes,
  type CatalogObjectType,
} from "@/service/datasource";

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
  const metadataQuery = useQuery({
    queryKey: ["data-catalog", "object", dataSourceId, database, schema, tableName, objectType],
    queryFn: ({ signal }) => getDataSourceCatalogObject(dataSourceId, path, signal),
  });
  const columnsQuery = useQuery({
    queryKey: ["data-catalog", "columns", dataSourceId, database, schema, tableName],
    queryFn: ({ signal }) => listDataSourceCatalogColumns(dataSourceId, path, signal),
  });
  const indexesQuery = useQuery({
    queryKey: ["data-catalog", "indexes", dataSourceId, database, schema, tableName],
    queryFn: ({ signal }) => listDataSourceCatalogIndexes(dataSourceId, path, signal),
    enabled: supportsIndexes,
  });

  const metadata = metadataQuery.data;
  const columns = columnsQuery.data ?? [];
  const indexes = indexesQuery.data ?? [];

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col bg-white">
      <div className="shrink-0 border-b border-[#ececec] px-5 py-4">
        <div className="flex items-center gap-2">
          <Table2 size={17} className="text-[#5e6b82]" />
          <div className="text-[15px] font-medium text-[#24272c]">{tableName}</div>
        </div>
        <div className="mt-1 flex items-center gap-3 text-[11px] text-[#8a919b]">
          <span><Database size={11} className="mr-1 inline" />{dataSourceName}</span>
          {[database, schema].filter(Boolean).map((value) => <span key={value}>{value}</span>)}
        </div>
      </div>

      <div className="grid grid-cols-3 gap-px border-b border-[#ececec] bg-[#ececec]">
        {[
          ["对象类型", metadata?.type || objectType],
          ["备注", metadata?.comment],
          ["主键", metadata?.primaryKeys?.join(", ")],
        ].map(([label, value]) => (
          <div key={label} className="bg-white px-4 py-3">
            <div className="text-[11px] text-[#8a919b]">{label}</div>
            <div className="mt-1 truncate text-[12px] text-[#30343a]">{value || "-"}</div>
          </div>
        ))}
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        <table className="w-full border-collapse text-left text-[12px]">
          <thead className="sticky top-0 bg-[#fafafa] text-[#737b86]">
            <tr>
              <th className="border-b border-[#ececec] px-4 py-2.5 font-medium">字段名</th>
              <th className="border-b border-[#ececec] px-4 py-2.5 font-medium">类型</th>
              <th className="border-b border-[#ececec] px-4 py-2.5 font-medium">可空</th>
              <th className="border-b border-[#ececec] px-4 py-2.5 font-medium">备注</th>
            </tr>
          </thead>
          <tbody>
            {columns.map((column) => (
              <tr key={column.name} className="hover:bg-[#f7f8fa]">
                <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#30343a]">
                  {column.primaryKey || metadata?.primaryKeys?.includes(column.name) ? (
                    <KeyRound size={12} className="mr-1 inline text-[#d99a22]" />
                  ) : null}
                  {column.name}
                </td>
                <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">{column.typeName || "-"}</td>
                <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">
                  {column.nullable === undefined ? "-" : column.nullable ? "是" : "否"}
                </td>
                <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">{column.remarks || "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {columnsQuery.isLoading ? (
          <div className="p-6 text-center text-[12px] text-[#8b919b]">加载字段...</div>
        ) : null}
        {columnsQuery.isError ? (
          <div className="p-6 text-center text-[12px] text-[#c15b52]">字段加载失败</div>
        ) : null}
        {!columnsQuery.isLoading && !columnsQuery.isError && columns.length === 0 ? (
          <div className="p-6 text-center text-[12px] text-[#8b919b]">暂无字段信息</div>
        ) : null}

        <div className="border-t border-[#ececec]">
          <div className="px-4 py-3 text-[12px] font-medium text-[#424a55]">索引</div>
          {capabilitiesQuery.isLoading ? (
            <div className="px-4 pb-4 text-[11px] text-[#8b919b]">读取元数据能力...</div>
          ) : capabilitiesQuery.isError ? (
            <div className="px-4 pb-4 text-[11px] text-[#c15b52]">无法读取索引能力</div>
          ) : !supportsIndexes ? (
            <div className="px-4 pb-4 text-[11px] text-[#8b919b]">当前数据源暂不支持索引元数据</div>
          ) : indexesQuery.isLoading ? (
            <div className="px-4 pb-4 text-[11px] text-[#8b919b]">加载索引...</div>
          ) : indexesQuery.isError ? (
            <div className="px-4 pb-4 text-[11px] text-[#c15b52]">索引加载失败</div>
          ) : indexes.length === 0 ? (
            <div className="px-4 pb-4 text-[11px] text-[#8b919b]">暂无索引信息</div>
          ) : (
            <table className="w-full border-collapse text-left text-[12px]">
              <thead className="bg-[#fafafa] text-[#737b86]">
                <tr>
                  <th className="border-b border-[#ececec] px-4 py-2 font-medium">索引名</th>
                  <th className="border-b border-[#ececec] px-4 py-2 font-medium">字段</th>
                  <th className="border-b border-[#ececec] px-4 py-2 font-medium">类型</th>
                </tr>
              </thead>
              <tbody>
                {indexes.map((index) => (
                  <tr key={index.name}>
                    <td className="border-b border-[#f0f1f3] px-4 py-2 text-[#30343a]">{index.name}</td>
                    <td className="border-b border-[#f0f1f3] px-4 py-2 text-[#59616d]">{index.columns.join(", ")}</td>
                    <td className="border-b border-[#f0f1f3] px-4 py-2 text-[#59616d]">
                      {index.primary ? "主键" : index.unique ? "唯一" : "普通"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
