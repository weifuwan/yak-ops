import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoaderCircle, Plus, RefreshCw, Search } from "lucide-react";
import { Button, Input, Modal, toast } from "@yak-ops/yak-ui";

import { modelingEntityApi } from "../../service";
import DataDomainCard from "./data-domain-card";
import DataDomainDetail from "./data-domain-detail";
import DataDomainFormDialog from "./data-domain-form-dialog";
import type { DataDomainFormValues, DataDomainRecord } from "./types";
import { isSystemRecord } from "./types";

interface Props { projectCode: string; }

function CreateDomainCard({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[182px] cursor-pointer flex-col items-center justify-center gap-2 border border-dashed border-[#c8d0e5] bg-white text-[#9ca1ba] transition-colors hover:border-[var(--yak-color-primary)] hover:bg-[#fafbff] hover:text-[var(--yak-color-primary)]"
      style={{ borderRadius: 6 }}
    >
      <Plus size={20} strokeWidth={1.5} />
      <span className="text-xs">新建域</span>
    </button>
  );
}

export function DataModelingDataDomainPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DataDomainRecord>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<DataDomainRecord>();
  const [deleting, setDeleting] = useState(false);

  const listQueryKey = ["modeling", "data-domain", "list", projectCode];
  const listQuery = useQuery({
    queryKey: listQueryKey,
    queryFn: () => modelingEntityApi.all<DataDomainRecord>(projectCode, "dataDomain"),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: ["modeling", "data-domain", "detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<DataDomainRecord>(projectCode, "dataDomain", selectedId),
    enabled: Boolean(projectCode && selectedId),
  });

  const records = listQuery.data || [];
  const filteredRecords = useMemo(() => {
    const value = keyword.trim().toLocaleLowerCase();
    if (!value) return records;
    return records.filter((record) => [
      record.cnName, record.enShortName, record.enName, record.director, record.reviewer, record.remark,
    ].some((item) => String(item || "").toLocaleLowerCase().includes(value)));
  }, [keyword, records]);

  const openCreate = () => {
    setEditing(undefined);
    setFormOpen(true);
  };

  const openEdit = async (record: DataDomainRecord) => {
    if (!record.id || openingId) return;
    setOpeningId(record.id);
    try {
      const detail = await modelingEntityApi.getById<DataDomainRecord>(projectCode, "dataDomain", record.id);
      setEditing(detail);
      setFormOpen(true);
    } catch {
      toast.warning("获取数据域详情失败，请刷新列表后重试");
    } finally {
      setOpeningId("");
    }
  };

  const save = async (values: DataDomainFormValues) => {
    if (saving) return;
    const payload: Record<string, unknown> = { ...values };
    if (editing?.id) {
      payload.id = editing.id;
      payload.flag = editing.flag;
    }
    setSaving(true);
    try {
      if (editing?.id) {
        await modelingEntityApi.update(projectCode, "dataDomain", payload);
        toast.success("数据域已更新");
      } else {
        await modelingEntityApi.create(projectCode, "dataDomain", payload);
        toast.success("数据域已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey: listQueryKey });
      if (selectedId) {
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-domain", "detail", projectCode, selectedId] });
      }
    } catch {
      toast.warning("保存数据域失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!pendingDelete?.id || deleting || isSystemRecord(pendingDelete)) return;
    setDeleting(true);
    try {
      await modelingEntityApi.remove(projectCode, "dataDomain", pendingDelete.id);
      toast.success("数据域已删除");
      setPendingDelete(undefined);
      if (selectedId === pendingDelete.id) setSelectedId("");
      await queryClient.invalidateQueries({ queryKey: listQueryKey });
    } catch {
      toast.warning("删除数据域失败，请检查该数据域下的业务过程及关联对象");
    } finally {
      setDeleting(false);
    }
  };

  const detail = detailQuery.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      {selectedId ? (
        detailQuery.isPending ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载数据域详情...
          </div>
        ) : detailQuery.isError || !detail ? (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>数据域详情加载失败</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
              <Button size="sm" variant="ghost" onClick={() => setSelectedId("")}>返回列表</Button>
            </div>
          </div>
        ) : (
          <DataDomainDetail
            projectCode={projectCode}
            record={detail}
            onBack={() => setSelectedId("")}
            onEditDomain={(record) => void openEdit(record)}
          />
        )
      ) : (
        <>
          <header className="shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
            <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">数据域</h1>
            <p className="mb-0 mt-2 text-xs leading-5 text-[#626875]">
              数据域是指一个或多个业务过程或者维度的集合，可以结合企业业务划分设计，方便业务快速筛选数据。如交易域、日志域、门店域、教学域等。
            </p>
          </header>
          <main className="min-h-0 flex-1 overflow-y-auto p-5">
            {listQuery.isPending ? (
              <div className="flex min-h-[220px] items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                <LoaderCircle size={15} className="animate-spin" />正在加载数据域...
              </div>
            ) : listQuery.isError ? (
              <div className="flex min-h-[220px] flex-col items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                <span>数据域列表加载失败</span>
                <Button size="sm" variant="secondary" onClick={() => void listQuery.refetch()}>重试</Button>
              </div>
            ) : (
              <>
                <div className="mb-4 flex items-center justify-between gap-3">
                  <div className="relative w-[280px] max-w-full">
                    <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
                    <Input size="small" variant="outlined" value={keyword} className="pl-8"
                      placeholder="搜索数据域名称、英文缩写或负责人"
                      onChange={(event) => setKeyword(event.target.value)} />
                  </div>
                  <Button size="sm" variant="secondary" isDisabled={listQuery.isFetching} onClick={() => void listQuery.refetch()}>
                    <RefreshCw size={14} className={listQuery.isFetching ? "animate-spin" : ""} />刷新
                  </Button>
                </div>
                {filteredRecords.length === 0 && keyword ? (
                  <div className="flex min-h-[220px] items-center justify-center text-xs text-[var(--yak-components-muted-text)]">
                    没有找到匹配的数据域
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-5 min-[576px]:grid-cols-2 min-[992px]:grid-cols-3 min-[1280px]:grid-cols-4 min-[1600px]:grid-cols-5">
                    <CreateDomainCard onClick={openCreate} />
                    {filteredRecords.map((record) => (
                      <DataDomainCard
                        key={record.id}
                        record={record}
                        opening={openingId === record.id}
                        onOpen={(item) => setSelectedId(item.id || "")}
                        onEdit={(item) => void openEdit(item)}
                        onDelete={setPendingDelete}
                      />
                    ))}
                  </div>
                )}
              </>
            )}
          </main>
        </>
      )}

      <DataDomainFormDialog
        open={formOpen}
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
      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除数据域"
        onClose={() => {
          if (!deleting) setPendingDelete(undefined);
        }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
        </>}
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete?.cnName || "当前数据域"}”吗？删除数据域也会同时删除其下的业务过程。
        </p>
      </Modal>
    </div>
  );
}

export default DataModelingDataDomainPage;
