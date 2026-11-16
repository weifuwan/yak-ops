import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, toast, type TableColumns } from "@yak-ops/yak-ui";

import { standardDirectoryApi } from "@/service/data-modeling/standards/shared/standard-directory";
import { getStandardRecordApi } from "@/service/data-modeling/standards/shared/standard-record";
import type { StandardDirectoryRecord, StandardModule, StandardRecord, StandardTreeNode } from "../types";
import { getDirectoryTypeLabel, getStandardResource, getStandardTitle, isDirectoryNode } from "../types";
import { standardsQueryKeys } from "../query-keys";
import { DirectoryStandardsTree, filterTree, findNodePath, getExpandableKeys } from "./standard-tree";
import { DirectoryStandardsDetail } from "./standard-detail";
import { StandardDirectoryFormDialog } from "./standard-directory-form-dialog";
import { StandardRecordFormDialog } from "./standard-record-form-dialog";

interface Props { projectCode: string; module: StandardModule; }
type PendingDelete = {
  id: string; name: string;
  resource: "standardDirectory" | "fieldStandard" | "codeStandard" | "unitStandard";
  directory: boolean;
};
const PAGE_SIZE = 10;

export function DirectoryStandardsPage({ projectCode, module }: Props) {
  const queryClient = useQueryClient();
  const title = getStandardTitle(module);
  const resource = getStandardResource(module);
  const recordApi = getStandardRecordApi(resource);
  const splitRef = useRef<HTMLDivElement | null>(null);
  const [leftWidth, setLeftWidth] = useState(280);
  const [resizing, setResizing] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selectedNode, setSelectedNode] = useState<StandardTreeNode>();
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
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
    queryKey: standardsQueryKeys.tree(projectCode, module),
    queryFn: () => standardDirectoryApi.tree(projectCode, module),
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
    queryKey: standardsQueryKeys.detail(projectCode, module, selectedNode?.type, selectedNode?.id),
    queryFn: () => selectedIsDirectory
      ? standardDirectoryApi.getById<StandardDirectoryRecord>(projectCode, selectedNode!.id)
      : recordApi.getById<StandardRecord>(projectCode, selectedNode!.id),
    enabled: Boolean(projectCode && selectedNode?.id && detailResource),
  });

  const directoryQuery = useQuery({
    queryKey: standardsQueryKeys.directories(projectCode, module, selectedNode?.id, directoryPageNo),
    queryFn: () => standardDirectoryApi.page<StandardDirectoryRecord>(projectCode, {
      dirModule: module,
      parentId: selectedNode?.id,
      pageNo: directoryPageNo,
      pageSize: PAGE_SIZE,
    }),
    enabled: Boolean(projectCode && selectedNode?.type === "FOLDER"),
  });

  const recordQuery = useQuery({
    queryKey: standardsQueryKeys.records(projectCode, module, selectedNode?.id, recordPageNo, recordKeyword),
    queryFn: () => recordApi.page<StandardRecord>(projectCode, {
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
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.directoriesPrefix(projectCode, module) });
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.recordsPrefix(projectCode, module) });
  };

  const invalidateModule = async () => {
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.tree(projectCode, module) });
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.detailPrefix(projectCode, module) });
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.directoriesPrefix(projectCode, module) });
    await queryClient.invalidateQueries({ queryKey: standardsQueryKeys.recordsPrefix(projectCode, module) });
  };

  const openCreateDirectory = (parentId?: string, dirType = "FOLDER") => {
    setEditingDirectory(undefined);
    setDirectoryParentId(parentId);
    setInitialDirType(dirType);
    setDirectoryDialogOpen(true);
  };

  const openEditDirectory = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await standardDirectoryApi.getById<StandardDirectoryRecord>(projectCode, id);
      setEditingDirectory(record);
      setDirectoryParentId(record.parentId || undefined);
      setInitialDirType(record.dirType || "FOLDER");
      setDirectoryDialogOpen(true);
    } catch {
      toast.warning("获取目录详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
  };

  const openCreateRecord = (parentId?: string) => {
    setEditingRecord(undefined);
    setRecordParentId(parentId);
    setRecordDialogOpen(true);
  };

  const openEditRecord = async (id: string) => {
    if (!id || openingId) return;
    setOpeningId(id);
    try {
      const record = await recordApi.getById<StandardRecord>(projectCode, id);
      setEditingRecord(record);
      setRecordParentId(record.parentId || undefined);
      setRecordDialogOpen(true);
    } catch {
      toast.warning("获取标准详情失败，请刷新后重试");
    } finally {
      setOpeningId("");
    }
  };

  const saveDirectory = async (values: Record<string, unknown>) => {
    if (saving) return;
    setSaving(true);
    try {
      if (editingDirectory?.id) {
        await standardDirectoryApi.update(projectCode, { ...values, id: editingDirectory.id });
        toast.success("目录已更新");
      } else {
        await standardDirectoryApi.create(projectCode, values);
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
        await recordApi.update(projectCode, { ...values, id: editingRecord.id });
        toast.success(title + "已更新");
      } else {
        await recordApi.create(projectCode, values);
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
      await (pendingDelete.directory ? standardDirectoryApi : recordApi).remove(projectCode, pendingDelete.id);
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
    <div ref={splitRef} className="relative flex h-full min-h-[620px] min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white">
      <DirectoryStandardsTree
        width={leftWidth} title={title} module={module} treeRows={treeRows}
        selectedNode={selectedNode} expandedKeys={expandedKeys} keyword={keyword}
        loading={treeQuery.isPending} error={treeQuery.isError} refreshing={treeQuery.isFetching}
        onKeywordChange={setKeyword} onToggleExpanded={toggleExpanded}
        onRefresh={() => void refresh()} onRetry={() => void treeQuery.refetch()}
        onCreateRoot={() => openCreateDirectory()} onSelect={setSelectedNode}
        onCreateDirectory={(node) => openCreateDirectory(node.id)}
        onCreateStandardSet={(node) => openCreateDirectory(node.id, "STANDARD_SET")}
        onCreateRecord={(node) => openCreateRecord(node.id)}
        onEditDirectory={(node) => void openEditDirectory(node.id)}
        onEditRecord={(node) => void openEditRecord(node.id)}
        onDeleteDirectory={(node) => setPendingDelete({ id: node.id, name: node.title, resource: "standardDirectory", directory: true })}
        onDeleteRecord={(node) => setPendingDelete({ id: node.id, name: node.title, resource, directory: false })}
      />
      <div role="separator" aria-orientation="vertical" aria-label={"调整" + title + "目录宽度"}
        className={["relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>
      <DirectoryStandardsDetail
        module={module} title={title} selectedNode={selectedNode} selectedIsDirectory={selectedIsDirectory}
        detailLoading={detailQuery.isPending} detailError={detailQuery.isError}
        selectedDirectory={selectedDirectory} selectedRecord={selectedRecord} breadcrumb={breadcrumb}
        directoryItems={directoryQuery.data?.totalList || []} directoryTotal={directoryQuery.data?.total || 0}
        directoryLoading={directoryQuery.isPending || directoryQuery.isFetching}
        directoryPageNo={directoryPageNo} onDirectoryPageChange={setDirectoryPageNo} directoryColumns={directoryColumns}
        recordItems={recordQuery.data?.totalList || []} recordTotal={recordQuery.data?.total || 0}
        recordLoading={recordQuery.isPending || recordQuery.isFetching}
        recordPageNo={recordPageNo} onRecordPageChange={setRecordPageNo}
        recordKeyword={recordKeyword}
        onRecordKeywordChange={(value) => { setRecordKeyword(value); setRecordPageNo(1); }}
        recordColumns={recordColumns} onRetry={() => void detailQuery.refetch()}
        onBack={() => setSelectedNode(undefined)}
        onCreateDirectory={(parentId) => openCreateDirectory(parentId)}
        onCreateStandardSet={(parentId) => openCreateDirectory(parentId, "STANDARD_SET")}
        onCreateRecord={(parentId) => openCreateRecord(parentId)}
        onEditDirectory={(id) => void openEditDirectory(id)} onEditRecord={(id) => void openEditRecord(id)}
      />
      <StandardDirectoryFormDialog
        open={directoryDialogOpen} module={module} parentId={directoryParentId}
        editing={editingDirectory} initialDirType={initialDirType} saving={saving}
        onClose={() => { if (!saving) { setDirectoryDialogOpen(false); setEditingDirectory(undefined); setDirectoryParentId(undefined); } }}
        onSubmit={(values) => void saveDirectory(values)}
      />
      <StandardRecordFormDialog
        open={recordDialogOpen} module={module} parentId={recordParentId}
        editing={editingRecord} saving={saving}
        onClose={() => { if (!saving) { setRecordDialogOpen(false); setEditingRecord(undefined); setRecordParentId(undefined); } }}
        onSubmit={(values) => void saveRecord(values)}
      />
      <Modal open={Boolean(pendingDelete)} centered width={420}
        title={pendingDelete?.directory ? "删除目录" : "删除" + title}
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" isPending={deleting} onClick={() => void confirmDelete()}>删除</Button>
        </>}>
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">确定删除“{pendingDelete?.name || "当前记录"}”吗？</p>
      </Modal>
    </div>
  );
}

export default DirectoryStandardsPage;
