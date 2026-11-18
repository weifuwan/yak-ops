import { useMemo, useState } from "react";
import {
  ArrowDown,
  ArrowDownUp,
  ArrowUp,
  KeyRound,
  Search,
  X,
} from "lucide-react";
import { Input } from "@yak-ops/yak-ui";

import type { DataSourceCatalogColumn } from "@/service/datasource";

type SortKey = "name" | "typeName";

function SortHeader({
  label,
  active,
  direction,
  onClick,
}: {
  label: string;
  active: boolean;
  direction: "asc" | "desc";
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex cursor-pointer items-center gap-1 text-left font-medium text-[#697586] transition-colors hover:text-[#344054]"
    >
      {label}
      {active ? (
        direction === "asc" ? <ArrowUp size={12} /> : <ArrowDown size={12} />
      ) : (
        <ArrowDownUp size={12} className="text-[#b0b7c1]" />
      )}
    </button>
  );
}

function formatColumnType(column: DataSourceCatalogColumn) {
  const typeName = column.typeName?.trim();
  if (!typeName) return "—";
  if (typeName.includes("(") || !column.size || column.size <= 0) return typeName;

  const isNumeric = /^(DECIMAL|NUMERIC|NUMBER)$/i.test(typeName);
  const scale = column.scale;
  const scaleText = scale != null && (scale > 0 || isNumeric) ? `, ${scale}` : "";
  return `${typeName}(${column.size}${scaleText})`;
}

export function ColumnsTab({
  columns,
  primaryKeys = [],
  isLoading,
  isError,
  onRetry,
}: {
  columns: DataSourceCatalogColumn[];
  primaryKeys?: string[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}) {
  const [keyword, setKeyword] = useState("");
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

  const visibleColumns = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLocaleLowerCase();
    const filtered = columns.filter((column) => {
      if (!normalizedKeyword) return true;
      const searchableText = [
        column.name,
        column.typeName,
        column.remarks,
      ].filter(Boolean).join(" ").toLocaleLowerCase();
      return searchableText.includes(normalizedKeyword);
    });

    if (!sortKey) return filtered;

    return filtered
      .map((column, index) => ({ column, index }))
      .sort((left, right) => {
        const leftValue = String(left.column[sortKey] ?? "");
        const rightValue = String(right.column[sortKey] ?? "");
        const compared = leftValue.localeCompare(rightValue, undefined, {
          numeric: true,
          sensitivity: "base",
        });
        if (compared === 0) return left.index - right.index;
        return sortDirection === "asc" ? compared : -compared;
      })
      .map(({ column }) => column);
  }, [columns, keyword, sortDirection, sortKey]);

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDirection((direction) => direction === "asc" ? "desc" : "asc");
      return;
    }
    setSortKey(key);
    setSortDirection("asc");
  };

  return (
    <section className="flex min-h-0 flex-1 flex-col bg-white">
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-[#eceff2] px-5 py-3">
        <div className="relative w-full max-w-[360px]">
          <Search
            size={13}
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[#98a1ad]"
          />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索字段名、类型或备注"
            aria-label="搜索字段"
            className="!h-6 w-full !rounded-[4px] pl-8 pr-8 text-[11px]"
          />
          {keyword ? (
            <button
              type="button"
              aria-label="清除搜索"
              onClick={() => setKeyword("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[#98a1ad] hover:text-[#475467]"
            >
              <X size={13} />
            </button>
          ) : null}
        </div>
        <div className="text-[11px] text-[#8a919b]">
         
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        {isLoading ? (
          <div className="flex min-h-40 items-center justify-center text-[12px] text-[#8a919b]">
            正在加载字段元数据...
          </div>
        ) : isError ? (
          <div className="flex min-h-40 flex-col items-center justify-center gap-2 text-[12px] text-[#8a919b]">
            <span className="text-[#c15b52]">字段信息加载失败</span>
            <button
              type="button"
              onClick={onRetry}
              className="cursor-pointer rounded-[4px] px-3 py-1.5 text-[11px] text-[#5677a8] transition-colors hover:bg-[#f1f5fb]"
            >
              重新加载
            </button>
          </div>
        ) : visibleColumns.length === 0 ? (
          <div className="flex min-h-40 items-center justify-center text-[12px] text-[#8a919b]">
            {keyword.trim() ? "没有找到匹配的字段" : "暂无字段信息"}
          </div>
        ) : (
          <table className="w-full min-w-[680px] border-collapse text-left text-[12px]">
            <thead className="sticky top-0 z-10 bg-[#fafafa]">
              <tr>
                <th className="w-[34%] border-b border-[#eceff2] px-5 py-2.5">
                  <SortHeader
                    label="字段名"
                    active={sortKey === "name"}
                    direction={sortDirection}
                    onClick={() => toggleSort("name")}
                  />
                </th>
                <th className="w-[22%] border-b border-[#eceff2] px-4 py-2.5">
                  <SortHeader
                    label="类型"
                    active={sortKey === "typeName"}
                    direction={sortDirection}
                    onClick={() => toggleSort("typeName")}
                  />
                </th>
                <th className="w-[14%] border-b border-[#eceff2] px-4 py-2.5 font-medium text-[#697586]">
                  可空
                </th>
                <th className="border-b border-[#eceff2] px-4 py-2.5 font-medium text-[#697586]">
                  备注
                </th>
              </tr>
            </thead>
            <tbody>
              {visibleColumns.map((column) => {
                const isPrimaryKey = Boolean(
                  column.primaryKey || primaryKeys.includes(column.name),
                );
                return (
                  <tr key={column.name} className="transition-colors hover:bg-[#f7f8fa]">
                    <td className="border-b border-[#f0f1f3] px-5 py-2.5 text-[#30343a]">
                      {isPrimaryKey ? (
                        <KeyRound
                          size={12}
                          className="mr-1.5 inline text-[#d99a22]"
                          aria-label="主键"
                        />
                      ) : null}
                      <span>{column.name}</span>
                    </td>
                    <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">
                      {formatColumnType(column)}
                    </td>
                    <td className="border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]">
                      {column.nullable === undefined ? "—" : column.nullable ? "是" : "否"}
                    </td>
                    <td
                      className="max-w-[420px] border-b border-[#f0f1f3] px-4 py-2.5 text-[#59616d]"
                      title={column.remarks || undefined}
                    >
                      <span className="block truncate">{column.remarks || "—"}</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
