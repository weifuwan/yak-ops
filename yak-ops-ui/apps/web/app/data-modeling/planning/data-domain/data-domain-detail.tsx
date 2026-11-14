import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Eye, Plus, SquarePen, Trash2 } from "lucide-react";
import { Button, Modal, Table, toast, type TableColumns } from "@yak-ops/yak-ui";

import { PlanningIcon } from "../icons";

import { modelingEntityApi, modelingQueryKeys } from "../../service";
import BusinessProcessFormDialog from "./business-process-form-dialog";
import BusinessProcessViewDialog from "./business-process-view-dialog";
import type { BusinessProcessFormValues, BusinessProcessRecord, DataDomainRecord } from "./types";
import { isSystemBusinessProcess } from "./types";

interface Props {
  projectCode: string;
  record: DataDomainRecord;
  onBack: () => void;
  onEditDomain: (record: DataDomainRecord) => void;
}

function SectionTitle({ children }: { children: string }) {
  return (
    <div className="flex items-center gap-2 text-sm font-semibold text-[#20232b]">
      <span className="h-4 w-1 rounded-sm bg-[#5664ff]" />
      {children}
    </div>
  );
}

function DescriptionItem({
  label, value, className = "", multiline = false,
}: {
  label: string; value?: string; className?: string; multiline?: boolean;
}) {
  return (
    <div className={"flex min-w-0 items-baseline " + className}>
      <span className="shrink-0 text-xs leading-[22px] text-[rgba(128,128,128,1)]">{label}：</span>
      <span className={"min-w-0 pl-3 text-xs leading-[22px] text-black " + (multiline ? "whitespace-pre-wrap break-words" : "break-words")}>
        {value || "-"}
      </span>
    </div>
  );
}

function BasicInfoSection({ record, onEdit }: { record: DataDomainRecord; onEdit: () => void }) {
  return (
    <section className="bg-white pb-2">
      <div className="flex items-center justify-between p-4">
        <SectionTitle>基本信息</SectionTitle>
        <Button size="sm" variant="ghost" className="h-6 min-w-8 px-0 text-xs text-[#1677ff] hover:bg-transparent" onClick={onEdit}>
          编辑
        </Button>
      </div>
      <div className="mb-1 grid grid-cols-1 gap-x-8 gap-y-2 px-6 pb-2 sm:grid-cols-2 xl:grid-cols-4">
        <DescriptionItem label="英文缩写" value={record.enShortName} />
        <DescriptionItem label="英文名称" value={record.enName} />
        <DescriptionItem label="中文名称" value={record.cnName} />
        <DescriptionItem label="负责人" value={record.director} />
        <DescriptionItem label="备注" value={record.remark} className="col-span-full" multiline />
        <DescriptionItem label="创建人" value={record.createBy} />
        <DescriptionItem label="创建时间" value={record.createTime} className="sm:col-span-1 xl:col-span-3" />
        <DescriptionItem label="修改人" value={record.updateBy} />
        <DescriptionItem label="修改时间" value={record.updateTime} className="sm:col-span-1 xl:col-span-3" />
        <DescriptionItem label="模型评审人" value={record.reviewer} className="col-span-full" />
      </div>
    </section>
  );
}

