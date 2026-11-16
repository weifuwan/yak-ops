import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, toast } from "@yak-ops/yak-ui";

import { namingDictionaryApi } from "@/service/data-modeling/standards/naming-dictionary";
import { standardsQueryKeys } from "../query-keys";
import { DictionaryListPanel } from "./dictionary-list";
import { DictionaryDetailPanel } from "./dictionary-detail";
import { DictionaryFormDialog } from "./dictionary-form-dialog";
import type { DictionaryFormValues, DictionaryStandardRecord } from "./types";
import { DICTIONARY_PAGE_SIZE } from "./types";

interface Props { projectCode: string; }

export function DictionaryStandardsPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const splitRef = useRef<HTMLDivElement | null>(null);
  const [leftWidth, setLeftWidth] = useState(292);
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
    queryKey: standardsQueryKeys.dictionary(projectCode, pageNo, keyword),
    queryFn: () => namingDictionaryApi.page<DictionaryStandardRecord>(projectCode, {
      pageNo, pageSize: DICTIONARY_PAGE_SIZE, keyword: keyword.trim(),
    }),
    enabled: Boolean(projectCode),
  });

  const detailQuery = useQuery({
    queryKey: standardsQueryKeys.dictionaryDetail(projectCode, selectedId),
    queryFn: () => namingDictionaryApi.getById<DictionaryStandardRecord>(projectCode, selectedId),
    enabled: Boolean(projectCode && selectedId),
  });

  useEffect(() => {
    if (!resizing) return;
    const onMove = (event: PointerEvent) => {
      const rect = splitRef.current?.getBoundingClientRect();
      if (rect) setLeftWidth(Math.min(360, Math.max(260, event.clientX - rect.left)));
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
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.dictionaryPrefix(projectCode) });
    if (selectedId) {
      await queryClient.invalidateQueries({
        queryKey: standardsQueryKeys.dictionaryDetail(projectCode, selectedId),
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
      const record = await namingDictionaryApi.getById<DictionaryStandardRecord>(projectCode, id);
      setEditing(record);
      setFormOpen(true);
    } catch {
      toast.warning("获取词典详情失败，请刷新后重试");
    } finally {
      setOpening(false);
    }
  };

  const save = async (values: DictionaryFormValues) => {
    if (saving) return;
    const payload: Record<string, unknown> = { ...values };
    setSaving(true);
    try {
      if (editing?.id) {
        await namingDictionaryApi.update(projectCode, { ...payload, id: editing.id });
        toast.success("命名词典已更新");
      } else {
        await namingDictionaryApi.create(projectCode, payload);
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
      await namingDictionaryApi.remove(projectCode, pendingDelete.id);
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

  const items = pageQuery.data?.totalList || [];
  const total = pageQuery.data?.total || 0;
  const pageCount = Math.max(1, Math.ceil(total / DICTIONARY_PAGE_SIZE));
  const selectedRecord = detailQuery.data;


  return (
    <div ref={splitRef} className="relative flex h-full min-h-[620px] min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white">
      <DictionaryListPanel
        width={leftWidth} items={items} total={total} pageNo={pageNo} pageCount={pageCount}
        keyword={keyword} selectedId={selectedId} loading={pageQuery.isPending}
        error={pageQuery.isError} refreshing={pageQuery.isFetching}
        onCreate={openCreate} onRefresh={() => void refresh()} onRetry={() => void pageQuery.refetch()}
        onKeywordChange={(value) => { setKeyword(value); setPageNo(1); }}
        onSelect={setSelectedId} onPageChange={setPageNo}
      />
      <div role="separator" aria-orientation="vertical" aria-label="调整命名词典列表宽度"
        className={["relative z-10 w-px shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>
      <DictionaryDetailPanel
        selectedId={selectedId} selectedRecord={selectedRecord}
        loading={detailQuery.isPending} error={detailQuery.isError} opening={opening}
        onRetry={() => void detailQuery.refetch()} onBack={() => setSelectedId("")}
        onEdit={() => void openEdit(selectedId)} onDelete={setPendingDelete}
      />
      <DictionaryFormDialog
        open={formOpen} editing={editing} saving={saving}
        onClose={() => { if (!saving) { setFormOpen(false); setEditing(undefined); } }}
        onSubmit={(values) => void save(values)}
      />
      <Modal open={Boolean(pendingDelete)} centered width={420} title="删除命名词典"
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
        </>}>
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">确定删除“{pendingDelete?.cnName || "当前词条"}”吗？</p>
      </Modal>
    </div>
  );
}

export default DictionaryStandardsPage;
