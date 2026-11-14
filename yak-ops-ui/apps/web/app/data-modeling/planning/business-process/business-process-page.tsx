import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, LoaderCircle, Plus, SquarePen, Trash2 } from "lucide-react";
import {
  Button, Input, Modal, Select, SelectContent, SelectItem, SelectItemText,
  SelectTrigger, SelectValue, Table, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { modelingEntityApi, modelingQueryKeys } from "../../service";
import type { DataDomainRecord } from "../data-domain/types";
import { withSelectedOption } from "../select-options";
import { isSystemBusinessProcess } from "../data-domain/types";
import BusinessProcessDetail from "./business-process-detail";
import BusinessProcessFormDialog from "./business-process-form-dialog";
import {
  EMPTY_FILTERS, type BusinessProcessFilters, type BusinessProcessFormValues,
  type BusinessProcessRecord,
} from "./types";

interface Props {
  projectCode: string;
}

const PAGE_SIZE = 10;
const FIELD_CLASS = "grid grid-cols-[64px_minmax(0,1fr)] items-center gap-2";

function FilterSelect({
  value, placeholder, options, disabled, fallbackLabel, onChange,
}: {
  value: string;
  placeholder: string;
  options: Array<{ value: string; label: string }>;
  disabled?: boolean;
  fallbackLabel?: string;
  onChange: (value: string) => void;
}) {
  const selectItems = [
    { value: "__all__", label: "不限" },
    ...withSelectedOption(options, value, fallbackLabel || value),
  ];
  return (
    <Select
      items={selectItems}
      value={value || undefined}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(nextValue === "__all__" ? "" : String(nextValue || ""))}
    >
      <SelectTrigger size="small" variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="__all__"><SelectItemText>不限</SelectItemText></SelectItem>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function DataModelingBusinessProcessPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<BusinessProcessFilters>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<Partial<BusinessProcessFilters>>({});
  const [pageNo, setPageNo] = useState(1);
  const [selectedId, setSelectedId] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessProcessRecord>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<BusinessProcessRecord>();
  const [deleting, setDeleting] = useState(false);

  const listQuery = useQuery({
    queryKey: [...modelingQueryKeys.businessProcessPage(projectCode), appliedFilters, pageNo, PAGE_SIZE],
    queryFn: () => modelingEntityApi.page<BusinessProcessRecord>(projectCode, "businessProcess", {
      ...appliedFilters,
      pageNo,
      pageSize: PAGE_SIZE,
    }),
    enabled: Boolean(projectCode),
  });

  const domainQuery = useQuery({
    queryKey: modelingQueryKeys.dataDomainAll(projectCode),
    queryFn: () => modelingEntityApi.all<DataDomainRecord>(projectCode, "dataDomain"),
    enabled: Boolean(projectCode),
  });

  const processOptionsQuery = useQuery({
    queryKey: modelingQueryKeys.businessProcessFilterOptions(projectCode),
    queryFn: () => modelingEntityApi.all<BusinessProcessRecord>(projectCode, "businessProcess"),
    enabled: Boolean(projectCode),
  });

  const detailQuery = useQuery({
    queryKey: ["modeling", "business-process", "detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<BusinessProcessRecord>(projectCode, "businessProcess", selectedId),
    enabled: Boolean(projectCode && selectedId),
  });

  const domains = domainQuery.data || [];
  const domainOptions = useMemo(() => domains
    .filter((item) => item.id)
    .map((item) => ({ value: item.id || "", label: item.cnName || item.enShortName || "未命名数据域" })),
  [domains]);

  const processOptions = processOptionsQuery.data || [];
  const creatorOptions = useMemo(() => Array.from(new Set(
    processOptions.map((item) => String(item.createBy || "").trim()).filter(Boolean),
  )).sort((a, b) => a.localeCompare(b)), [processOptions]);
  const directorOptions = useMemo(() => Array.from(new Set(
    processOptions.map((item) => String(item.director || "").trim()).filter(Boolean),
  )).sort((a, b) => a.localeCompare(b)), [processOptions]);

  const updateFilter = (key: keyof BusinessProcessFilters, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const search = () => {
    const nextFilters: Partial<BusinessProcessFilters> = {};
    (Object.keys(filters) as Array<keyof BusinessProcessFilters>).forEach((key) => {
      const value = filters[key].trim();
      if (value) nextFilters[key] = value;
    });
    setPageNo(1);
    setAppliedFilters(nextFilters);
  };

  const reset = () => {
    setFilters(EMPTY_FILTERS);
    setAppliedFilters({});
    setPageNo(1);
  };

  const openCreate = () => {
    setEditing(undefined);
    setFormOpen(true);
  };

  const openEdit = async (record: BusinessProcessRecord) => {
    if (!record.id || openingId) return;
    setOpeningId(record.id);
    try {
      const detail = await modelingEntityApi.getById<BusinessProcessRecord>(projectCode, "businessProcess", record.id);
      setEditing(detail);
      setFormOpen(true);
    } catch {
      toast.warning("获取业务过程详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
  };

  const closeForm = () => {
    if (saving) return;
    setFormOpen(false);
    setEditing(undefined);
  };

  const save = async (values: BusinessProcessFormValues) => {
    if (saving) return;
    const payload: Record<string, unknown> = { ...values };
    if (editing?.id) {
      payload.id = editing.id;
      payload.flag = isSystemBusinessProcess(editing) ? 1 : Number(editing.flag ?? 0);
    }
    setSaving(true);
    try {
      if (editing?.id) {
        await modelingEntityApi.update(projectCode, "businessProcess", payload);
        toast.success("业务过程已更新");
      } else {
        await modelingEntityApi.create(projectCode, "businessProcess", payload);
        setPageNo(1);
        toast.success("业务过程已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessPage(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessFilterOptions(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessAll(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.dataDomainBusinessProcesses(projectCode) }),
      ]);
      if (selectedId) {
        await queryClient.invalidateQueries({
          queryKey: ["modeling", "business-process", "detail", projectCode, selectedId],
        });
      }
    } catch {
      toast.warning("保存业务过程失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!pendingDelete?.id || deleting || isSystemBusinessProcess(pendingDelete)) return;
    setDeleting(true);
    try {
      await modelingEntityApi.remove(projectCode, "businessProcess", pendingDelete.id);
      toast.success("业务过程已删除");
      setPendingDelete(undefined);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessPage(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessFilterOptions(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessAll(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.dataDomainBusinessProcesses(projectCode) }),
      ]);
    } catch {
      toast.warning("删除业务过程失败，请确认该业务过程没有被其他建模对象引用");
    } finally {
      setDeleting(false);
    }
  };

  const columns: TableColumns<BusinessProcessRecord> = [
    {
      key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150,
      render: (value, record) => (
        <span>
          {String(value || "-")}
          {isSystemBusinessProcess(record) ? (
            <span className="ml-2 rounded border border-[#91caff] bg-[#e6f4ff] px-1.5 py-0.5 text-[10px] text-[#1677ff]">系统</span>
          ) : null}
        </span>
      ),
    },
    { key: "cnName", title: "中文名", dataIndex: "cnName", width: 150 },
    { key: "enName", title: "英文名", dataIndex: "enName", width: 180 },
    { key: "dataDomainCnName", title: "数据域", dataIndex: "dataDomainCnName", width: 140 },
    { key: "director", title: "负责人", dataIndex: "director", width: 120 },
    { key: "remark", title: "备注" },
    {
      key: "actions", title: "操作", width: 150, align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1 whitespace-nowrap">
          <Button size="sm" variant="ghost" className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
            onClick={() => setSelectedId(record.id || "")}>
            <Eye size={12} />查看
          </Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
            isDisabled={Boolean(openingId)} onClick={() => void openEdit(record)}>
            {openingId === record.id ? "加载中" : <><SquarePen size={12} />编辑</>}
          </Button>
          {!isSystemBusinessProcess(record) ? (
            <>
              <span className="h-3 w-px bg-[#e4e7ec]" />
              <Button size="sm" variant="ghost" className="px-1 text-xs font-normal text-[#d92d20]"
                onClick={() => setPendingDelete(record)}>
                <Trash2 size={12} />删除
              </Button>
            </>
          ) : null}
        </div>
      ),
    },
  ];

  const detail = detailQuery.data;

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      {selectedId ? (
        detailQuery.isPending ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载业务过程详情...
          </div>
        ) : detailQuery.isError || !detail ? (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>业务过程详情加载失败</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
              <Button size="sm" variant="ghost" onClick={() => setSelectedId("")}>返回列表</Button>
            </div>
          </div>
        ) : (
          <BusinessProcessDetail
            record={detail}
            onBack={() => setSelectedId("")}
            onEdit={(record) => void openEdit(record)}
          />
        )
      ) : (
        <>
          <header className="shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
            <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">业务过程</h1>
            <p className="mb-0 mt-2 text-xs leading-5 text-[#626875]">
              业务过程指企业的业务活动事件，是对业务系统和业务事实进行的抽象，企业可以结合SOP设计，订单、支付、收货、退货等。
            </p>
          </header>

          <main className="min-h-0 flex-1 overflow-y-auto p-4">
            <section className="bg-white pb-4 pt-4">
              <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
                <div className="space-y-2">
                  <label className={FIELD_CLASS}>
                    <span className="text-right text-xs text-[var(--yak-components-muted-text)]">英文缩写：</span>
                    <Input size="small" variant="outlined" value={filters.enShortName}
                      onChange={(event) => updateFilter("enShortName", event.target.value)} />
                  </label>
                  <label className={FIELD_CLASS}>
                    <span className="text-right text-xs text-[var(--yak-components-muted-text)]">英文名：</span>
                    <Input size="small" variant="outlined" value={filters.enName}
                      onChange={(event) => updateFilter("enName", event.target.value)} />
                  </label>
                  <label className={FIELD_CLASS}>
                    <span className="text-right text-xs text-[var(--yak-components-muted-text)]">中文名：</span>
                    <Input size="small" variant="outlined" value={filters.cnName}
                      onChange={(event) => updateFilter("cnName", event.target.value)} />
                  </label>
                </div>

                <div className="space-y-2">
                  <label className={FIELD_CLASS}>
                    <span className="text-right text-xs text-[var(--yak-components-muted-text)]">数据域：</span>
                    <FilterSelect value={filters.dataDomainId} placeholder="请选择"
                      options={domainOptions} disabled={domainQuery.isPending || domainQuery.isError}
                      fallbackLabel="已选数据域（名称暂不可用）"
                      onChange={(value) => updateFilter("dataDomainId", value)} />
                  </label>
                  <label className={FIELD_CLASS}>
                    <span className="text-right text-xs text-[var(--yak-components-muted-text)]">创建人：</span>
                    <FilterSelect value={filters.createBy} placeholder="请选择"
                      options={creatorOptions.map((value) => ({ value, label: value }))}
                      disabled={processOptionsQuery.isPending}
                      onChange={(value) => updateFilter("createBy", value)} />
                  </label>
                  <label className={FIELD_CLASS}>
                    <span className="text-right text-xs text-[var(--yak-components-muted-text)]">负责人：</span>
                    <FilterSelect value={filters.director} placeholder="请选择"
                      options={directorOptions.map((value) => ({ value, label: value }))}
                      disabled={processOptionsQuery.isPending}
                      onChange={(value) => updateFilter("director", value)} />
                  </label>
                </div>

                <div className="flex items-start gap-2 pt-0.5">
                  <Button size="sm" variant="primary" onClick={search}>搜索</Button>
                  <Button size="sm" variant="secondary" onClick={reset}>重置</Button>
                </div>
              </div>

              <div className="mt-4 px-4">
                <Button size="sm" variant="primary" className="mb-3" onClick={openCreate}>
                  <Plus size={14} />新建业务过程
                </Button>
              </div>

              {listQuery.isError ? (
                <div className="mx-4 flex min-h-32 flex-col items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                  <span>业务过程加载失败</span>
                  <Button size="sm" variant="secondary" onClick={() => void listQuery.refetch()}>重试</Button>
                </div>
              ) : (
                <div className="px-4">
                  <Table<BusinessProcessRecord>
                    columns={columns}
                    dataSource={listQuery.data?.totalList || []}
                    rowKey={(record) => record.id || JSON.stringify(record)}
                    loading={listQuery.isPending || listQuery.isFetching}
                    bordered
                    size="small"
                    emptyText="暂无业务过程"
                    pagination={{
                      current: pageNo,
                      pageSize: PAGE_SIZE,
                      total: listQuery.data?.total || 0,
                      disabled: listQuery.isFetching,
                      onChange: (page) => setPageNo(page),
                    }}
                  />
                </div>
              )}
            </section>
          </main>
        </>
      )}

      <BusinessProcessFormDialog
        open={formOpen}
        editing={editing}
        domains={domains}
        domainsLoading={domainQuery.isPending}
        domainsError={domainQuery.isError}
        onRetryDomains={() => void domainQuery.refetch()}
        saving={saving}
        onClose={closeForm}
        onSubmit={(values) => void save(values)}
      />

      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除业务过程"
        onClose={() => {
          if (!deleting) setPendingDelete(undefined);
        }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
        </>}
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete?.cnName || "当前业务过程"}”吗？删除后不可恢复。
        </p>
      </Modal>
    </div>
  );
}

export default DataModelingBusinessProcessPage;
