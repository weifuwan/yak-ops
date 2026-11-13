import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Search, Trash2 } from "lucide-react";
import {
  Button, Input, Modal, Table, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { modelingEntityApi } from "../service";
import type { ModelingPageResult } from "../service";
import type { DictionaryStandardRecord } from "./types";

interface Props { projectCode: string; }
interface FormValues { enShortName: string; enName: string; cnName: string; }

const PAGE_SIZE = 10;

function DictionaryFormDialog({
  open, editing, saving, onClose, onSubmit,
}: {
  open: boolean; editing?: DictionaryStandardRecord; saving: boolean;
  onClose: () => void; onSubmit: (values: FormValues) => void;
}) {
  const [values, setValues] = useState<FormValues>({
    enShortName: "", enName: "", cnName: "",
  });
  const [error, setError] = useState("");
  const isSystem = editing?.flag === 1;

  useEffect(() => {
    if (!open) return;
    setValues({
      enShortName: editing?.enShortName || "",
      enName: editing?.enName || "",
      cnName: editing?.cnName || "",
    });
    setError("");
  }, [open, editing]);

  const update = (key: keyof FormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setError("");
  };

  const submit = () => {
    const enShortName = values.enShortName.trim();
    const enName = values.enName.trim();
    const cnName = values.cnName.trim();
    if (!enShortName) return setError("请输入英文缩写");
    if (!enName) return setError("请输入英文名称");
    if (!cnName) return setError("请输入中文名称");
    if (enShortName.length > 64 || enName.length > 64 || cnName.length > 64) {
      return setError("名称长度不能超过 64 个字符");
    }
    onSubmit({ enShortName, enName, cnName });
  };

  return (
    <Modal
      open={open}
      centered
      width={620}
      title={editing ? "编辑命名词典" : "新建命名词典"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} isDisabled={isSystem} onClick={submit}>确定</Button>
      </>}
    >
      <div className="space-y-3">
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
          <label className="pt-1.5 text-xs text-[#475467]">英文缩写 <span className="text-[#d92d20]">*</span></label>
          <Input size="small" variant="outlined" maxLength={64} value={values.enShortName}
            disabled={isSystem} placeholder="请输入英文缩写" onChange={(event) => update("enShortName", event.target.value)} />
        </div>
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
          <label className="pt-1.5 text-xs text-[#475467]">英文名 <span className="text-[#d92d20]">*</span></label>
          <Input size="small" variant="outlined" maxLength={64} value={values.enName}
            disabled={isSystem} placeholder="请输入英文名" onChange={(event) => update("enName", event.target.value)} />
        </div>
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
          <label className="pt-1.5 text-xs text-[#475467]">中文名 <span className="text-[#d92d20]">*</span></label>
          <Input size="small" variant="outlined" maxLength={64} value={values.cnName}
            disabled={isSystem} placeholder="请输入中文名" onChange={(event) => update("cnName", event.target.value)} />
        </div>
        {isSystem ? (
          <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            系统内置词条不可修改
          </p>
        ) : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export function DictionaryStandardsPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DictionaryStandardRecord>();
  const [saving, setSaving] = useState(false);
  const [opening, setOpening] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<DictionaryStandardRecord>();
  const [deleting, setDeleting] = useState(false);

  const pageQuery = useQuery({
    queryKey: ["modeling", "standards", "dictionary", projectCode, pageNo, keyword],
    queryFn: () => modelingEntityApi.page<DictionaryStandardRecord>(projectCode, "dictionaryStandard", {
      pageNo, pageSize: PAGE_SIZE, keyword: keyword.trim(),
    }),
    enabled: Boolean(projectCode),
  });

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "dictionary", projectCode] });
  };

  const openCreate = () => {
    setEditing(undefined);
    setFormOpen(true);
  };

  const openEdit = async (id: string) => {
    if (!id || opening) return;
    setOpening(true);
    try {
      const record = await modelingEntityApi.getById<DictionaryStandardRecord>(projectCode, "dictionaryStandard", id);
      setEditing(record);
      setFormOpen(true);
    } catch {
      toast.warning("获取词典详情失败，请刷新后重试");
    } finally {
      setOpening(false);
    }
  };

  const save = async (values: FormValues) => {
    if (saving) return;
    const payload: Record<string, unknown> = { ...values };
    setSaving(true);
    try {
      if (editing?.id) {
        await modelingEntityApi.update(projectCode, "dictionaryStandard", { ...payload, id: editing.id });
        toast.success("命名词典已更新");
      } else {
        await modelingEntityApi.create(projectCode, "dictionaryStandard", payload);
        toast.success("命名词典已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await refresh();
    } catch {
      toast.warning("保存命名词典失败，请检查必填项后重试");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!pendingDelete?.id || deleting) return;
    setDeleting(true);
    try {
      await modelingEntityApi.remove(projectCode, "dictionaryStandard", pendingDelete.id);
      toast.success("命名词典已删除");
      setPendingDelete(undefined);
      await refresh();
    } catch {
      toast.warning("删除命名词典失败，请重试");
    } finally {
      setDeleting(false);
    }
  };

  const columns: TableColumns<DictionaryStandardRecord> = [
    {
      key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 180,
      render: (value, record) => (
        <div className="flex items-center gap-2">
          <span>{String(value || "-")}</span>
          {record.flag === 1 ? (
            <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">系统</span>
          ) : null}
        </div>
      ),
    },
    { key: "cnName", title: "中文名", dataIndex: "cnName", width: 200 },
    { key: "enName", title: "英文名", dataIndex: "enName", width: 240 },
    { key: "createBy", title: "创建人", dataIndex: "createBy", width: 140 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "updateBy", title: "修改人", dataIndex: "updateBy", width: 140 },
    { key: "updateTime", title: "修改时间", dataIndex: "updateTime", width: 190 },
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" isDisabled={item.flag === 1}
            onClick={() => void openEdit(item.id || "")}><Pencil size={12} />编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]"
            isDisabled={item.flag === 1}
            onClick={() => setPendingDelete(item)}><Trash2 size={12} />删除</Button>
        </div>
      ),
    },
  ];

  const data = pageQuery.data as ModelingPageResult<DictionaryStandardRecord> | undefined;

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]">
      <header className="shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
        <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">命名词典</h1>
        <p className="mb-0 mt-2 text-xs leading-5 text-[#626875]">
          管理业务名词、物理表和字段的词根与词素，统一标准化英文命名
        </p>
      </header>

      <main className="min-h-0 flex-1 overflow-y-auto p-4">
        <section className="rounded-md border border-[#e7e9ee] bg-white p-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Input size="small" variant="outlined" value={keyword} placeholder="请输入中文名、英文名或英文缩写"
                onChange={(event) => { setKeyword(event.target.value); setPageNo(1); }} />
              <Button size="sm" variant="ghost" isIconOnly aria-label="搜索" onClick={() => void pageQuery.refetch()}>
                <Search size={14} />
              </Button>
            </div>
            <Button size="sm" variant="primary" onClick={openCreate}><Plus size={13} />新建</Button>
          </div>
          <Table<DictionaryStandardRecord>
            columns={columns}
            dataSource={data?.bizData || []}
            rowKey={(item) => item.id || ""}
            loading={pageQuery.isPending || pageQuery.isFetching || opening}
            bordered size="small" emptyText="暂无命名词典"
            pagination={(data?.pagination.total || 0) > 0 ? {
              current: pageNo, pageSize: PAGE_SIZE, total: data?.pagination.total || 0,
              disabled: pageQuery.isFetching, onChange: setPageNo,
            } : false}
          />
        </section>
      </main>

      <DictionaryFormDialog
        open={formOpen}
        editing={editing}
        saving={saving}
        onClose={() => { if (!saving) { setFormOpen(false); setEditing(undefined); } }}
        onSubmit={(values) => void save(values)}
      />
      <Modal
        open={Boolean(pendingDelete)} centered width={420} title="删除命名词典"
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
        </>}
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete?.cnName || "当前词条"}”吗？
        </p>
      </Modal>
    </div>
  );
}

export default DictionaryStandardsPage;
