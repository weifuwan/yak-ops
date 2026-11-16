import { Eye, Plus, SquarePen, Trash2 } from "lucide-react";
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";

import type { BusinessProcessRecord } from "../types";
import { isSystemBusinessProcess } from "../types";
import { BUSINESS_PROCESS_PAGE_SIZE, type BusinessProcessFilters, type BusinessProcessSelectOption } from "./types";
import { BusinessProcessFiltersPanel } from "./business-process-filters";

interface Props {
  filters: BusinessProcessFilters;
  domainOptions: BusinessProcessSelectOption[];
  creatorOptions: string[];
  directorOptions: string[];
  domainsLoading: boolean;
  domainsError: boolean;
  creatorsLoading: boolean;
  creatorsError: boolean;
  directorsLoading: boolean;
  directorsError: boolean;
  onRetryDomains: () => void;
  onRetryCreators: () => void;
  onRetryDirectors: () => void;
  onFilterChange: (key: keyof BusinessProcessFilters, value: string) => void;
  onSearch: () => void;
  onReset: () => void;
  items: BusinessProcessRecord[];
  total: number;
  pageNo: number;
  loading: boolean;
  error: boolean;
  openingId: string;
  onRetry: () => void;
  onPageChange: (page: number) => void;
  onCreate: () => void;
  onView: (record: BusinessProcessRecord) => void;
  onEdit: (record: BusinessProcessRecord) => void;
  onDelete: (record: BusinessProcessRecord) => void;
}

export function BusinessProcessList({
  filters,
  domainOptions,
  creatorOptions,
  directorOptions,
  domainsLoading,
  domainsError,
  creatorsLoading,
  creatorsError,
  directorsLoading,
  directorsError,
  onRetryDomains,
  onRetryCreators,
  onRetryDirectors,
  onFilterChange,
  onSearch,
  onReset,
  items,
  total,
  pageNo,
  loading,
  error,
  openingId,
  onRetry,
  onPageChange,
  onCreate,
  onView,
  onEdit,
  onDelete,
}: Props) {
  const columns: TableColumns<BusinessProcessRecord> = [
    {
      key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150,
      render: (value, record) => (
        <span>
          {String(value || "-")}
          {isSystemBusinessProcess(record) ? <span className="ml-2 rounded border border-[#91caff] bg-[#e6f4ff] px-1.5 py-0.5 text-[10px] text-[#1677ff]">系统</span> : null}
        </span>
      ),
    },
    { key: "cnName", title: "中文名", dataIndex: "cnName", width: 150 },
    { key: "enName", title: "英文名", dataIndex: "enName", width: 180 },
    { key: "dataDomainCnName", title: "数据域", dataIndex: "dataDomainCnName", width: 140 },
    { key: "director", title: "负责人", dataIndex: "director", width: 120 },
    { key: "remark", title: "备注" },
    {
      key: "actions", title: "操作", width: 200, align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1 whitespace-nowrap">
          <Button size="sm" variant="ghost" className="shrink-0 px-1 text-xs font-normal text-[var(--yak-color-primary)]" onClick={() => onView(record)}>
            <Eye size={12} />查看
          </Button>
          <span className="h-3 w-px shrink-0 bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="shrink-0 px-1 text-xs font-normal text-[var(--yak-color-primary)]"
            isDisabled={Boolean(openingId)} onClick={() => onEdit(record)}>
            {openingId === record.id ? "加载中" : <><SquarePen size={12} />编辑</>}
          </Button>
          {!isSystemBusinessProcess(record) ? (
            <>
              <span className="h-3 w-px shrink-0 bg-[#e4e7ec]" />
              <Button size="sm" variant="ghost" className="shrink-0 px-1 text-xs font-normal text-[#d92d20]" onClick={() => onDelete(record)}>
                <Trash2 size={12} />删除
              </Button>
            </>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
        <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">业务过程</h1>
        <p className="mb-0 mt-2 text-xs leading-5 text-[#626875]">
          业务过程指企业的业务活动事件，是对业务系统和业务事实进行的抽象，企业可以结合SOP设计，订单、支付、收货、退货等。
        </p>
      </header>
      <main className="min-h-0 flex-1 overflow-y-auto p-4">
        <BusinessProcessFiltersPanel
          filters={filters}
          domainOptions={domainOptions}
          creatorOptions={creatorOptions}
          directorOptions={directorOptions}
          domainsLoading={domainsLoading}
          domainsError={domainsError}
          creatorsLoading={creatorsLoading}
          creatorsError={creatorsError}
          directorsLoading={directorsLoading}
          directorsError={directorsError}
          onRetryDomains={onRetryDomains}
          onRetryCreators={onRetryCreators}
          onRetryDirectors={onRetryDirectors}
          onChange={onFilterChange}
          onSearch={onSearch}
          onReset={onReset}
        />
        <section className="bg-white px-4 pb-4">
          <Button size="sm" variant="primary" className="mb-3" onClick={onCreate}><Plus size={14} />新建业务过程</Button>
          {error ? (
            <div className="flex min-h-32 flex-col items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
              <span>业务过程加载失败</span><Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
            </div>
          ) : (
            <Table<BusinessProcessRecord>
              columns={columns} dataSource={items}
              rowKey={(record) => record.id || JSON.stringify(record)}
              loading={loading} bordered size="small" emptyText="暂无业务过程"
              pagination={{ current: pageNo, pageSize: BUSINESS_PROCESS_PAGE_SIZE, total, disabled: loading, onChange: onPageChange }}
            />
          )}
        </section>
      </main>
    </div>
  );
}

export default BusinessProcessList;
