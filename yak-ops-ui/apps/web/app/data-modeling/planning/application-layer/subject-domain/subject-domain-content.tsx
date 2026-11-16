import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoaderCircle } from "lucide-react";
import { Button, Modal, toast } from "@yak-ops/yak-ui";

import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { planningQueryKeys } from "../../query-keys";
import { PlanningIcon } from "../../components/icons";
import { SubjectDomainTree, filterTree, getExpandableKeys } from "./subject-domain-tree";
import { SubjectDomainDetail } from "./subject-domain-detail";
import { SubjectDomainFormDialog } from "../components/subject-domain-form-dialog";
import { getSubjectDomainId, type SubjectDomainRecord, type SubjectDomainFormValues } from "./types";
import type { DataMartOption } from "../data-mart/types";

interface Props { projectCode: string; }

export function DataModelingSubjectDomainPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const splitRef = useRef<HTMLDivElement | null>(null);
  const [leftWidth, setLeftWidth] = useState(280);
  const [resizing, setResizing] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selectedKey, setSelectedKey] = useState("");
  const [selectedType, setSelectedType] = useState<"MART" | "SUBJECT">();
  const [selectedId, setSelectedId] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<SubjectDomainRecord>();
  const [formParentId, setFormParentId] = useState<string>();
  const [inheritedDataMartId, setInheritedDataMartId] = useState<string>();
  const [inheritedDataMartName, setInheritedDataMartName] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<SubjectDomainRecord>();
  const [deleting, setDeleting] = useState(false);

  const treeQuery = useQuery({
    queryKey: planningQueryKeys.subjectDomainTree(projectCode),
    queryFn: () => subjectDomainApi.tree(projectCode),
    enabled: Boolean(projectCode),
  });
  const dataMartsQuery = useQuery({
    queryKey: planningQueryKeys.dataMartAll(projectCode),
    queryFn: () => dataMartApi.all<DataMartOption>(projectCode),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: planningQueryKeys.subjectDomainDetail(projectCode, selectedId),
    queryFn: () => subjectDomainApi.getById<SubjectDomainRecord>(projectCode, selectedId),
    enabled: Boolean(projectCode && selectedId && selectedType === "SUBJECT"),
  });

  const treeRows = useMemo(
    () => filterTree(treeQuery.data || [], keyword),
    [treeQuery.data, keyword],
  );
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

  const refresh = async () => {
    await treeQuery.refetch();
    if (selectedId) await detailQuery.refetch();
  };

  const openCreate = (parentId?: string, dataMartId?: string, dataMartName?: string) => {
    setEditing(undefined);
    setFormParentId(parentId);
    setInheritedDataMartId(dataMartId);
    setInheritedDataMartName(dataMartName);
    setFormOpen(true);
  };

  const openCreateChild = async (node: SubjectDomainTreeNode) => {
    const parentId = getSubjectDomainId(node);
    if (!parentId || openingId) return;
    setOpeningId(parentId);
    try {
      const parent = await subjectDomainApi.getById<SubjectDomainRecord>(projectCode, parentId);
      openCreate(parentId, parent.dataMartId, parent.dataMartName);
    } catch {
      toast.warning("获取上级主题域失败，请刷新目录后重试");
    } finally {
      setOpeningId("");
    }
  };

  const openEdit = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await subjectDomainApi.getById<SubjectDomainRecord>(projectCode, id);
      setEditing(record);
      setFormParentId(record.parentId || undefined);
      setInheritedDataMartId(record.dataMartId || undefined);
      setInheritedDataMartName(record.dataMartName || undefined);
      setFormOpen(true);
    } catch {
      toast.warning("获取主题域详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
  };

  const save = async (values: SubjectDomainFormValues) => {
    if (saving) return;
    setSaving(true);
    try {
      const payload: Record<string, unknown> = { ...values };
      if (editing?.id) {
        payload.id = editing.id;
        payload.parentId = editing.parentId || null;
      } else if (formParentId) {
        payload.parentId = formParentId;
        payload.dataMartId = inheritedDataMartId || values.dataMartId;
      }
      if (editing?.parentId) payload.dataMartId = editing.dataMartId || inheritedDataMartId || values.dataMartId;
      if (editing?.id) {
        await subjectDomainApi.update(projectCode, payload);
        toast.success("主题域已更新");
      } else {
        await subjectDomainApi.create(projectCode, payload);
        toast.success("主题域已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainTreePrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainChildrenPrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartSubjectDomainsPrefix(projectCode) });
      if (selectedId) {
        await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainDetail(projectCode, selectedId) });
      }
    } catch {
      toast.warning("保存主题域失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!pendingDelete?.id || deleting) return;
    setDeleting(true);
    try {
      await subjectDomainApi.remove(projectCode, pendingDelete.id);
      toast.success("主题域已删除");
      if (selectedId === pendingDelete.id) {
        setSelectedId("");
        setSelectedKey("");
        setSelectedType(undefined);
      }
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainTreePrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.subjectDomainChildrenPrefix(projectCode) });
      await queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartSubjectDomainsPrefix(projectCode) });
    } catch {
      toast.warning("删除主题域失败，请确认该主题域没有被其他模型引用");
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

  return (
    <div ref={splitRef} className="relative flex h-full min-h-0 min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white">
      <SubjectDomainTree
        width={leftWidth}
        treeData={treeRows}
        selectedKey={selectedKey}
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
        onSelect={(item) => {
          setSelectedKey(item.value || item.key);
          setSelectedType(item.type);
          setSelectedId(item.type === "SUBJECT" ? getSubjectDomainId(item) : "");
        }}
        onCreateChild={(node) => void openCreateChild(node)}
        onEdit={(node) => void openEdit(getSubjectDomainId(node))}
        onDelete={(node) => setPendingDelete({
          id: getSubjectDomainId(node),
          domainName: node.title,
        })}
      />

      <div role="separator" aria-orientation="vertical" aria-label="调整主题域目录宽度"
        className={["relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f6f6f6]">
        {!selectedId || selectedType !== "SUBJECT" ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
            <div className="flex flex-col items-center gap-2 text-center">
              <PlanningIcon name={selectedType === "MART" ? "data-mart" : "subject-domain"} size={32} className="text-[#c5cbd3]" />
              <span className="text-sm text-[var(--yak-components-muted-text)]">
                {selectedType === "MART" ? "已选择数据集市，请展开目录并选择主题域查看详情" : "请选择左侧主题域查看详情"}
              </span>
            </div>
          </div>
        ) : detailQuery.isPending ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
            <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载主题域详情...
          </div>
        ) : detailQuery.isError || !detailQuery.data ? (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
            <span>主题域详情加载失败</span>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
              <Button size="sm" variant="ghost" onClick={() => { setSelectedId(""); setSelectedKey(""); setSelectedType(undefined); }}>返回目录</Button>
            </div>
          </div>
        ) : (
          <>
            <header className="flex h-[72px] shrink-0 items-center border-b border-[var(--yak-components-panel-border)] bg-white px-5">
              <PlanningIcon name="subject-domain" size={20} className="mr-2 text-[var(--yak-color-primary)]" />
              <h1 className="truncate text-lg font-semibold text-[var(--yak-components-panel-text)]">{detailQuery.data.domainName}</h1>
            </header>
            <SubjectDomainDetail
              projectCode={projectCode}
              record={detailQuery.data}
              onEdit={() => void openEdit(detailQuery.data.id || "")}
              onAddChild={() => {
                const current = detailQuery.data;
                if (current.id) void openCreate(current.id, current.dataMartId, current.dataMartName);
              }}
              onEditChild={(item) => void openEdit(item.id || "")}
              onDeleteChild={setPendingDelete}
            />
          </>
        )}
      </main>

      <SubjectDomainFormDialog
        open={formOpen}
        editing={editing}
        parentId={formParentId}
        inheritedDataMartId={inheritedDataMartId}
        inheritedDataMartName={inheritedDataMartName}
        dataMarts={dataMartsQuery.data || []}
        dataMartsLoading={dataMartsQuery.isPending}
        dataMartsError={dataMartsQuery.isError}
        onRetryDataMarts={() => void dataMartsQuery.refetch()}
        saving={saving}
        onClose={() => {
          if (!saving) {
            setFormOpen(false);
            setEditing(undefined);
            setFormParentId(undefined);
            setInheritedDataMartId(undefined);
            setInheritedDataMartName(undefined);
          }
        }}
        onSubmit={(values) => void save(values)}
      />
      <Modal
        open={Boolean(pendingDelete)} centered width={420} title="删除主题域"
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
        </>}
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete?.domainName || "当前主题域"}”吗？
        </p>
      </Modal>
    </div>
  );
}

export default DataModelingSubjectDomainPage;
