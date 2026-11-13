import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown, ChevronRight, FileText, Folder, FolderOpen, Layers, LoaderCircle,
  Pencil, Plus, PlusSquare, RefreshCw, Trash2,
} from "lucide-react";
import {
  Button, Input, Modal, Table, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { modelingEntityApi } from "../service";
import type {
  CodeDetailRecord, CodeStandardRecord, FieldStandardRecord, StandardDirectoryRecord,
  StandardModule, StandardTreeNode, UnitStandardRecord,
} from "./types";
import {
  getDirectoryTypeLabel, getStandardDescription, getStandardResource,
  getStandardTitle, isDirectoryNode,
} from "./types";
import { StandardDirectoryFormDialog } from "./standard-directory-form-dialog";
import { StandardRecordFormDialog } from "./standard-record-form-dialog";

interface Props { projectCode: string; module: StandardModule; }
interface ContextMenuState { x: number; y: number; node: StandardTreeNode; }
type StandardRecord = FieldStandardRecord | CodeStandardRecord | UnitStandardRecord;
type PendingDelete = {
  id: string;
  name: string;
  resource: "standardDirectory" | "fieldStandard" | "codeStandard" | "unitStandard";
  directory: boolean;
};

const CARD = "mb-4 rounded-md border border-[#e7e9ee] bg-white p-4";
const TITLE = "flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";
const PAGE_SIZE = 10;

function SectionTitle({ children }: { children: string }) {
  return <h3 className={TITLE}><span className="h-4 w-1 rounded-sm bg-[var(--yak-color-primary)]" />{children}</h3>;
}

function display(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "boolean") return value ? "是" : "否";
  return String(value);
}

