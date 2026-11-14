import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown, ChevronRight, Database, Layers, LoaderCircle,
  Pencil, Plus, PlusSquare, RefreshCw, Search, Trash2, X,
} from "lucide-react";
import {
  Button, Input, Modal, Table, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { modelingEntityApi, modelingSubjectDomainApi, modelingQueryKeys } from "../../service";
import {
  getSubjectDomainId, type DataMartOption, type SubjectDomainRecord,
  type SubjectDomainTreeNode, type SubjectDomainFormValues,
} from "./types";
import { SubjectDomainFormDialog } from "./subject-domain-form-dialog";
import { PlanningIcon } from "../icons";

interface Props { projectCode: string; }
interface ContextMenuState { x: number; y: number; node: SubjectDomainTreeNode; }

const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";
const display = (value?: string | null) => value && value.trim() ? value : "-";

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

function getExpandableKeys(nodes: SubjectDomainTreeNode[]): string[] {
  return nodes.reduce<string[]>((keys, node) => {
    if (node.children?.length) keys.push(node.value || node.key, ...getExpandableKeys(node.children));
    return keys;
  }, []);
}

function filterTree(nodes: SubjectDomainTreeNode[], keyword: string): SubjectDomainTreeNode[] {
  if (!keyword.trim()) return nodes;
  const normalized = keyword.trim().toLocaleLowerCase();
  return nodes.reduce<SubjectDomainTreeNode[]>((result, node) => {
    const children = filterTree(node.children || [], normalized);
    if (node.title.toLocaleLowerCase().includes(normalized) || children.length) {
      result.push({ ...node, children });
    }
    return result;
  }, []);
}

function TreeNodeRow({
  node, depth, selectedKey, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: SubjectDomainTreeNode; depth: number; selectedKey: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: SubjectDomainTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: SubjectDomainTreeNode) => void;
}) {
  const key = node.value || node.key;
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(key);
  const selected = selectedKey === key;
  const isMart = node.type === "MART";
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
          {hasChildren ? (expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />) : null}
        </button>
        <PlanningIcon name={isMart ? "data-mart" : "subject-domain"} size={14} className={selected ? "text-[var(--yak-color-primary)]" : "text-[#8b929e]"} />
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
        {isMart ? <span className="text-[10px] text-[#98a0aa]">集市</span> : null}
      </div>
      {hasChildren && expanded ? (
        <div role="group">
          {node.children?.map((child) => (
            <TreeNodeRow key={child.key || child.value} node={child} depth={depth + 1}
              selectedKey={selectedKey} expandedKeys={expandedKeys} onToggle={onToggle}
              onSelect={onSelect} onContextMenu={onContextMenu} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function SubjectDomainDetail({
  projectCode, record, onEdit, onAddChild, onEditChild, onDeleteChild,
}: {
  projectCode: string; record: SubjectDomainRecord; onEdit: () => void; onAddChild: () => void;
  onEditChild: (record: SubjectDomainRecord) => void; onDeleteChild: (record: SubjectDomainRecord) => void;
}) {
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 10;
  const childrenQuery = useQuery({
    queryKey: ["modeling", "subject-domain", "children", projectCode, record.id, pageNo],
    queryFn: () => modelingEntityApi.page<SubjectDomainRecord>(projectCode, "subjectDomain", {
      id: record.id, dataMartId: record.dataMartId, pageNo, pageSize,
    }),
    enabled: Boolean(projectCode && record.id),
  });
  useEffect(() => setPageNo(1), [record.id]);

  const columns: TableColumns<SubjectDomainRecord> = [
    { key: "domainName", title: "主题域名称", dataIndex: "domainName", width: 220 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 160 },
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

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-[#f2f3f5] p-4">
      <section className="bg-white pb-2">
        <div className="flex items-center justify-between p-4">
          <SectionTitle>基本信息</SectionTitle>
          <Button size="sm" variant="ghost" className="h-6 min-w-8 px-0 text-xs text-[#1677ff] hover:bg-transparent" onClick={onEdit}>编辑</Button>
        </div>
        <div className="mb-1 grid grid-cols-1 gap-x-8 gap-y-2 px-6 pb-2 sm:grid-cols-2 xl:grid-cols-4">
          <DescriptionItem label="英文缩写" value={record.enShortName} />
          <DescriptionItem label="主题域名称" value={record.domainName} />
          <DescriptionItem label="所属数据集市" value={record.dataMartName} className="xl:col-span-2" />
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
          <SectionTitle>子主题域</SectionTitle>
          <Button size="sm" variant="primary" onClick={onAddChild}><Plus size={13} />新建子主题域</Button>
        </div>
        <Table<SubjectDomainRecord>
          columns={columns} dataSource={childrenQuery.data?.totalList || []}
          rowKey={(item) => item.id || JSON.stringify(item)}
          loading={childrenQuery.isPending || childrenQuery.isFetching}
          bordered size="small" emptyText="暂无子主题域"
          pagination={(childrenQuery.data?.total || 0) > 0 ? {
            current: pageNo, pageSize, total: childrenQuery.data?.total || 0,
            disabled: childrenQuery.isFetching, onChange: setPageNo,
          } : false}
        />
      </section>
    </div>
  );
}

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
  const [contextMenu, setContextMenu] = useState<ContextMenuState>();
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
    queryKey: modelingQueryKeys.subjectDomainTree(projectCode),
    queryFn: () => modelingSubjectDomainApi.tree(projectCode),
    enabled: Boolean(projectCode),
  });
  const dataMartsQuery = useQuery({
    queryKey: modelingQueryKeys.dataMartAll(projectCode),
    queryFn: () => modelingEntityApi.all<DataMartOption>(projectCode, "dataMart"),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: ["modeling", "subject-domain", "detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<SubjectDomainRecord>(projectCode, "subjectDomain", selectedId),
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
    setContextMenu(undefined);
  };

  const openCreateChild = async (node: SubjectDomainTreeNode) => {
    const parentId = getSubjectDomainId(node);
    if (!parentId || openingId) return;
    setOpeningId(parentId);
    try {
      const parent = await modelingEntityApi.getById<SubjectDomainRecord>(projectCode, "subjectDomain", parentId);
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
      const record = await modelingEntityApi.getById<SubjectDomainRecord>(projectCode, "subjectDomain", id);
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
    setContextMenu(undefined);
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
        await modelingEntityApi.update(projectCode, "subjectDomain", payload);
        toast.success("主题域已更新");
      } else {
        await modelingEntityApi.create(projectCode, "subjectDomain", payload);
        toast.success("主题域已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "children", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "subject-domains", projectCode] });
      if (selectedId) {
        await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "detail", projectCode, selectedId] });
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
      await modelingEntityApi.remove(projectCode, "subjectDomain", pendingDelete.id);
      toast.success("主题域已删除");
      if (selectedId === pendingDelete.id) {
        setSelectedId("");
        setSelectedKey("");
        setSelectedType(undefined);
      }
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "subject-domain", "children", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "data-mart", "subject-domains", projectCode] });
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
    <div ref={splitRef} className="relative flex h-full min-h-0 min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white"
      onClick={() => contextMenu && setContextMenu(undefined)}>
      <aside className="flex min-h-0 shrink-0 flex-col overflow-hidden bg-white" style={{ width: leftWidth }}>
        <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
          <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">主题域</span>
          <div className="flex items-center gap-1">
            <button type="button" title="新建主题域" className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]" onClick={() => openCreate()}>
              <PlusSquare size={16} />
            </button>
            <button type="button" title="刷新" className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]" onClick={() => void refresh()}>
              <RefreshCw size={15} className={treeQuery.isFetching ? "animate-spin" : ""} />
            </button>
          </div>
        </div>
        <div className="shrink-0 px-3 py-2">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
            <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8" placeholder="搜索"
              onValueChange={(value) => setKeyword(String(value))} />
            {keyword ? <button type="button" aria-label="清空搜索" className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]" onClick={() => setKeyword("")}><X size={13} /></button> : null}
          </div>
        </div>
        <div role="tree" className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
          {treeQuery.isPending ? (
            <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">正在加载主题域...</div>
          ) : treeQuery.isError ? (
            <div className="px-2 py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
              <p>主题域加载失败</p>
              <Button size="sm" variant="secondary" className="mt-2" onClick={() => void treeQuery.refetch()}>重试</Button>
            </div>
          ) : treeRows.length ? (
            <div className="space-y-0.5">
              {treeRows.map((node) => (
                <TreeNodeRow key={node.key || node.value} node={node} depth={0}
                  selectedKey={selectedKey} expandedKeys={expandedKeys} onToggle={toggleExpanded}
                  onSelect={(item) => {
                    setSelectedKey(item.value || item.key);
                    setSelectedType(item.type);
                    setSelectedId(item.type === "SUBJECT" ? getSubjectDomainId(item) : "");
                  }}
                  onContextMenu={(event, item) => {
                    event.preventDefault();
                    event.stopPropagation();
                    if (item.type === "SUBJECT") {
                      setContextMenu({ x: event.clientX, y: event.clientY, node: item });
                    }
                  }} />
              ))}
            </div>
          ) : (
            <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
              {keyword ? "没有找到匹配的主题域" : "暂无主题域，点击右上角新建"}
            </div>
          )}
        </div>
      </aside>

      <div role="separator" aria-orientation="vertical" aria-label="调整主题域目录宽度"
        className={["relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f6f6f6]">
        {!selectedId || selectedType !== "SUBJECT" ? (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
            <div className="flex flex-col items-center gap-2 text-center">
              {selectedType === "MART"
                ? <Database size={32} strokeWidth={1.3} className="text-[#c5cbd3]" />
                : <Layers size={32} strokeWidth={1.3} className="text-[#c5cbd3]" />}
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
              <Layers size={20} className="mr-2 text-[var(--yak-color-primary)]" />
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

      {contextMenu ? (
        <div
          className="fixed z-[100] min-w-[148px] rounded-md border border-[#e4e7ec] bg-white p-1 shadow-[0_8px_24px_rgba(16,24,40,0.14)]"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}
        >
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
            onClick={() => void openCreateChild(contextMenu.node)}>
            <Plus size={13} />新增子主题域
          </button>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
            onClick={() => void openEdit(getSubjectDomainId(contextMenu.node))}>
            <Pencil size={13} />编辑
          </button>
          <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
            onClick={() => {
              setPendingDelete({ id: getSubjectDomainId(contextMenu.node), domainName: contextMenu.node.title });
              setContextMenu(undefined);
            }}>
            <Trash2 size={13} />删除
          </button>
        </div>
      ) : null}

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
