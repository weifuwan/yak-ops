import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button, Input, Select, SelectContent, SelectItem, SelectItemIndicator, SelectItemText,
  SelectTrigger, SelectValue, Table, type TableColumns,
} from "@yak-ops/yak-ui";
import { RefreshCw, Search } from "lucide-react";

import { getIndicatorApi } from "@/service/data-modeling/indicators";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { DW_TYPE_OPTIONS, PAGE_SIZE_OPTIONS, RELATION_FIELDS } from "../../config/indicator-definitions";
import type {
  IndicatorDefinition,
  IndicatorFieldOption,
  IndicatorListFilterKey,
  IndicatorOptionResource,
} from "../../types";
import { recordName, selectOptionLabel } from "../../utils/indicator-form";

type IndicatorListTabProps = {
  projectCode: string;
  definition: IndicatorDefinition;
  dwType: string;
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
  onEdit: (record: ModelingRecord) => void;
  onDelete: (record: ModelingRecord) => void;
};

const EMPTY_FILTERS: Record<IndicatorListFilterKey, string> = {
  businessTypeId: "",
  domainId: "",
  categoryId: "",
};

export function IndicatorList({
  projectCode,
  definition,
  dwType,
  optionsByResource,
  onEdit,
  onDelete,
}: IndicatorListTabProps) {
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("all");
  const [filters, setFilters] = useState({ ...EMPTY_FILTERS });
  const [applied, setApplied] = useState({
    keyword: "",
    status: "all",
    filters: { ...EMPTY_FILTERS },
  });
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const pageQuery = useQuery({
    queryKey: [
      "modeling",
      "indicator-page",
      projectCode,
      definition.key,
      pageNo,
      pageSize,
      JSON.stringify(applied),
      definition.filterByDwType ? dwType : "all",
    ],
    queryFn: () => {
      const params: Record<string, unknown> = {
        pageNo,
        pageSize,
        keyword: applied.keyword || undefined,
      };
      if (definition.filterByDwType) params.dwType = dwType;
      if (applied.status !== "all") params.submit = applied.status;
      (definition.listFilters || []).forEach((filter) => {
        const value = applied.filters[filter.key];
        if (value) params[filter.paramKey] = value;
      });
      return getIndicatorApi(definition.key).page<ModelingRecord>(projectCode, params);
    },
    enabled: Boolean(projectCode),
  });

  const relationLabel = (key: string, value: unknown): string => {
    if (value === undefined || value === null || value === "") return "-";
    const source = RELATION_FIELDS[key];
    if (!source) return String(value);
    const option = (optionsByResource[source] || []).find((item) => String(item.id) === String(value));
    return option ? recordName(option) : String(value);
  };

  const columns: TableColumns<ModelingRecord> = [
    {
      key: "sequence",
      title: "序号",
      width: 64,
      align: "center",
      render: (_value, _record, index) => String((pageNo - 1) * pageSize + index + 1).padStart(2, "0"),
    },
    ...definition.columns.map((column) => ({
      key: column.key,
      title: column.title,
      dataIndex: column.key,
      width: column.width,
      ellipsis: true,
      render: (value: unknown, record: ModelingRecord) => {
        const format = definition.columnFormats?.[column.key]
          || (RELATION_FIELDS[column.key] ? "relation" : undefined);
        if (format === "indicatorName") {
          return (
            <div className="min-w-0 leading-5">
              <div className="truncate text-xs text-[var(--yak-components-panel-text)]">{String(record.enName || "-")}</div>
              <div className="truncate text-xs font-medium text-[var(--yak-components-panel-text)]">{String(record.cnName || "-")}</div>
            </div>
          );
        }
        if (format === "status") {
          const submitted = record.submit === true || record.submit === "true";
          return (
            <span className={submitted
              ? "inline-flex rounded bg-[var(--yak-color-hover)] px-2 py-1 text-[11px] text-[var(--yak-components-panel-text)]"
              : "inline-flex rounded bg-[var(--yak-color-hover)] px-2 py-1 text-[11px] text-[var(--yak-components-muted-text)]"}>
              {submitted ? "已提交" : "草稿"}
            </span>
          );
        }
        if (format === "dwType") {
          return DW_TYPE_OPTIONS.find((option) => option.value === String(value))?.label || "-";
        }
        if (format === "computeMode") return String(value) === "1" ? "同环比" : "计算表达式";
        if (format === "modifierType") return String(value) === "DIMENSION" ? "维度枚举" : "普通业务";
        if (format === "relation") return relationLabel(column.key, value);
        return value === undefined || value === null || value === "" ? "-" : String(value);
      },
    })),
    {
      key: "updateTime",
      title: "最近修改时间",
      width: 170,
      render: (_value, record) => String(record.updateTime || "-"),
    },
    {
      key: "actions",
      title: "操作",
      width: 132,
      align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEdit(record)}>编辑</Button>
          <span className="h-3 w-px bg-[var(--yak-components-control-border)]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[var(--yak-components-danger-text)]" onClick={() => onDelete(record)}>删除</Button>
        </div>
      ),
    },
  ];

  const applySearch = () => {
    setPageNo(1);
    setApplied({ keyword, status, filters: { ...filters } });
  };

  const resetSearch = () => {
    const emptyFilters = { ...EMPTY_FILTERS };
    setKeyword("");
    setStatus("all");
    setFilters(emptyFilters);
    setPageNo(1);
    setApplied({ keyword: "", status: "all", filters: emptyFilters });
  };

  const updateFilter = (key: IndicatorListFilterKey, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const filterOptions = (
    resource: IndicatorOptionResource,
    labelField?: string,
  ): IndicatorFieldOption[] => (optionsByResource[resource] || [])
    .map((record) => ({
      value: String(record.id || ""),
      label: String(record[labelField || "cnName"] || recordName(record)),
    }))
    .filter((option) => option.value);

  const statusOptions: IndicatorFieldOption[] = [
    { value: "all", label: "全部状态" },
    { value: "true", label: "已提交" },
    { value: "false", label: "草稿" },
  ];

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 flex-col bg-white p-4">
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[var(--yak-components-panel-border)] pb-3">
        <div className="relative min-w-[220px] flex-1">
          <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            className="pl-8"
            placeholder={"搜索" + definition.title + "名称"}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter") applySearch(); }}
          />
        </div>
        <div className="w-32">
          <Select size="small" value={status} onValueChange={(value) => setStatus(value ?? "all")}>
            <SelectTrigger variant="outlined">
              <SelectValue placeholder="状态">
                {(value) => selectOptionLabel(statusOptions, value, "状态")}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              {statusOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <SelectItemText>{option.label}</SelectItemText><SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {(definition.listFilters || []).map((filter) => {
          const options: IndicatorFieldOption[] = [
            { value: "all", label: filter.label },
            ...filterOptions(filter.optionsSource, filter.optionLabelField),
          ];
          return (
            <div key={filter.key} className="w-36">
              <Select
                size="small"
                value={filters[filter.key] || "all"}
                onValueChange={(value) => updateFilter(filter.key, value === "all" ? "" : String(value || ""))}
              >
                <SelectTrigger variant="outlined">
                  <SelectValue placeholder={filter.label}>
                    {(value) => selectOptionLabel(options, value, filter.label)}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {options.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <SelectItemText>{option.label}</SelectItemText><SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          );
        })}
        <Button size="sm" variant="primary" onClick={applySearch}><Search size={13} />查询</Button>
        <Button size="sm" variant="secondary" onClick={resetSearch}>重置</Button>
        <Button size="sm" variant="secondary" isDisabled={pageQuery.isFetching} onClick={() => void pageQuery.refetch()}>
          <RefreshCw size={13} className={pageQuery.isFetching ? "animate-spin" : ""} />刷新
        </Button>
      </div>

      {pageQuery.isError ? (
        <div className="mt-3 flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          列表加载失败，请检查建模服务和当前工作空间权限。
          <Button size="sm" variant="secondary" onClick={() => void pageQuery.refetch()}>重试</Button>
        </div>
      ) : null}

      <div className="mt-3 min-h-0 flex-1">
        <Table<ModelingRecord>
          columns={columns}
          dataSource={pageQuery.data?.totalList || []}
          rowKey={(record) => record.id || JSON.stringify(record)}
          loading={pageQuery.isPending || pageQuery.isFetching}
          bordered
          size="small"
          scroll={{ x: 1100, y: "calc(100vh - 300px)" }}
          emptyText="暂无数据"
          pagination={pageQuery.data?.total ? {
            current: pageNo,
            pageSize,
            total: pageQuery.data.total,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            pageSizeLabel: "每页显示：",
            showSizeChanger: true,
            showQuickJumper: true,
            disabled: pageQuery.isFetching,
            onChange: (page, size) => { setPageNo(page); setPageSize(size); },
          } : false}
        />
      </div>
      <div className="mt-2 shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">{definition.description}</div>
    </div>
  );
}