function DescriptionItem({
  label, value, className = "", multiline = false,
}: {
  label: string; value: unknown; className?: string; multiline?: boolean;
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

function filterTree(nodes: StandardTreeNode[], keyword: string): StandardTreeNode[] {
  const normalized = keyword.trim().toLocaleLowerCase();
  if (!normalized) return nodes;
  return nodes.reduce<StandardTreeNode[]>((result, node) => {
    const children = filterTree(node.children || [], normalized);
    if (node.title.toLocaleLowerCase().includes(normalized) || children.length) {
      result.push({ ...node, children });
    }
    return result;
  }, []);
}

function getExpandableKeys(nodes: StandardTreeNode[]): string[] {
  return nodes.reduce<string[]>((keys, node) => {
    if (node.children?.length) keys.push(node.key, ...getExpandableKeys(node.children));
    return keys;
  }, []);
}

function findNodePath(nodes: StandardTreeNode[], key: string, path: string[] = []): string[] {
  for (const node of nodes) {
    const next = [...path, node.title];
    if (node.key === key) return next;
    const found = findNodePath(node.children || [], key, next);
    if (found.length) return found;
  }
  return [];
}

function TreeNodeRow({
  node, depth, selectedKey, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: StandardTreeNode; depth: number; selectedKey: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: StandardTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: StandardTreeNode) => void;
}) {
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(node.key);
  const selected = selectedKey === node.key;
  const directory = isDirectoryNode(node);
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
          onClick={(event) => { event.stopPropagation(); if (hasChildren) onToggle(node.key); }}
        >
          {hasChildren ? (expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />) : <span className="size-1 rounded-full bg-[#c4c9d1]" />}
        </button>
        {node.type === "FOLDER"
          ? (expanded ? <FolderOpen size={14} className="shrink-0 text-[#8b929e]" /> : <Folder size={14} className="shrink-0 text-[#8b929e]" />)
          : node.type === "STANDARD_SET"
            ? <Layers size={14} className="shrink-0 text-[#8b929e]" />
            : <FileText size={14} className="shrink-0 text-[#8b929e]" />}
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
        {node.type === "STANDARD_SET" ? <span className="text-[10px] text-[#98a0aa]">标准集</span> : null}
        {!directory ? <span className="text-[10px] text-[#98a0aa]">{node.type === "FIELD_STANDARD" ? "字段" : node.type === "CODE_STANDARD" ? "代码" : node.type === "UNIT_STANDARD" ? "单位" : ""}</span> : null}
      </div>
      {hasChildren && expanded ? (
        <div role="group">
          {node.children?.map((child) => (
            <TreeNodeRow key={child.key} node={child} depth={depth + 1}
              selectedKey={selectedKey} expandedKeys={expandedKeys} onToggle={onToggle}
              onSelect={onSelect} onContextMenu={onContextMenu} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function CodeDetailsTable({ items }: { items: CodeDetailRecord[] }) {
  const columns: TableColumns<CodeDetailRecord> = [
    { key: "enShortName", title: "编码取值", dataIndex: "enShortName", width: 180 },
    { key: "cnName", title: "中文名称", dataIndex: "cnName", width: 180 },
    { key: "enName", title: "英文名称", dataIndex: "enName", width: 220 },
    { key: "remark", title: "编码含义", dataIndex: "remark" },
  ];
  return (
    <section className={CARD}>
      <div className="mb-3 flex items-center justify-between">
        <SectionTitle>代码明细</SectionTitle>
        <span className="text-xs text-[var(--yak-components-muted-text)]">{items.length} 条</span>
      </div>
      <Table<CodeDetailRecord>
        columns={columns} dataSource={items} rowKey={(item) => item.id || item.enShortName || ""}
        bordered size="small" emptyText="暂无代码明细" pagination={items.length > PAGE_SIZE ? { pageSize: PAGE_SIZE } : false}
      />
    </section>
  );
}

function StandardRecordDetails({
  module, record, directoryName,
}: {
  module: StandardModule; record: StandardRecord; directoryName?: string;
}) {
  const field = record as FieldStandardRecord;
  const code = record as CodeStandardRecord;
  return (
    <>
      <section className={CARD}>
        <div className="mb-3 flex items-center justify-between"><SectionTitle>基本信息</SectionTitle></div>
        <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-2 sm:grid-cols-2 xl:grid-cols-3">
          {module === "FIELD" ? (
            <>
              <DescriptionItem label="标准编码" value={field.stdCode} />
              <DescriptionItem label="中文名称" value={field.cnName} />
              <DescriptionItem label="英文缩写" value={field.enShortName} />
              <DescriptionItem label="英文名称" value={field.enName} />
              <DescriptionItem label="数据类型" value={field.fieldDataType} />
              <DescriptionItem label="字段长度" value={field.fieldLength} />
              <DescriptionItem label="字段精度" value={field.fieldPrecision} />
              <DescriptionItem label="允许为空" value={field.fieldCanNull} />
              <DescriptionItem label="默认值" value={field.fieldDefaultValue} />
              <DescriptionItem label="父级标准" value={field.parentStd} />
              <DescriptionItem label="引用代码" value={field.reference} />
              <DescriptionItem label="所属目录" value={directoryName} />
              <DescriptionItem label="业务定义" value={field.businessDefinition} className="col-span-full" multiline />
            </>
          ) : (
            <>
              <DescriptionItem label="英文缩写" value={record.enShortName} />
              <DescriptionItem label="英文名称" value={record.enName} />
              <DescriptionItem label="中文名称" value={record.cnName} />
              {module === "CODE" ? <DescriptionItem label="描述" value={code.remark} className="col-span-full" multiline /> : null}
              <DescriptionItem label="所属目录" value={directoryName} />
            </>
          )}
          <DescriptionItem label="创建人" value={record.createBy} />
          <DescriptionItem label="创建时间" value={record.createTime} />
          <DescriptionItem label="修改人" value={record.updateBy} />
          <DescriptionItem label="修改时间" value={record.updateTime} />
        </div>
      </section>
      {module === "CODE" ? <CodeDetailsTable items={code.detailList || []} /> : null}
    </>
  );
}

export function DirectoryStandardsPage({ projectCode, module }: Props) {
  const queryClient = useQueryClient();
  const title = getStandardTitle(module);
  const description = getStandardDescription(module);
  const resource = getStandardResource(module);
  const [keyword, setKeyword] = useState("");
  const [selectedNode, setSelectedNode] = useState<StandardTreeNode>();
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [contextMenu, setContextMenu] = useState<ContextMenuState>();
  const [directoryDialogOpen, setDirectoryDialogOpen] = useState(false);
  const [editingDirectory, setEditingDirectory] = useState<StandardDirectoryRecord>();
  const [directoryParentId, setDirectoryParentId] = useState<string>();
  const [initialDirType, setInitialDirType] = useState("FOLDER");
  const [recordDialogOpen, setRecordDialogOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<StandardRecord>();
  const [recordParentId, setRecordParentId] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<PendingDelete>();
  const [deleting, setDeleting] = useState(false);
  const [recordKeyword, setRecordKeyword] = useState("");
  const [recordPageNo, setRecordPageNo] = useState(1);
  const [directoryPageNo, setDirectoryPageNo] = useState(1);

  const treeQuery = useQuery({
    queryKey: ["modeling", "standards", "tree", projectCode, module],
    queryFn: () => modelingEntityApi.directoryTree(projectCode, module),
    enabled: Boolean(projectCode),
  });
  const treeRows = useMemo(
    () => filterTree(treeQuery.data || [], keyword),
    [treeQuery.data, keyword],
  );

  useEffect(() => {
    setExpandedKeys(new Set(getExpandableKeys(treeRows)));
  }, [treeRows]);

  useEffect(() => {
    setRecordPageNo(1);
    setDirectoryPageNo(1);
    setRecordKeyword("");
  }, [selectedNode?.id]);

  const selectedIsDirectory = isDirectoryNode(selectedNode);
  const detailResource = selectedNode
    ? selectedIsDirectory ? "standardDirectory" : resource
    : undefined;

  const detailQuery = useQuery({
    queryKey: ["modeling", "standards", "detail", projectCode, module, selectedNode?.type, selectedNode?.id],
    queryFn: () => modelingEntityApi.getById<StandardDirectoryRecord | StandardRecord>(
      projectCode, detailResource!, selectedNode!.id,
    ),
    enabled: Boolean(projectCode && selectedNode?.id && detailResource),
  });

  const directoryQuery = useQuery({
    queryKey: ["modeling", "standards", "directories", projectCode, module, selectedNode?.id, directoryPageNo],
    queryFn: () => modelingEntityApi.page<StandardDirectoryRecord>(projectCode, "standardDirectory", {
      dirModule: module,
      parentId: selectedNode?.id,
      pageNo: directoryPageNo,
      pageSize: PAGE_SIZE,
    }),
    enabled: Boolean(projectCode && selectedNode?.type === "FOLDER"),
  });

  const recordQuery = useQuery({
    queryKey: ["modeling", "standards", "records", projectCode, module, selectedNode?.id, recordPageNo, recordKeyword],
    queryFn: () => modelingEntityApi.page<StandardRecord>(projectCode, resource, {
      parentId: selectedNode?.id,
      pageNo: recordPageNo,
      pageSize: PAGE_SIZE,
      keyword: recordKeyword.trim(),
    }),
    enabled: Boolean(projectCode && selectedIsDirectory && selectedNode?.id),
  });

  const refresh = async () => {
    await treeQuery.refetch();
    if (selectedNode?.id) await detailQuery.refetch();
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "directories", projectCode, module] });
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "records", projectCode, module] });
  };

  const invalidateModule = async () => {
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "tree", projectCode, module] });
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "detail", projectCode, module] });
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "directories", projectCode, module] });
    await queryClient.invalidateQueries({ queryKey: ["modeling", "standards", "records", projectCode, module] });
  };

  const openCreateDirectory = (parentId?: string, dirType = "FOLDER") => {
    setEditingDirectory(undefined);
    setDirectoryParentId(parentId);
    setInitialDirType(dirType);
    setDirectoryDialogOpen(true);
    setContextMenu(undefined);
  };

  const openEditDirectory = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await modelingEntityApi.getById<StandardDirectoryRecord>(projectCode, "standardDirectory", id);
      setEditingDirectory(record);
      setDirectoryParentId(record.parentId || undefined);
      setInitialDirType(record.dirType || "FOLDER");
      setDirectoryDialogOpen(true);
    } catch {
      toast.warning("获取目录详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
      setContextMenu(undefined);
    }
  };

  const openCreateRecord = (parentId?: string) => {
    setEditingRecord(undefined);
    setRecordParentId(parentId);
    setRecordDialogOpen(true);
    setContextMenu(undefined);
  };

  const openEditRecord = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await modelingEntityApi.getById<StandardRecord>(projectCode, resource, id);
      setEditingRecord(record);
      setRecordParentId(record.parentId || undefined);
      setRecordDialogOpen(true);
    } catch {
      toast.warning("获取标准详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
      setContextMenu(undefined);
    }
  };

  const saveDirectory = async (values: Record<string, unknown>) => {
    if (saving) return;
    setSaving(true);
    try {
      if (editingDirectory?.id) {
        await modelingEntityApi.update(projectCode, "standardDirectory", { ...values, id: editingDirectory.id });
        toast.success("目录已更新");
      } else {
        await modelingEntityApi.create(projectCode, "standardDirectory", values);
        toast.success("目录已创建");
      }
      setDirectoryDialogOpen(false);
      setEditingDirectory(undefined);
      await invalidateModule();
    } catch {
      toast.warning("保存目录失败，请检查目录名称和层级后重试");
    } finally {
      setSaving(false);
    }
  };

  const saveRecord = async (values: Record<string, unknown>) => {
    if (saving) return;
    setSaving(true);
    try {
      if (editingRecord?.id) {
        await modelingEntityApi.update(projectCode, resource, { ...values, id: editingRecord.id });
        toast.success(title + "已更新");
      } else {
        await modelingEntityApi.create(projectCode, resource, values);
        toast.success(title + "已创建");
      }
      setRecordDialogOpen(false);
      setEditingRecord(undefined);
      await invalidateModule();
    } catch {
      toast.warning("保存" + title + "失败，请检查必填项后重试");
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    try {
      await modelingEntityApi.remove(projectCode, pendingDelete.resource, pendingDelete.id);
      toast.success(pendingDelete.directory ? "目录已删除" : title + "已删除");
      if (selectedNode?.id === pendingDelete.id) setSelectedNode(undefined);
      setPendingDelete(undefined);
      await invalidateModule();
    } catch {
      toast.warning("删除失败：请检查目录是否仍包含子项，或该标准是否仍被其他资产引用");
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

  const directoryColumns: TableColumns<StandardDirectoryRecord> = [
    { key: "dirName", title: "目录名称", dataIndex: "dirName", width: 240 },
    { key: "dirType", title: "目录类型", dataIndex: "dirType", width: 140, render: (value) => getDirectoryTypeLabel(String(value || "")) },
    { key: "createBy", title: "创建人", dataIndex: "createBy", width: 140 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => void openEditDirectory(item.id || "")}>编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]"
            onClick={() => setPendingDelete({ id: item.id || "", name: item.dirName || "目录", resource: "standardDirectory", directory: true })}>删除</Button>
        </div>
      ),
    },
  ];

  const recordColumns: TableColumns<StandardRecord> = [
    ...(module === "FIELD" ? [
      { key: "stdCode", title: "标准编码", dataIndex: "stdCode", width: 160 },
      { key: "cnName", title: "中文名称", dataIndex: "cnName", width: 200 },
      { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150 },
      { key: "fieldDataType", title: "数据类型", dataIndex: "fieldDataType", width: 120 },
      { key: "fieldLength", title: "长度", dataIndex: "fieldLength", width: 90 },
      { key: "fieldPrecision", title: "精度", dataIndex: "fieldPrecision", width: 90 },
      { key: "fieldCanNull", title: "允许为空", dataIndex: "fieldCanNull", width: 100, render: (value: unknown) => value === true ? "是" : value === false ? "否" : "-" },
    ] : module === "CODE" ? [
      { key: "cnName", title: "代码集名称", dataIndex: "cnName", width: 220 },
      { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 160 },
      { key: "enName", title: "英文名称", dataIndex: "enName", width: 240 },
      { key: "remark", title: "描述", dataIndex: "remark" },
    ] : [
      { key: "cnName", title: "单位名称", dataIndex: "cnName", width: 220 },
      { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 160 },
      { key: "enName", title: "英文名称", dataIndex: "enName", width: 240 },
    ]),
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => void openEditRecord(item.id || "")}>编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]"
            onClick={() => setPendingDelete({ id: item.id || "", name: item.cnName || title, resource, directory: false })}>删除</Button>
        </div>
      ),
    },
  ];

  const selectedDetail = detailQuery.data;
  const selectedDirectory = selectedDetail as StandardDirectoryRecord | undefined;
  const selectedRecord = selectedDetail as StandardRecord | undefined;
  const breadcrumb = selectedNode
    ? findNodePath(treeQuery.data || [], selectedNode.key)
    : [];

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-[#f2f3f5]" onClick={() => setContextMenu(undefined)}>
      <header className="shrink-0 bg-white px-5 py-4 shadow-[0_2px_8px_#0000000d]">
        <h1 className="m-0 text-xl font-bold leading-7 text-[#26282c]">{title}</h1>
        <p className="mb-0 mt-2 text-xs leading-5 text-[#626875]">{description}</p>
      </header>

      <main className="flex min-h-0 flex-1 overflow-hidden">
        <aside className="flex w-[280px] shrink-0 flex-col border-r border-[#e7e9ee] bg-white max-md:w-[220px]">
          <div className="flex items-center justify-between px-3 pb-2 pt-3">
            <span className="text-xs font-semibold text-[#3a3d51]">{title}目录</span>
            <div className="flex items-center gap-1">
              <Button size="sm" variant="ghost" isIconOnly aria-label="新建根目录" onClick={() => openCreateDirectory()}>
                <PlusSquare size={15} />
              </Button>
              <Button size="sm" variant="ghost" isIconOnly aria-label="刷新目录" onClick={() => void refresh()}>
                <RefreshCw size={14} />
              </Button>
            </div>
          </div>
          <div className="px-3 pb-2">
            <Input size="small" variant="outlined" value={keyword} placeholder={"搜索" + title + "目录"}
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
                  <TreeNodeRow key={node.key} node={node} depth={0}
                    selectedKey={selectedNode?.key || ""} expandedKeys={expandedKeys}
                    onToggle={toggleExpanded} onSelect={setSelectedNode}
                    onContextMenu={(event, item) => {
                      event.preventDefault();
                      event.stopPropagation();
                      setContextMenu({ x: event.clientX, y: event.clientY, node: item });
                    }} />
                ))}
              </div>
            ) : (
              <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                {keyword ? "没有匹配的目录" : "暂无目录，点击上方按钮新建"}
              </div>
            )}
          </div>
        </aside>

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
          {!selectedNode ? (
            <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
              请选择左侧目录或标准查看详情
            </div>
          ) : detailQuery.isPending ? (
            <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
              <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载详情...
            </div>
          ) : detailQuery.isError || !selectedDetail ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
              <span>详情加载失败</span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
                <Button size="sm" variant="ghost" onClick={() => setSelectedNode(undefined)}>返回目录</Button>
              </div>
            </div>
          ) : (
            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              <div className="mb-3 flex items-center justify-between rounded-md border border-[#e7e9ee] bg-white px-4 py-3">
                <div className="min-w-0">
                  <h2 className="m-0 truncate text-sm font-semibold text-[#26282c]">{selectedNode.title}</h2>
                  <div className="mt-1 flex flex-wrap items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]">
                    <span>{title}</span>
                    {breadcrumb.map((part, index) => (
                      <span key={part + index} className="flex items-center gap-1">
                        <ChevronRight size={11} />{part}
                      </span>
                    ))}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {selectedIsDirectory && selectedNode.type === "FOLDER" ? (
                    <>
                      {module === "FIELD" ? (
                        <Button size="sm" variant="secondary" onClick={() => openCreateDirectory(selectedNode.id, "STANDARD_SET")}>新建标准集</Button>
                      ) : null}
                      <Button size="sm" variant="primary" onClick={() => openCreateRecord(selectedNode.id)}>
                        <Plus size={13} />新建{module === "FIELD" ? "字段标准" : module === "CODE" ? "标准代码" : "度量单位"}
                      </Button>
                    </>
                  ) : null}
                  {selectedIsDirectory && selectedNode.type === "STANDARD_SET" ? (
                    <Button size="sm" variant="primary" onClick={() => openCreateRecord(selectedNode.id)}>
                      <Plus size={13} />新建字段标准
                    </Button>
                  ) : null}
                  <Button size="sm" variant="ghost" className="text-xs" onClick={() => {
                    if (selectedIsDirectory) void openEditDirectory(selectedNode.id);
                    else void openEditRecord(selectedNode.id);
                  }}>
                    <Pencil size={13} />编辑
                  </Button>
                </div>
              </div>

              {selectedIsDirectory ? (
                <>
                  <section className={CARD}>
                    <div className="mb-3 flex items-center justify-between"><SectionTitle>目录信息</SectionTitle></div>
                    <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-2 sm:grid-cols-2 xl:grid-cols-3">
                      <DescriptionItem label="目录名称" value={selectedDirectory?.dirName} />
                      <DescriptionItem label="目录类型" value={getDirectoryTypeLabel(selectedDirectory?.dirType)} />
                      <DescriptionItem label="所属模块" value={title} />
                      <DescriptionItem label="创建人" value={selectedDirectory?.createBy} />
                      <DescriptionItem label="创建时间" value={selectedDirectory?.createTime} />
                      <DescriptionItem label="修改人" value={selectedDirectory?.updateBy} />
                      <DescriptionItem label="修改时间" value={selectedDirectory?.updateTime} />
                    </div>
                  </section>

                  {selectedNode.type === "FOLDER" ? (
                    <section className={CARD}>
                      <div className="mb-3 flex items-center justify-between">
                        <SectionTitle>子目录</SectionTitle>
                        <Button size="sm" variant="secondary" onClick={() => openCreateDirectory(selectedNode.id)}>
                          <Plus size={13} />新建子目录
                        </Button>
                      </div>
                      <Table<StandardDirectoryRecord>
                        columns={directoryColumns}
                        dataSource={directoryQuery.data?.bizData || []}
                        rowKey={(item) => item.id || ""}
                        loading={directoryQuery.isPending || directoryQuery.isFetching}
                        bordered size="small" emptyText="暂无子目录"
                        pagination={(directoryQuery.data?.pagination.total || 0) > 0 ? {
                          current: directoryPageNo, pageSize: PAGE_SIZE,
                          total: directoryQuery.data?.pagination.total || 0,
                          disabled: directoryQuery.isFetching, onChange: setDirectoryPageNo,
                        } : false}
                      />
                    </section>
                  ) : null}

                  <section className={CARD}>
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                      <SectionTitle>{module === "FIELD" ? "字段标准列表" : module === "CODE" ? "标准代码列表" : "度量单位列表"}</SectionTitle>
                      <div className="flex items-center gap-2">
                        <Input size="small" variant="outlined" value={recordKeyword}
                          placeholder={"搜索" + title} onChange={(event) => setRecordKeyword(event.target.value)} />
                        <Button size="sm" variant="primary" onClick={() => openCreateRecord(selectedNode.id)}>
                          <Plus size={13} />新增
                        </Button>
                      </div>
                    </div>
                    <Table<StandardRecord>
                      columns={recordColumns}
                      dataSource={recordQuery.data?.bizData || []}
                      rowKey={(item) => item.id || ""}
                      loading={recordQuery.isPending || recordQuery.isFetching}
                      bordered size="small" emptyText={"暂无" + title}
                      pagination={(recordQuery.data?.pagination.total || 0) > 0 ? {
                        current: recordPageNo, pageSize: PAGE_SIZE,
                        total: recordQuery.data?.pagination.total || 0,
                        disabled: recordQuery.isFetching, onChange: setRecordPageNo,
                      } : false}
                    />
                  </section>
                </>
              ) : (
                <StandardRecordDetails
                  module={module}
                  record={selectedRecord!}
                  directoryName={breadcrumb.length > 1 ? breadcrumb[breadcrumb.length - 2] : undefined}
                />
              )}
            </div>
          )}
        </section>
      </main>

      {contextMenu ? (
        <div
          className="fixed z-[100] min-w-[164px] rounded-md border border-[#e4e7ec] bg-white p-1 shadow-[0_8px_24px_rgba(16,24,40,0.14)]"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}
        >
          {isDirectoryNode(contextMenu.node) ? (
            <>
              {contextMenu.node.type === "FOLDER" ? (
                <>
                  <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                    onClick={() => openCreateDirectory(contextMenu.node.id)}>
                    <Plus size={13} />新建子目录
                  </button>
                  {module === "FIELD" ? (
                    <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                      onClick={() => openCreateDirectory(contextMenu.node.id, "STANDARD_SET")}>
                      <Layers size={13} />新建标准集
                    </button>
                  ) : null}
                </>
              ) : null}
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                onClick={() => openCreateRecord(contextMenu.node.id)}>
                <Plus size={13} />新建{module === "FIELD" ? "字段标准" : module === "CODE" ? "标准代码" : "度量单位"}
              </button>
              <div className="my-1 border-t border-[#eef0f3]" />
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                onClick={() => void openEditDirectory(contextMenu.node.id)}>
                <Pencil size={13} />编辑目录
              </button>
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
                onClick={() => {
                  setPendingDelete({ id: contextMenu.node.id, name: contextMenu.node.title, resource: "standardDirectory", directory: true });
                  setContextMenu(undefined);
                }}>
                <Trash2 size={13} />删除目录
              </button>
            </>
          ) : (
            <>
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#344054] hover:bg-[#f5f7fa]"
                onClick={() => void openEditRecord(contextMenu.node.id)}>
                <Pencil size={13} />编辑
              </button>
              <button type="button" className="flex w-full cursor-pointer items-center gap-2 rounded px-2.5 py-2 text-left text-xs text-[#d92d20] hover:bg-[#fef3f2]"
                onClick={() => {
                  setPendingDelete({ id: contextMenu.node.id, name: contextMenu.node.title, resource, directory: false });
                  setContextMenu(undefined);
                }}>
                <Trash2 size={13} />删除
              </button>
            </>
          )}
        </div>
      ) : null}

      <StandardDirectoryFormDialog
        open={directoryDialogOpen}
        module={module}
        parentId={directoryParentId}
        editing={editingDirectory}
        initialDirType={initialDirType}
        saving={saving}
        onClose={() => {
          if (!saving) {
            setDirectoryDialogOpen(false);
            setEditingDirectory(undefined);
            setDirectoryParentId(undefined);
          }
        }}
        onSubmit={(values) => void saveDirectory(values)}
      />
      <StandardRecordFormDialog
        open={recordDialogOpen}
        module={module}
        parentId={recordParentId}
        editing={editingRecord}
        saving={saving}
        onClose={() => {
          if (!saving) {
            setRecordDialogOpen(false);
            setEditingRecord(undefined);
            setRecordParentId(undefined);
          }
        }}
        onSubmit={(values) => void saveRecord(values)}
      />
      <Modal
        open={Boolean(pendingDelete)} centered width={420}
        title={pendingDelete?.directory ? "删除目录" : "删除" + title}
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void confirmDelete()}>删除</Button>
        </>}
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete?.name || "当前记录"}”吗？
        </p>
      </Modal>
    </div>
  );
}

export default DirectoryStandardsPage;
