import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { Button, toast } from "@yak-ops/yak-ui";

import { businessProcessApi } from "@/service/data-modeling/planning/common-layer/business-process";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { planningQueryKeys } from "../../query-keys";
import type { BusinessProcessRecord, DataDomainRecord } from "../types";
import { isSystemBusinessProcess } from "../types";
import {
  BUSINESS_PROCESS_PAGE_SIZE, EMPTY_FILTERS,
  type BusinessProcessFilters, type BusinessProcessFormValues,
} from "./types";
import { businessProcessQueryKeys } from "./query-keys";
import { BusinessProcessDetail } from "./business-process-detail";
import { BusinessProcessFormDialog } from "./business-process-form-dialog";
import { BusinessProcessList } from "./business-process-list";
import { BusinessProcessDeleteDialog } from "./business-process-delete-dialog";

interface Props { projectCode: string; }

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
    queryKey: [...planningQueryKeys.businessProcessPage(projectCode), appliedFilters, pageNo, BUSINESS_PROCESS_PAGE_SIZE],
    queryFn: () => businessProcessApi.page<BusinessProcessRecord>(projectCode, {
      ...appliedFilters,
      pageNo,
      pageSize: BUSINESS_PROCESS_PAGE_SIZE,
    }),
    enabled: Boolean(projectCode),
  });

  const domainQuery = useQuery({
    queryKey: planningQueryKeys.dataDomainAll(projectCode),
    queryFn: () => dataDomainApi.all<DataDomainRecord>(projectCode),
    enabled: Boolean(projectCode),
  });

  const processOptionsQuery = useQuery({
    queryKey: planningQueryKeys.businessProcessFilterOptions(projectCode),
    queryFn: () => businessProcessApi.all<BusinessProcessRecord>(projectCode),
    enabled: Boolean(projectCode),
  });

  const detailQuery = useQuery({
    queryKey: businessProcessQueryKeys.detail(projectCode, selectedId),
    queryFn: () => businessProcessApi.getById<BusinessProcessRecord>(projectCode, selectedId),
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
      const detail = await businessProcessApi.getById<BusinessProcessRecord>(projectCode, record.id);
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
        await businessProcessApi.update(projectCode, payload);
        toast.success("业务过程已更新");
      } else {
        await businessProcessApi.create(projectCode, payload);
        setPageNo(1);
        toast.success("业务过程已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessProcessPage(projectCode) }),
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessProcessFilterOptions(projectCode) }),
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessProcessAll(projectCode) }),
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataDomainBusinessProcesses(projectCode) }),
      ]);
      if (selectedId) {
        await queryClient.invalidateQueries({
          queryKey: businessProcessQueryKeys.detail(projectCode, selectedId),
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
      await businessProcessApi.remove(projectCode, pendingDelete.id);
      toast.success("业务过程已删除");
      setPendingDelete(undefined);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessProcessPage(projectCode) }),
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessProcessFilterOptions(projectCode) }),
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessProcessAll(projectCode) }),
        queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataDomainBusinessProcesses(projectCode) }),
      ]);
    } catch {
      toast.warning("删除业务过程失败，请确认该业务过程没有被其他建模对象引用");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      {selectedId ? (
        detailQuery.isPending ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载业务过程详情...
          </div>
        ) : detailQuery.isError || !detailQuery.data ? (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>业务过程详情加载失败</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
              <Button size="sm" variant="ghost" onClick={() => setSelectedId("")}>返回列表</Button>
            </div>
          </div>
        ) : (
          <BusinessProcessDetail record={detailQuery.data}
            onBack={() => setSelectedId("")} onEdit={(record) => void openEdit(record)} />
        )
      ) : (
        <BusinessProcessList
          filters={filters} domainOptions={domainOptions} creatorOptions={creatorOptions} directorOptions={directorOptions}
          domainsLoading={domainQuery.isPending} domainsError={domainQuery.isError}
          processOptionsLoading={processOptionsQuery.isPending}
          onFilterChange={updateFilter} onSearch={search} onReset={reset}
          items={listQuery.data?.totalList || []} total={listQuery.data?.total || 0}
          pageNo={pageNo} loading={listQuery.isPending || listQuery.isFetching} error={listQuery.isError}
          openingId={openingId} onRetry={() => void listQuery.refetch()} onPageChange={setPageNo}
          onCreate={openCreate} onView={(record) => setSelectedId(record.id || "")}
          onEdit={(record) => void openEdit(record)} onDelete={setPendingDelete}
        />
      )}

      <BusinessProcessFormDialog
        open={formOpen} editing={editing} domains={domains}
        domainsLoading={domainQuery.isPending} domainsError={domainQuery.isError}
        onRetryDomains={() => void domainQuery.refetch()} saving={saving}
        onClose={closeForm} onSubmit={(values) => void save(values)}
      />
      <BusinessProcessDeleteDialog record={pendingDelete} deleting={deleting}
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        onConfirm={() => void remove()} />
    </div>
  );
}

export default DataModelingBusinessProcessPage;
