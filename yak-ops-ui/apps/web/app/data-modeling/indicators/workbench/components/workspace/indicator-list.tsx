import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button, Input, Select, SelectContent, SelectItem, SelectItemIndicator, SelectItemText,
  SelectTrigger, SelectValue, Table, type TableColumns,
} from "@yak-ops/yak-ui";
import { RefreshCw, Search } from "lucide-react";

import { modelingEntityApi, type ModelingRecord, type ModelingResourceKey } from "../../../../service";
import { DW_TYPE_OPTIONS, PAGE_SIZE_OPTIONS, RELATION_FIELDS } from "../../config/indicator-definitions";
import type { IndicatorDefinition, IndicatorFieldOption } from "../../types";
import { recordName, selectOptionLabel } from "../../utils/indicator-form";

type IndicatorListTabProps = {
  projectCode: string;
  definition: IndicatorDefinition;
  dwType: string;
  optionsByResource: Partial<Record<ModelingResourceKey, ModelingRecord[]>>;
  onEdit: (record: ModelingRecord) => void;
  onDelete: (record: ModelingRecord) => void;
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
  const [businessTypeId, setBusinessTypeId] = useState("");
  const [domainId, setDomainId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [applied, setApplied] = useState({
    keyword: "",
    status: "all",
    businessTypeId: "",
    domainId: "",
    categoryId: "",
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
      definition.key === "atomIndicator" ? dwType : "all",
    ],
    queryFn: () => {
      const params: Record<string, unknown> = {
        pageNo,
        pageSize,
        keyword: applied.keyword || undefined,
      };
      if (definition.key === "atomIndicator") params.dwType = dwType;
      if (applied.status !== "all") params.submit = applied.status;
      if (applied.businessTypeId) params.busTypeId = applied.businessTypeId;
      if (applied.domainId) params.domainId = applied.domainId;
      if (applied.categoryId) params.categoryId = applied.categoryId;
      return modelingEntityApi.page<ModelingRecord>(projectCode, definition.key, params);
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
    { key: "sequence", title: "序号", width: 64, align: "center", render: (_value, _record, index) => String((pageNo - 1) * pageSize + index + 1).padStart(2, "0") },
    ...definition.columns.map((column) => ({
      key: column.key,
      title: column.title,
      dataIndex: column.key,
      width: column.width,
      ellipsis: true,
      render: (value: unknown, record: ModelingRecord) => {
        if (column.key === "cnName") {
          return (
            <div className="min-w-0 leading-5">
              <div className="truncate text-xs text-[var(--yak-components-panel-text)]">{String(record.enName || "-")}</div>
              <div className="truncate text-xs font-medium text-[var(--yak-components-panel-text)]">{String(record.cnName || "-")}</div>
            </div>
          );
        }
        if (column.key === "submit") {
          const submitted = record.submit === true || record.submit === "true";
          return (
            <span className={submitted
              ? "inline-flex rounded bg-[var(--yak-color-hover)] px-2 py-1 text-[11px] text-[var(--yak-components-panel-text)]"
              : "inline-flex rounded bg-[var(--yak-color-hover)] px-2 py-1 text-[11px] text-[var(--yak-components-muted-text)]"}>
              {submitted ? "已提交" : "草稿"}
            </span>
          );
        }
        if (column.key === "dwType") return DW_TYPE_OPTIONS.find((option) => option.value === String(value))?.label || "-";
        if (column.key === "computeMode") return String(value) === "1" ? "同环比" : "计算表达式";
        if (column.key === "modifierType") return String(value) === "DIMENSION" ? "维度枚举" : "普通业务";
        return relationLabel(column.key, value);
      },
    })),
    { key: "updateTime", title: "最近修改时间", width: 170, render: (_value, record) => String(record.updateTime || "-") },
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
    setApplied({ keyword, status, businessTypeId, domainId, categoryId });
  };

  const resetSearch = () => {
    setKeyword("");
    setStatus("all");
    setBusinessTypeId("");
    setDomainId("");
    setCategoryId("");
    setPageNo(1);
    setApplied({ keyword: "", status: "all", businessTypeId: "", domainId: "", categoryId: "" });
  };

  const filterOptions = (resource: ModelingResourceKey, labelField?: string): IndicatorFieldOption[] =>
    (optionsByResource[resource] || []).map((record) => ({
      value: String(record.id || ""),
      label: String(record[labelField || "cnName"] || recordName(record)),
    })).filter((option) => option.value);

  const statusOptions: IndicatorFieldOption[] = [
    { value: "all", label: "全部状态" },
    { value: "true", label: "已提交" },
    { value: "false", label: "草稿" },
  ];
  const categoryOptions: IndicatorFieldOption[] = [
    { value: "all", label: "全部周期" },
    ...filterOptions("timePeriodCategory"),
  ];
  const businessTypeOptions: IndicatorFieldOption[] = [
    { value: "all", label: "全部业务分类" },
    ...filterOptions("businessType", "businessName"),
  ];
  const domainOptions: IndicatorFieldOption[] = [
    { value: "all", label: "全部数据域" },
    ...filterOptions("dataDomain"),
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
        {definition.key === "timePeriod" ? (
          <div className="w-40">
            <Select value={categoryId || "all"} size="small" onValueChange={(value) => setCategoryId(value === "all" ? "" : String(value))}>
              <SelectTrigger variant="outlined">
                <SelectValue placeholder="周期粒度">
                  {(value) => selectOptionLabel(categoryOptions, value, "周期粒度")}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                {categoryOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText></SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ) : (
          <>
            <div className="w-36">
              <Select value={businessTypeId || "all"} size="small" onValueChange={(value) => setBusinessTypeId(value === "all" ? "" : String(value))}>
                <SelectTrigger variant="outlined">
                  <SelectValue placeholder="业务分类">
                    {(value) => selectOptionLabel(businessTypeOptions, value, "业务分类")}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {businessTypeOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText></SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="w-36">
              <Select value={domainId || "all"} size="small" onValueChange={(value) => setDomainId(value === "all" ? "" : String(value))}>
                <SelectTrigger variant="outlined">
                  <SelectValue placeholder="数据域">
                    {(value) => selectOptionLabel(domainOptions, value, "数据域")}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {domainOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}><SelectItemText>{option.label}</SelectItemText></SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </>
        )}
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
