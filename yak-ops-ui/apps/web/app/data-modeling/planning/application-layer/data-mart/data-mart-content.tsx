import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { Button, Modal, toast } from "@yak-ops/yak-ui";

import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { planningQueryKeys } from "../../query-keys";
import { PlanningIcon } from "../../components/icons";
import { DataMartTree, getExpandableKeys } from "./data-mart-tree";
import { DataMartDetail } from "./data-mart-detail";
import { DataMartFormDialog } from "./data-mart-form-dialog";
import { SubjectDomainFormDialog } from "../components/subject-domain-form-dialog";
import type { BusinessTypeOption, DataMartFormValues, DataMartOption, DataMartRecord } from "./types";
import type { SubjectDomainFormValues, SubjectDomainRecord } from "../subject-domain/types";

interface Props { projectCode: string; }
type PendingDelete =
  | { kind: "mart"; record: DataMartRecord }
  | { kind: "subjectDomain"; record: SubjectDomainRecord };

export function DataModelingDataMartPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const splitRef = useRef<HTMLDivElement | null>(null);
  const [leftWidth, setLeftWidth] = useState(280);
  const [resizing, setResizing] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DataMartRecord>();
  const [formParentId, setFormParentId] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<PendingDelete>();
  const [deleting, setDeleting] = useState(false);
  const [subjectFormOpen, setSubjectFormOpen] = useState(false);
  const [editingSubject, setEditingSubject] = useState<SubjectDomainRecord>();
  const [savingSubject, setSavingSubject] = useState(false);

  const treeQuery = useQuery({
    queryKey: planningQueryKeys.dataMartTree(projectCode, keyword),
    queryFn: () => dataMartApi.tree(projectCode, keyword),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: planningQueryKeys.dataMartDetail(projectCode, selectedId),
    queryFn: () => dataMartApi.getById<DataMartRecord>(projectCode, selectedId),
    enabled: Boolean(projectCode && selectedId),
  });
  const businessTypesQuery = useQuery({
    queryKey: planningQueryKeys.dataMartBusinessTypes(projectCode),
    queryFn: () => businessTypeApi.all<BusinessTypeOption>(projectCode),
    enabled: Boolean(projectCode),
  });
  const dataMartsQuery = useQuery({
    queryKey: planningQueryKeys.dataMartAll(projectCode),
    queryFn: () => dataMartApi.all<DataMartOption>(projectCode),
    enabled: Boolean(projectCode),
  });

  const refresh = async () => {
    await treeQuery.refetch();
    if (selectedId) await detailQuery.refetch();
  };

  const openCreate = (parentId?: string) => {
    setEditing(undefined);
    setFormParentId(parentId);
    setFormOpen(true);
  };

  const openEdit = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await dataMartApi.getById<DataMartRecord>(projectCode, id);
      setEditing(record);
      setFormParentId(record.parentId || undefined);
      setFormOpen(true);
    } catch {
      toast.warning("获取数据集市详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
  };

  const save = async (values: DataMartFormValues) => {
    if (saving) return;
    setSaving(true);
    try {
      const payload: Record<string, unknown> = { ...values };
      if (editing?.id) {
        payload.id = editing.id;
        payload.parentId = editing.parentId || null;
        await dataMartApi.update(projectCode, payload);
        toast.success("数据集市已更新");
      } else {
        if (formParentId) payload.parentId = formParentId;
        await dataMartApi.create(projectCode, payload);
        toast.success("数据集市已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartTreePrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartChildrenPrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartAll(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainTreePrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainDataMarts(projectCode) });
      if (selectedId) {
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "detail", projectCode, selectedId] });
      }
    } catch {
      toast.warning("保存数据集市失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const openEditSubjectDomain = async (record: SubjectDomainRecord) => {
    if (!record.id || openingId) return;
    setOpeningId(record.id);
    try {
      const detail = await subjectDomainApi.getById<SubjectDomainRecord>(projectCode, record.id);
      setEditingSubject(detail);
      setSubjectFormOpen(true);
    } catch {
      toast.warning("获取主题域详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
  };

  const saveSubjectDomain = async (values: SubjectDomainFormValues) => {
    if (!editingSubject?.id || savingSubject) return;
    setSavingSubject(true);
    try {
      await subjectDomainApi.update(projectCode, {
        id: editingSubject.id,
        parentId: editingSubject.parentId || null,
        ...values,
      });
      toast.success("主题域已更新");
      setSubjectFormOpen(false);
      setEditingSubject(undefined);
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartSubjectDomainsPrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainTreePrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainDetail(projectCode, editingSubject.id) });
    } catch {
      toast.warning("更新主题域失败，请重试");
    } finally {
      setSavingSubject(false);
    }
  };

  const remove = async () => {
    if (!pendingDelete?.record.id || deleting) return;
    setDeleting(true);
    try {
      if (pendingDelete.kind === "mart") {
        await dataMartApi.remove(projectCode, pendingDelete.record.id);
        toast.success("数据集市已删除");
        if (selectedId === pendingDelete.record.id) setSelectedId("");
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "tree", projectCode] });
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartChildrenPrefix(projectCode) });
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartAll(projectCode) });
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainTreePrefix(projectCode) });
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainDataMarts(projectCode) });
      } else {
        await subjectDomainApi.remove(projectCode, pendingDelete.record.id);
        toast.success("主题域已删除");
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartSubjectDomainsPrefix(projectCode) });
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainTreePrefix(projectCode) });
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainChildrenPrefix(projectCode) });
      }
      setPendingDelete(undefined);
    } catch {
      toast.warning(pendingDelete.kind === "mart"
        ? "删除数据集市失败，请确认没有关联主题域或模型"
        : "删除主题域失败，请确认没有关联模型");
    } finally {
      setDeleting(false);
    }
  };

  const toggleExpanded = (key: string) => {
    setExpandedKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const treeRows = useMemo(() => treeQuery.data || [], [treeQuery.data]);
  useEffect(() => {
    setExpandedKeys(new Set(getExpandableKeys(treeRows)));
  }, [treeRows]);
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
  const selectedRecord = detailQuery.data;
  const pendingDeleteName = pendingDelete
    ? pendingDelete.kind === "mart" ? pendingDelete.record.martName : pendingDelete.record.domainName
    : "";

  return (
    <div ref={splitRef} className="relative flex h-full min-h-0 min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white">
      <DataMartTree
        width={leftWidth}
        treeData={treeRows}
        selectedId={selectedId}
        expandedKeys={expandedKeys}
        keyword={keyword}
        loading={treeQuery.isPending}
        error={treeQuery.isError}
        refreshing={treeQuery.isFetching}
        onKeywordChange={setKeyword}
        onToggleExpanded={toggleExpanded}
        onRefresh={() => void refresh()}
        onRetry={() => void treeQuery.refetch()}
        onCreateRoot={() => openCreate()}
        onSelect={(node) => setSelectedId(node.value || node.key)}
        onCreateChild={(node) => openCreate(node.value || node.key)}
        onEdit={(node) => void openEdit(node.value || node.key)}
        onDelete={(node) => setPendingDelete({
          kind: "mart",
          record: { id: node.value || node.key, martName: node.title },
        })}
      />

      <div role="separator" aria-orientation="vertical" aria-label="调整数据集市目录宽度"
        className={["relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f6f6f6]">
        {!selectedId ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
            <div className="flex flex-col items-center gap-2 text-center">
              <PlanningIcon name="data-mart" size={32} className="text-[#c5cbd3]" />
              <span className="text-sm text-[var(--yak-components-muted-text)]">请选择左侧数据集市查看详情</span>
            </div>
          </div>
        ) : detailQuery.isPending ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载数据集市详情...
          </div>
        ) : detailQuery.isError || !selectedRecord ? (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>数据集市详情加载失败</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
              <Button size="sm" variant="ghost" onClick={() => setSelectedId("")}>返回目录</Button>
            </div>
          </div>
        ) : (
          <>
            <header className="flex h-[72px] shrink-0 items-center border-b border-[var(--yak-components-panel-border)] bg-white px-5">
              <PlanningIcon name="data-mart" size={20} className="mr-2 text-[var(--yak-color-primary)]" />
              <h1 className="truncate text-lg font-semibold text-[var(--yak-components-panel-text)]">{selectedRecord.martName}</h1>
            </header>
            <DataMartDetail
              projectCode={projectCode} record={selectedRecord}
              onEdit={() => void openEdit(selectedRecord.id || "")}
              onAddChild={() => openCreate(selectedRecord.id)}
              onEditChild={(item) => void openEdit(item.id || "")}
              onDeleteChild={(item) => setPendingDelete({ kind: "mart", record: item })}
              onEditSubjectDomain={(item) => void openEditSubjectDomain(item)}
              onDeleteSubjectDomain={(item) => setPendingDelete({ kind: "subjectDomain", record: item })}
            />
          </>
        )}
      </main>

      <DataMartFormDialog
        open={formOpen} editing={editing} parentId={formParentId}
        businessTypes={businessTypesQuery.data || []}
        businessTypesLoading={businessTypesQuery.isPending}
        businessTypesError={businessTypesQuery.isError}
        onRetryBusinessTypes={() => void businessTypesQuery.refetch()}
        saving={saving}
        onClose={() => { if (!saving) { setFormOpen(false); setEditing(undefined); } }}
        onSubmit={(values) => void save(values)}
      />
      <SubjectDomainFormDialog
        open={subjectFormOpen}
        editing={editingSubject}
        parentId={editingSubject?.parentId || undefined}
        inheritedDataMartId={editingSubject?.dataMartId}
        inheritedDataMartName={editingSubject?.dataMartName}
        dataMarts={dataMartsQuery.data || []}
        dataMartsLoading={dataMartsQuery.isPending}
        dataMartsError={dataMartsQuery.isError}
        onRetryDataMarts={() => void dataMartsQuery.refetch()}
        saving={savingSubject}
        onClose={() => { if (!savingSubject) { setSubjectFormOpen(false); setEditingSubject(undefined); } }}
        onSubmit={(values) => void saveSubjectDomain(values)}
      />
      <Modal
        open={Boolean(pendingDelete)} centered width={420}
        title={pendingDelete?.kind === "subjectDomain" ? "删除主题域" : "删除数据集市"}
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
        </>}
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDeleteName || "当前记录"}”吗？
        </p>
      </Modal>
    </div>
  );
}

export default DataModelingDataMartPage;
