import { ListTree } from "lucide-react";

import type { CatalogIndex } from "@/service/datasource";

export function IndexesTab({
  indexes,
  isLoading,
  isError,
  onRetry,
}: {
  indexes: CatalogIndex[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}) {
  return (
    <section className="flex min-h-0 flex-1 flex-col bg-white">
      <div className="flex shrink-0 items-center justify-between border-b border-[#eceff2] px-5 py-3">
        <div className="flex items-center gap-2 text-[12px] font-medium text-[#424a55]">
          <ListTree size={14} className="text-[#7b8797]" />
          索引信息
        </div>
        <div className="text-[11px] text-[#8a919b]">
          {isLoading ? "正在读取索引..." : `共 ${indexes.length} 个索引`}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        {isLoading ? (
          <div className="flex min-h-40 items-center justify-center text-[12px] text-[#8a919b]">
            正在加载索引元数据...
          </div>
        ) : isError ? (
          <div className="flex min-h-40 flex-col items-center justify-center gap-2 text-[12px] text-[#8a919b]">
            <span className="text-[#c15b52]">索引信息加载失败</span>
            <button
              type="button"
              onClick={onRetry}
              className="cursor-pointer rounded-[4px] px-3 py-1.5 text-[11px] text-[#5677a8] transition-colors hover:bg-[#f1f5fb]"
            >
              重新加载
            </button>
          </div>
        ) : indexes.length === 0 ? (
          <div className="flex min-h-40 items-center justify-center text-[12px] text-[#8a919b]">
            暂无索引信息
          </div>
        ) : (
          <table className="w-full min-w-[620px] border-collapse text-left text-[12px]">
            <thead className="sticky top-0 z-10 bg-[#fafafa] text-[#697586]">
              <tr>
                <th className="w-[32%] border-b border-[#eceff2] px-5 py-2.5 font-medium">
                  索引名
                </th>
                <th className="w-[48%] border-b border-[#eceff2] px-4 py-2.5 font-medium">
                  字段
                </th>
                <th className="border-b border-[#eceff2] px-4 py-2.5 font-medium">
                  类型
                </th>
              </tr>
            </thead>
            <tbody>
              {indexes.map((index) => (
                <tr key={index.name} className="transition-colors hover:bg-[#f7f8fa]">
                  <td className="border-b border-[#f0f1f3] px-5 py-2.5 text-[#30343a]">
                    {index.name}
                  </td>
                  <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">
                    {index.columns.join(", ") || "—"}
                  </td>
                  <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">
                    {index.primary ? "主键" : index.unique ? "唯一" : "普通"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