function BusinessProcessSection({ projectCode, domain }: { projectCode: string; domain: DataDomainRecord }) {
  const queryClient = useQueryClient();
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 10;
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<BusinessProcessRecord>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<BusinessProcessRecord>();
  const [deleting, setDeleting] = useState(false);
  const [viewing, setViewing] = useState<BusinessProcessRecord>();
  const queryKey = ["modeling", "data-domain", "business-processes", projectCode, domain.id, pageNo, pageSize];

  const processQuery = useQuery({
    queryKey,
    queryFn: () => modelingEntityApi.page<BusinessProcessRecord>(projectCode, "businessProcess", {
      dataDomainId: domain.id,
      pageNo,
      pageSize,
    }),
    enabled: Boolean(projectCode && domain.id),
  });

  useEffect(() => setPageNo(1), [domain.id]);

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

  const save = async (values: BusinessProcessFormValues) => {
    if (saving) return;
    setSaving(true);
    const payload: Record<string, unknown> = {
      ...values,
      dataDomainId: domain.id,
    };
    if (editing?.id) {
      payload.id = editing.id;
      payload.flag = isSystemBusinessProcess(editing) ? 1 : Number(editing.flag ?? 0);
    }
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
        queryClient.invalidateQueries({ queryKey: [...modelingQueryKeys.dataDomainBusinessProcesses(projectCode), domain.id] }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessPage(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessFilterOptions(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessAll(projectCode) }),
      ]);
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
        queryClient.invalidateQueries({ queryKey: [...modelingQueryKeys.dataDomainBusinessProcesses(projectCode), domain.id] }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessPage(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessFilterOptions(projectCode) }),
        queryClient.invalidateQueries({ queryKey: modelingQueryKeys.businessProcessAll(projectCode) }),
      ]);
    } catch {
      toast.warning("删除业务过程失败，请确认该业务过程没有被指标等对象引用");
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
      key: "actions", title: "操作", width: 150, align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1 whitespace-nowrap">
          <Button size="sm" variant="ghost" className="px-1 text-xs font-normal text-[var(--yak-color-primary)]" onClick={() => setViewing(record)}>
            <Eye size={12} />查看
          </Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs font-normal text-[var(--yak-color-primary)]" isDisabled={Boolean(openingId)} onClick={() => void openEdit(record)}>
            {openingId === record.id ? "加载中" : <><SquarePen size={12} />编辑</>}
          </Button>
          {!isSystemBusinessProcess(record) ? (
            <>
              <span className="h-3 w-px bg-[#e4e7ec]" />
              <Button size="sm" variant="ghost" className="px-1 text-xs font-normal text-[#d92d20]" onClick={() => setPendingDelete(record)}>
                <Trash2 size={12} />删除
              </Button>
            </>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <>
      <section className="mt-4 bg-white pb-4 pt-4">
        <div className="px-4 pb-3"><SectionTitle>业务过程管理</SectionTitle></div>
        <div className="px-4">
          <Button size="sm" variant="primary" className="mb-3" onClick={openCreate}>
            <Plus size={14} />新增业务过程
          </Button>
          {processQuery.isError ? (
            <div className="flex min-h-32 flex-col items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
              <span>业务过程加载失败</span>
              <Button size="sm" variant="secondary" onClick={() => void processQuery.refetch()}>重试</Button>
            </div>
          ) : (
            <Table<BusinessProcessRecord>
              columns={columns}
              dataSource={processQuery.data?.totalList || []}
              rowKey={(record) => record.id || JSON.stringify(record)}
              loading={processQuery.isPending || processQuery.isFetching}
              bordered
              size="small"
              emptyText="暂无业务过程"
              pagination={{
                current: pageNo,
                pageSize,
                total: processQuery.data?.total || 0,
                disabled: processQuery.isFetching,
                onChange: (page) => setPageNo(page),
              }}
            />
          )}
        </div>
      </section>

      <BusinessProcessFormDialog
        open={formOpen}
        domain={domain}
        editing={editing}
        saving={saving}
        onClose={() => {
          if (!saving) {
            setFormOpen(false);
            setEditing(undefined);
          }
        }}
        onSubmit={(values) => void save(values)}
      />
      <BusinessProcessViewDialog record={viewing} onClose={() => setViewing(undefined)} />
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
    </>
  );
}

export function DataDomainDetail({ projectCode, record, onBack, onEditDomain }: Props) {
  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      <header className="z-[9] shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
        <nav aria-label="Breadcrumb" className="flex items-center text-xs leading-[17px]">
          <button type="button" onClick={onBack} className="cursor-pointer border-0 bg-transparent p-0 text-[#1677ff] hover:text-[#0958d9]">数据域</button>
          <span className="mx-2 text-[#bfbfbf]">/</span>
          <span className="font-medium text-[#595959]">{record.cnName || "详情"}</span>
        </nav>
        <div className="mt-2 flex items-center gap-2">
          <Button size="sm" variant="outline" isIconOnly aria-label="返回数据域列表" className="h-6 w-[42px] rounded-xl p-0" onClick={onBack}>
            <ArrowLeft size={14} />
          </Button>
          <PlanningIcon name="data-domain" size={20} className="text-[var(--yak-color-primary)]" />
          <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">{record.cnName || "数据域详情"}</h1>
        </div>
      </header>
      <main className="min-h-0 flex-1 overflow-y-auto p-4">
        <BasicInfoSection record={record} onEdit={() => onEditDomain(record)} />
        <BusinessProcessSection projectCode={projectCode} domain={record} />
      </main>
    </div>
  );
}

export default DataDomainDetail;
