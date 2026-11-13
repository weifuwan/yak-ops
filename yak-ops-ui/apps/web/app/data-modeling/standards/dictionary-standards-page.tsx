import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen, ChevronLeft, ChevronRight, FileText, LoaderCircle, Pencil, PlusSquare, RefreshCw, Search, Trash2, X } from "lucide-react";
import {
  Button, Input, Modal, toast,
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
  const splitRef = useRef<HTMLDivElement | null>(null);
  const [leftWidth, setLeftWidth] = useState(280);
  const [resizing, setResizing] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [selectedId, setSelectedId] = useState("");
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

  const detailQuery = useQuery({
    queryKey: ["modeling", "standards", "dictionary-detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<DictionaryStandardRecord>(projectCode, "dictionaryStandard", selectedId),
    enabled: Boolean(projectCode && selectedId),
  });

  useEffect(() => {
    if (!resizing) return;
    const onMove = (event: PointerEvent) => {
      const rect = splitRef.current?.getBoundingClientRect();
      if (rect) setLeftWidth(Math.min(480, Math.max(220, event.clientX - rect.left)));
    };
    const onUp = () => setResizing(false);
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
  }, [resizing]);

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "dictionary", projectCode] });
    if (selectedId) {
      await queryClient.invalidateQueries({
        queryKey: ["modeling", "standards", "dictionary-detail", projectCode, selectedId],
      });
    }
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
      if (selectedId === pendingDelete.id) setSelectedId("");
      setPendingDelete(undefined);
      await refresh();
    } catch {
      toast.warning("删除命名词典失败，请重试");
    } finally {
      setDeleting(false);
    }
  };

  const data = pageQuery.data as ModelingPageResult<DictionaryStandardRecord> | undefined;
  const items = data?.bizData || [];
  const total = data?.pagination.total || 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const selectedRecord = detailQuery.data;

  return (
    <div ref={splitRef}
      className="relative flex h-full min-h-[620px] min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white">
      <aside className="flex min-h-0 shrink-0 flex-col overflow-hidden bg-white" style={{ width: leftWidth }}>
        <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
          <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">命名词典</span>
          <div className="flex items-center gap-1">
            <button type="button" title="新建命名词典"
              className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
              onClick={openCreate}>
              <PlusSquare size={16} />
            </button>
            <button type="button" title="刷新"
              className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
              onClick={() => void refresh()}>
              <RefreshCw size={15} className={pageQuery.isFetching ? "animate-spin" : ""} />
            </button>
          </div>
        </div>

        <div className="shrink-0 px-3 py-2">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
            <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8" placeholder="搜索"
              onValueChange={(value) => { setKeyword(String(value)); setPageNo(1); }} />
            {keyword ? (
              <button type="button" aria-label="清空搜索"
                className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]"
                onClick={() => { setKeyword(""); setPageNo(1); }}>
                <X size={13} />
              </button>
            ) : null}
          </div>
        </div>

        <div role="tree" className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
          {pageQuery.isPending ? (
            <div className="flex items-center justify-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
              <LoaderCircle size={14} className="animate-spin" />正在加载命名词典...
            </div>
          ) : pageQuery.isError ? (
            <div className="flex flex-col items-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
              <span>命名词典加载失败</span>
              <Button size="sm" variant="secondary" onClick={() => void pageQuery.refetch()}>重试</Button>
            </div>
          ) : items.length ? (
            <div className="space-y-0.5">
              {items.map((item) => {
                const selected = selectedId === item.id;
                return (
                  <div key={item.id} role="treeitem" aria-selected={selected}
                    onClick={() => item.id && setSelectedId(item.id)}
                    className={[
                      "flex min-h-8 cursor-pointer items-center gap-2 rounded-md px-2.5 pr-2 text-[13px] transition-colors",
                      selected
                        ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
                        : "text-[#3a3d51] hover:bg-[var(--yak-color-hover)]",
                    ].join(" ")}>
                    <FileText size={14} className="shrink-0 text-[#8b929e]" />
                    <span className="min-w-0 flex-1 truncate">{item.cnName || item.enShortName || item.enName || "未命名词条"}</span>
                    {item.flag === 1 ? (
                      <span className="shrink-0 rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">系统</span>
                    ) : null}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
              {keyword ? "没有匹配的命名词典" : "暂无命名词典，点击上方按钮新建"}
            </div>
          )}
        </div>

        {total > 0 ? (
          <div className="flex h-10 shrink-0 items-center justify-between border-t border-[var(--yak-components-panel-border)] px-3">
            <span className="text-[11px] text-[var(--yak-components-muted-text)]">共 {total} 项</span>
            {total > PAGE_SIZE ? (
              <div className="flex items-center gap-1">
                <button type="button" aria-label="上一页" disabled={pageNo <= 1 || pageQuery.isFetching}
                  className="flex size-6 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
                  onClick={() => setPageNo((page) => Math.max(1, page - 1))}>
                  <ChevronLeft size={14} />
                </button>
                <span className="text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">{pageNo}/{pageCount}</span>
                <button type="button" aria-label="下一页" disabled={pageNo >= pageCount || pageQuery.isFetching}
                  className="flex size-6 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
                  onClick={() => setPageNo((page) => Math.min(pageCount, page + 1))}>
                  <ChevronRight size={14} />
                </button>
              </div>
            ) : null}
          </div>
        ) : null}
      </aside>

      <div role="separator" aria-orientation="vertical" aria-label="调整命名词典列表宽度"
        className={["relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
        {!selectedId ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
            <div className="flex flex-col items-center gap-2 text-center">
              <BookOpen size={32} strokeWidth={1.3} className="text-[#c5cbd3]" />
              <span className="text-sm text-[var(--yak-components-muted-text)]">请选择左侧命名词典查看详情</span>
            </div>
          </div>
        ) : detailQuery.isPending ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载命名词典详情...
          </div>
        ) : detailQuery.isError || !selectedRecord ? (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>命名词典详情加载失败</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
              <Button size="sm" variant="ghost" onClick={() => setSelectedId("")}>返回列表</Button>
            </div>
          </div>
        ) : (
          <>
            <header className="flex h-[72px] shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] bg-white px-5">
              <div className="flex min-w-0 items-center gap-2">
                <BookOpen size={20} className="shrink-0 text-[var(--yak-color-primary)]" />
                <div className="min-w-0">
                  <h1 className="truncate text-base font-semibold text-[var(--yak-components-panel-text)]">{selectedRecord.cnName || selectedRecord.enShortName || "命名词典"}</h1>
                  <p className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
                    {selectedRecord.enShortName || "-"} · {selectedRecord.enName || "-"}
                  </p>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                {selectedRecord.flag === 1 ? (
                  <span className="mr-1 rounded bg-[#f2f4f7] px-2 py-1 text-[10px] text-[#667085]">系统内置</span>
                ) : null}
                <Button size="sm" variant="ghost" className="text-xs" isDisabled={selectedRecord.flag === 1 || opening}
                  onClick={() => void openEdit(selectedId)}>
                  <Pencil size={13} />编辑
                </Button>
                <Button size="sm" variant="ghost" className="text-xs text-[#d92d20]" isDisabled={selectedRecord.flag === 1}
                  onClick={() => setPendingDelete(selectedRecord)}>
                  <Trash2 size={13} />删除
                </Button>
              </div>
            </header>

            <div className="min-h-0 flex-1 overflow-y-auto bg-[#f6f6f6] p-4">
              <section className="rounded-md border border-[#e7e9ee] bg-white p-4">
                <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]">
                  <span className="h-4 w-1 rounded-sm bg-[var(--yak-color-primary)]" />基本信息
                </div>
                <div className="grid grid-cols-1 gap-x-8 gap-y-3 px-2 sm:grid-cols-2 xl:grid-cols-3">
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">英文缩写：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.enShortName || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">英文名称：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.enName || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">中文名称：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.cnName || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">创建人：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.createBy || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">创建时间：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.createTime || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">修改人：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.updateBy || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">修改时间：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.updateTime || "-"}</span>
                  </div>
                  <div className="flex min-w-0 items-baseline gap-2 text-xs">
                    <span className="shrink-0 text-[var(--yak-components-muted-text)]">类型：</span>
                    <span className="min-w-0 break-words text-[var(--yak-components-panel-text)]">{selectedRecord.flag === 1 ? "系统内置" : "自定义"}</span>
                  </div>
                </div>
              </section>
            </div>
          </>
        )}
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
