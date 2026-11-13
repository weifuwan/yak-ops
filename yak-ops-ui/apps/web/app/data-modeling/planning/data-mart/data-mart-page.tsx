import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2, ChevronDown, ChevronRight, Folder, FolderOpen, LoaderCircle,
  Pencil, Plus, PlusSquare, RefreshCw, Trash2,
} from "lucide-react";
import {
  Button, Input, Modal, Table, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { modelingEntityApi, modelingDataMartApi, type ModelingRecord } from "../../service";
import {
  getMartTypeLabel, type DataMartRecord, type DataMartTreeNode,
  type DataMartFormValues,
} from "./types";
import { DataMartFormDialog } from "./data-mart-form-dialog";
import { SubjectDomainFormDialog } from "../subject-domain/subject-domain-form-dialog";
import type { DataMartOption, SubjectDomainFormValues, SubjectDomainRecord } from "../subject-domain/types";

interface Props { projectCode: string; }
interface BusinessTypeOption extends ModelingRecord { id?: string; businessName?: string; }
interface ContextMenuState { x: number; y: number; node: DataMartTreeNode; }
type PendingDelete =
  | { kind: "mart"; record: DataMartRecord }
  | { kind: "subjectDomain"; record: SubjectDomainRecord };

const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";
const display = (value?: string | null) => value && value.trim() ? value : "-";

function getExpandableKeys(nodes: DataMartTreeNode[]): string[] {
  return nodes.reduce<string[]>((keys, node) => {
    if (node.children?.length) keys.push(node.value || node.key, ...getExpandableKeys(node.children));
    return keys;
  }, []);
}

function SectionTitle({ children }: { children: string }) {
  return <h3 className={TITLE}><span className="h-4 w-1 rounded-sm bg-[#5664ff]" />{children}</h3>;
}

function DescriptionItem({ label, value, className = "", multiline = false }: {
  label: string; value?: string | null; className?: string; multiline?: boolean;
}) {
  return (
    <div className={"flex min-w-0 items-baseline " + className}>
      <span className="shrink-0 text-xs leading-[22px] text-[rgba(128,128,128,1)]">{label}：</span>
      <span className={"min-w-0 pl-3 text-xs leading-[22px] text-black " + (multiline ? "whitespace-pre-wrap break-words" : "break-words")}>
        {display(value)}
      </span>
    </div>
  );
}

function TreeNodeRow({
  node, depth, selectedId, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: DataMartTreeNode; depth: number; selectedId: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: DataMartTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: DataMartTreeNode) => void;
}) {
  const key = node.value || node.key;
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(key);
  const selected = selectedId === key;
  return (
    <div>
      <div
        role="treeitem"
        aria-selected={selected}
        aria-expanded={hasChildren ? expanded : undefined}
        onContextMenu={(event) => onContextMenu(event, node)}
        onClick={() => onSelect(node)}
        style={{ paddingLeft: 10 + depth * 18 }}
        className={[
          "flex min-h-8 cursor-pointer items-center gap-1 rounded-md pr-2 text-[13px] transition-colors",
          selected
            ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
            : "text-[#3a3d51] hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
      >
        <button
          type="button"
          className="flex size-4 shrink-0 cursor-pointer items-center justify-center text-[#98a0aa]"
          aria-label={hasChildren ? (expanded ? "收起" : "展开") : "无子级"}
          onClick={(event) => { event.stopPropagation(); if (hasChildren) onToggle(key); }}
        >
          {hasChildren ? (expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />) : <span className="size-1 rounded-full bg-[#c4c9d1]" />}
        </button>
        {hasChildren ? (
          expanded ? <FolderOpen size={14} className="shrink-0 text-[#8b929e]" /> : <Folder size={14} className="shrink-0 text-[#8b929e]" />
        ) : <Building2 size={14} className="shrink-0 text-[#8b929e]" />}
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
      </div>
      {hasChildren && expanded ? (
        <div role="group">
          {node.children?.map((child) => (
            <TreeNodeRow key={child.key || child.value} node={child} depth={depth + 1}
              selectedId={selectedId} expandedKeys={expandedKeys} onToggle={onToggle}
              onSelect={onSelect} onContextMenu={onContextMenu} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function DataMartDetail({
  projectCode, record, onEdit, onAddChild, onEditChild, onDeleteChild,
  onEditSubjectDomain, onDeleteSubjectDomain,
}: {
  projectCode: string; record: DataMartRecord; onEdit: () => void; onAddChild: () => void;
  onEditChild: (record: DataMartRecord) => void; onDeleteChild: (record: DataMartRecord) => void;
  onEditSubjectDomain: (record: SubjectDomainRecord) => void;
  onDeleteSubjectDomain: (record: SubjectDomainRecord) => void;
}) {
  const [childPage, setChildPage] = useState(1);
  const [subjectPage, setSubjectPage] = useState(1);
  const pageSize = 10;
  const childrenQuery = useQuery({
    queryKey: ["modeling", "data-mart", "children", projectCode, record.id, childPage],
    queryFn: () => modelingEntityApi.page<DataMartRecord>(projectCode, "dataMart", {
      id: record.id, pageNo: childPage, pageSize,
    }),
    enabled: Boolean(projectCode && record.id),
  });
  const subjectQuery = useQuery({
    queryKey: ["modeling", "data-mart", "subject-domains", projectCode, record.id, subjectPage],
    queryFn: () => modelingEntityApi.page<SubjectDomainRecord>(projectCode, "subjectDomain", {
      dataMartId: record.id, pageNo: subjectPage, pageSize,
    }),
    enabled: Boolean(projectCode && record.id),
  });

  const childColumns: TableColumns<DataMartRecord> = [
    { key: "martName", title: "集市名称", dataIndex: "martName", width: 210 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEditChild(item)}>编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => onDeleteChild(item)}>删除</Button>
        </div>
      ),
    },
  ];
  const subjectColumns: TableColumns<SubjectDomainRecord> = [
    { key: "domainName", title: "主题域名称", dataIndex: "domainName", width: 210 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEditSubjectDomain(item)}>编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => onDeleteSubjectDomain(item)}>删除</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-[#f2f3f5] p-4">
      <section className="bg-white pb-2">
        <div className="flex items-center justify-between p-4">
          <SectionTitle>基本信息</SectionTitle>
          <Button size="sm" variant="ghost" className="h-6 min-w-8 px-0 text-xs text-[#1677ff] hover:bg-transparent" onClick={onEdit}>编辑</Button>
        </div>
        <div className="mb-1 grid grid-cols-1 gap-x-8 gap-y-2 px-6 pb-2 sm:grid-cols-2 xl:grid-cols-4">
          <DescriptionItem label="英文缩写" value={record.enShortName} />
          <DescriptionItem label="集市名称" value={record.martName} />
          <DescriptionItem label="所属业务分类" value={record.businessTypeName} />
          <DescriptionItem label="集市类型" value={getMartTypeLabel(record)} />
          <DescriptionItem label="负责人" value={record.director} />
          <DescriptionItem label="创建人" value={record.createBy} />
          <DescriptionItem label="创建时间" value={record.createTime} />
          <DescriptionItem label="修改人" value={record.updateBy} />
          <DescriptionItem label="修改时间" value={record.updateTime} />
          <DescriptionItem label="备注" value={record.remark} className="col-span-full" multiline />
        </div>
      </section>

      <section className={CARD + " mt-4"}>
        <div className="mb-3 flex items-center justify-between">
          <SectionTitle>子数据集市</SectionTitle>
          <Button size="sm" variant="primary" onClick={onAddChild}><Plus size={13} />新建子数据集市</Button>
        </div>
        <Table<DataMartRecord>
          columns={childColumns} dataSource={childrenQuery.data?.bizData || []}
          rowKey={(item) => item.id || JSON.stringify(item)}
          loading={childrenQuery.isPending || childrenQuery.isFetching}
          bordered size="small" emptyText="暂无子数据集市"
          pagination={(childrenQuery.data?.pagination.total || 0) > 0 ? {
            current: childPage, pageSize, total: childrenQuery.data?.pagination.total || 0,
            disabled: childrenQuery.isFetching, onChange: setChildPage,
          } : false}
        />
      </section>

      <section className={CARD}>
        <div className="mb-3 flex items-center justify-between">
          <SectionTitle>主题域管理</SectionTitle>
        </div>
        <Table<SubjectDomainRecord>
          columns={subjectColumns} dataSource={subjectQuery.data?.bizData || []}
          rowKey={(item) => item.id || JSON.stringify(item)}
          loading={subjectQuery.isPending || subjectQuery.isFetching}
          bordered size="small" emptyText="暂无关联主题域"
          pagination={(subjectQuery.data?.pagination.total || 0) > 0 ? {
            current: subjectPage, pageSize, total: subjectQuery.data?.pagination.total || 0,
            disabled: subjectQuery.isFetching, onChange: setSubjectPage,
          } : false}
        />
      </section>
    </div>
  );
}

export function DataModelingDataMartPage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [contextMenu, setContextMenu] = useState<ContextMenuState>();
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
    queryKey: ["modeling", "data-mart", "tree", projectCode, keyword],
    queryFn: () => modelingDataMartApi.tree(projectCode, keyword),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: ["modeling", "data-mart", "detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<DataMartRecord>(projectCode, "dataMart", selectedId),
    enabled: Boolean(projectCode && selectedId),
  });
  const businessTypesQuery = useQuery({
    queryKey: ["modeling", "data-mart", "business-types", projectCode],
    queryFn: () => modelingEntityApi.all<BusinessTypeOption>(projectCode, "businessType"),
    enabled: Boolean(projectCode),
  });
  const dataMartsQuery = useQuery({
    queryKey: ["modeling", "data-mart", "all", projectCode],
    queryFn: () => modelingEntityApi.all<DataMartOption>(projectCode, "dataMart"),
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
    setContextMenu(undefined);
  };

  const openEdit = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await modelingEntityApi.getById<DataMartRecord>(projectCode, "dataMart", id);
      setEditing(record);
      setFormParentId(record.parentId || undefined);
      setFormOpen(true);
    } catch {
      toast.warning("获取数据集市详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
    setContextMenu(undefined);
  };

  const save = async (values: DataMartFormValues) => {
    if (saving) return;
    setSaving(true);
    try {
      const payload: Record<string, unknown> = { ...values };
      if (editing?.id) {
        payload.id = editing.id;
        payload.parentId = editing.parentId || null;
        await modelingEntityApi.update(projectCode, "dataMart", payload);
        toast.success("数据集市已更新");
      } else {
        if (formParentId) payload.parentId = formParentId;
        await modelingEntityApi.create(projectCode, "dataMart", payload);
        toast.success("数据集市已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "children", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "all", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "data-marts", projectCode] });
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
      const detail = await modelingEntityApi.getById<SubjectDomainRecord>(projectCode, "subjectDomain", record.id);
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
      await modelingEntityApi.update(projectCode, "subjectDomain", {
        id: editingSubject.id,
        parentId: editingSubject.parentId || null,
        ...values,
      });
      toast.success("主题域已更新");
      setSubjectFormOpen(false);
      setEditingSubject(undefined);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "subject-domains", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "detail", projectCode, editingSubject.id] });
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
        await modelingEntityApi.remove(projectCode, "dataMart", pendingDelete.record.id);
        toast.success("数据集市已删除");
        if (selectedId === pendingDelete.record.id) setSelectedId("");
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "tree", projectCode] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "children", projectCode] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "all", projectCode] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "tree", projectCode] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "data-marts", projectCode] });
      } else {
        await modelingEntityApi.remove(projectCode, "subjectDomain", pendingDelete.record.id);
        toast.success("主题域已删除");
        await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "subject-domains", projectCode] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "tree", projectCode] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "children", projectCode] });
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
  const selectedRecord = detailQuery.data;
  const pendingDeleteName = pendingDelete
    ? pendingDelete.kind === "mart" ? pendingDelete.record.martName : pendingDelete.record.domainName
    : "";

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]" onClick={() => setContextMenu(undefined)}>
      <header className="shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
        <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">数据集市</h1>
        <p className="mb-0 mt-2 text-xs leading-5 text-[#626875]">
          面向某个应用场景或者产品的数据组织，一般会依赖数据公共层。
        </p>
      </header>

      <main className="flex min-h-0 flex-1 overflow-hidden">
        <aside className="flex w-[280px] shrink-0 flex-col border-r border-[#e7e9ee] bg-white max-md:w-[220px]">
          <div className="flex items-center justify-between px-3 pb-2 pt-3">
            <span className="text-xs font-semibold text-[#3a3d51]">数据集市目录</span>
            <div className="flex items-center gap-1">
              <Button size="sm" variant="ghost" isIconOnly aria-label="新建一级数据集市" onClick={() => openCreate()}>
                <PlusSquare size={15} />
              </Button>
              <Button size="sm" variant="ghost" isIconOnly aria-label="刷新目录" onClick={() => void refresh()}>
                <RefreshCw size={14} />
              </Button>
            </div>
          </div>
          <div className="px-3 pb-2">
            <Input size="small" variant="outlined" value={keyword} placeholder="搜索数据集市"
              onChange={(event) => setKeyword(event.target.value)} />
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
            {treeQuery.isPending ? (
              <div className="flex items-center justify-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
                <LoaderCircle size={14} className="animate-spin" />正在加载目录...
              </div>
            ) : treeQuery.isError ? (
              <div className="flex flex-col items-center gap-2 py-8 text-xs text-[var(--yak-components-muted-text)]">
                <span>目录加载失败</span>
                <Button size="sm" variant="secondary" onClick={() => void treeQuery.refetch()}>重试</Button>
              </div>
            ) : treeRows.length ? (
              <div role="tree" className="space-y-0.5">
                {treeRows.map((node) => (
                  <TreeNodeRow key={node.key || node.value} node={node} depth={0}
                    selectedId={selectedId} expandedKeys={expandedKeys} onToggle={toggleExpanded}
                    onSelect={(item) => setSelectedId(item.value || item.key)}
                    onContextMenu={(event, item) => {
                      event.preventDefault();
                      event.stopPropagation();
                      setContextMenu({ x: event.clientX, y: event.clientY, node: item });
                    }} />
                ))}
              </div>
            ) : (
              <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">暂无数据集市</div>
            )}
          </div>
        </aside>

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
          {!selectedId ? (
            <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
              请选择左侧数据集市查看详情
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
            <DataMartDetail
              projectCode={projectCode} record={selectedRecord}
              onEdit={() => void openEdit(selectedRecord.id || "")}
              onAddChild={() => openCreate(selectedRecord.id)}
              onEditChild={(item) => void openEdit(item.id || "")}
              onDeleteChild={(item) => setPendingDelete({ kind: "mart", record: item })}
              onEditSubjectDomain={(item) => void openEditSubjectDomain(item)}
              onDeleteSubjectDomain={(item) => setPendingDelete({ kind: "subjectDomain", record: item })}
            />
          )}
        </section>
      </main>

      {contextMenu ? (
        <div
          className="fixed z-[100] min-w-[148px] rounded-md border border-[#e4e7ec] bg-white p-1 shadow-[0_8px_24px_rgba(16,24,40,0.14)]"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}
        >
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
            onClick={() => openCreate(contextMenu.node.value || contextMenu.node.key)}>
            <Plus size={13} />新增子数据集市
          </button>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
            onClick={() => void openEdit(contextMenu.node.value || contextMenu.node.key)}>
            <Pencil size={13} />编辑
          </button>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
            onClick={() => {
              setPendingDelete({ kind: "mart", record: { id: contextMenu.node.value || contextMenu.node.key, martName: contextMenu.node.title } });
              setContextMenu(undefined);
            }}>
            <Trash2 size={13} />删除
          </button>
        </div>
      ) : null}

      <DataMartFormDialog
        open={formOpen} editing={editing} parentId={formParentId}
        businessTypes={businessTypesQuery.data || []} saving={saving}
        onClose={() => { if (!saving) { setFormOpen(false); setEditing(undefined); } }}
        onSubmit={(values) => void save(values)}
      />
      <SubjectDomainFormDialog
        open={subjectFormOpen}
        editing={editingSubject}
        parentId={editingSubject?.parentId || undefined}
        inheritedDataMartId={editingSubject?.dataMartId}
        dataMarts={dataMartsQuery.data || []}
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
