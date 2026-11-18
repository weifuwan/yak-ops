import { useQuery } from "@tanstack/react-query";
import { Database, KeyRound, Table2 } from "lucide-react";

import {
  listDataSourceColumns,
  listDataSourceTableMetadata,
} from "@/service/datasource";

export function TableDetailView({
  dataSourceId,
  dataSourceName,
  database,
  tableName,
}: {
  dataSourceId: string;
  dataSourceName: string;
  database: string;
  tableName: string;
}) {
  const metadataQuery = useQuery({
    queryKey: ["data-development", "catalog", "table", dataSourceId, database, tableName],
    queryFn: ({ signal }) =>
      listDataSourceTableMetadata(dataSourceId, database, [tableName], signal),
  });

  const columnsQuery = useQuery({
    queryKey: ["data-development", "catalog", "columns", dataSourceId, database, tableName],
    queryFn: ({ signal }) =>
      listDataSourceColumns(dataSourceId, { database, table: tableName }, signal),
  });

  const metadata = metadataQuery.data?.[0];
  const columns = columnsQuery.data ?? [];

  return (
    <div className="flex h-full min-h-0 flex-col bg-white">
      <div className="shrink-0 border-b border-[#ececec] px-5 py-4">
        <div className="flex items-center gap-2">
          <Table2 size={17} className="text-[#5e6b82]" />
          <div className="text-[15px] font-medium text-[#24272c]">{tableName}</div>
        </div>
        <div className="mt-1 flex items-center gap-3 text-[11px] text-[#8a919b]">
          <span><Database size={11} className="mr-1 inline" />{dataSourceName}</span>
          <span>{database}</span>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-px border-b border-[#ececec] bg-[#ececec]">
        {[
          ["表类型", metadata?.type],
          ["备注", metadata?.remarks],
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
                  {metadata?.primaryKeys?.includes(column.name) ? (
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
        {!columnsQuery.isLoading && columns.length === 0 ? (
          <div className="p-6 text-center text-[12px] text-[#8b919b]">暂无字段信息</div>
        ) : null}
      </div>
    </div>
  );
}
