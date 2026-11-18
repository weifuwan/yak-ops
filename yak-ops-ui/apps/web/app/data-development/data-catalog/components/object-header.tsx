import {
  ChevronRight,
  Code2,
  Database,
  Eye,
  Hash,
  Layers3,
  Table2,
  Workflow,
  Zap,
} from "lucide-react";

import type { CatalogObject } from "@/service/datasource";

function ObjectTypeIcon({ type }: { type: string }) {
  const className = "shrink-0 text-[#5f6f86]";

  switch (type) {
    case "VIEW":
      return <Eye size={19} strokeWidth={1.8} className={className} />;
    case "MATERIALIZED_VIEW":
      return <Layers3 size={19} strokeWidth={1.8} className={className} />;
    case "FUNCTION":
      return <Code2 size={19} strokeWidth={1.8} className={className} />;
    case "PROCEDURE":
      return <Workflow size={19} strokeWidth={1.8} className={className} />;
    case "TRIGGER":
      return <Zap size={19} strokeWidth={1.8} className={className} />;
    case "SEQUENCE":
      return <Hash size={19} strokeWidth={1.8} className={className} />;
    case "TABLE":
    default:
      return <Table2 size={19} strokeWidth={1.8} className={className} />;
  }
}

function MetadataItem({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="min-w-0">
      <div className="text-[11px] leading-4 text-[#8a919b]">{label}</div>
      <div className="mt-1 truncate text-[12px] leading-5 text-[#30343a]" title={value}>
        {value || "—"}
      </div>
    </div>
  );
}

export function ObjectHeader({
  dataSourceName,
  database,
  schema,
  objectName,
  objectType,
  metadata,
  isLoading,
  isError,
  onRetry,
}: {
  dataSourceName: string;
  database: string;
  schema?: string;
  objectName: string;
  objectType: string;
  metadata?: CatalogObject;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}) {
  const resolvedType = metadata?.type || objectType;
  const primaryKeys = metadata?.primaryKeys ?? [];
  const primaryKeyText = isLoading
    ? "读取中..."
    : isError
      ? "读取失败"
      : primaryKeys.length > 0
        ? primaryKeys.join(", ")
        : "—";
  const breadcrumbItems = [dataSourceName, database, schema, objectName].filter(
    (item): item is string => Boolean(item),
  );

  return (
    <header className="shrink-0 border-b border-[#e8eaee] bg-white px-5 pb-4 pt-4">
      <div className="flex min-w-0 items-center gap-1.5 overflow-hidden text-[11px] text-[#89919c]">
        {breadcrumbItems.map((item, index) => (
          <span key={item + index} className="flex min-w-0 shrink-0 items-center gap-1.5">
            {index > 0 ? <ChevronRight size={12} className="shrink-0 text-[#b0b6bf]" /> : null}
            <span className={index === breadcrumbItems.length - 1 ? "truncate text-[#4a5360]" : "truncate"}>
              {item}
            </span>
          </span>
        ))}
      </div>

      <div className="mt-3 flex min-w-0 items-center gap-2.5">
        <ObjectTypeIcon type={resolvedType} />
        <h1 className="min-w-0 truncate text-[17px] font-semibold leading-6 text-[#24272c]">
          {objectName}
        </h1>
        <span className="shrink-0 rounded-[4px] border border-[#e5e8ed] bg-[#f7f8fa] px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-[#667085]">
          {resolvedType}
        </span>
        {isError ? (
          <button
            type="button"
            onClick={onRetry}
            className="ml-auto shrink-0 cursor-pointer rounded-[4px] px-2 py-1 text-[11px] text-[#5677a8] transition-colors hover:bg-[#f1f5fb]"
          >
            重试元数据
          </button>
        ) : null}
      </div>

      <div className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 border-t border-[#f0f1f3] pt-3 sm:grid-cols-4">
        <MetadataItem label="数据源" value={dataSourceName} />
        <MetadataItem label="数据库" value={database} />
        <MetadataItem label="Schema" value={schema || "—"} />
        <MetadataItem label="主键" value={primaryKeyText} />
      </div>

      {metadata?.comment ? (
        <div className="mt-3 border-t border-[#f0f1f3] pt-3">
          <MetadataItem label="备注" value={metadata.comment} />
        </div>
      ) : null}

      {isError ? (
        <div className="mt-2 text-[11px] text-[#c15b52]">
          对象元数据加载失败，字段信息仍可独立查看
        </div>
      ) : null}
    </header>
  );
}
