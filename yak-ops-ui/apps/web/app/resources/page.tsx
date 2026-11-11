import { useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Archive,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Download,
  File,
  FileCode2,
  FileImage,
  FileSpreadsheet,
  FileText,
  Folder,
  FolderOpen,
  FolderPlus,
  LoaderCircle,
  Pencil,
  RefreshCw,
  Search,
  Trash2,
  Upload,
} from "lucide-react";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  PageHeader,
  toast,
} from "@yak-ops/yak-ui";

import {
  createResourceDirectory,
  deleteResource,
  downloadResource,
  getResourceBaseDir,
  listResourceItems,
  listWorkflowResources,
  renameResource,
  uploadResourceFile,
  type ResourceItemRecord,
  type WorkflowResourceNode,
} from "@/service/resources";

const RESOURCE_QUERY_KEY = ["resource-management"] as const;
const PAGE_SIZE = 20;

type ResourceDialog =
  | { kind: "create" }
  | { kind: "rename"; item: ResourceItemRecord }
  | { kind: "delete"; item: ResourceItemRecord };

const isDirectoryNode = (node: WorkflowResourceNode) =>
  Boolean(
    node.isDirctory ??
      node.dirctory ??
      node.isDirectory ??
      (node.children && node.children.length > 0),
  );

const isDirectoryItem = (item: ResourceItemRecord) =>
  Boolean(item.isDirectory ?? item.directory ?? item.isDirctory);

const getName = (item: ResourceItemRecord) => {
  const pathParts = item.fullName.split(/[\\/]/).filter(Boolean);
  return item.fileName || item.alias || pathParts[pathParts.length - 1] || "未命名资源";
};

const getExtension = (name: string) => {
  const dot = name.lastIndexOf(".");
  return dot > 0 && dot < name.length - 1 ? name.slice(dot + 1).toLowerCase() : "";
};

const formatSize = (size?: number) => {
  if (size === undefined || size <= 0) return "—";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`;
};

const formatDate = (value?: string | number | Date) => {
  if (!value) return "—";
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
};

const getFileIcon = (name: string) => {
  const extension = getExtension(name);
  if (["jar", "zip", "gz", "tar", "war", "7z"].includes(extension)) return Archive;
  if (["sql", "py", "java", "js", "ts", "sh", "json", "xml", "yml", "yaml"].includes(extension)) return FileCode2;
  if (["xlsx", "xls", "csv"].includes(extension)) return FileSpreadsheet;
  if (["png", "jpg", "jpeg", "gif", "webp", "svg"].includes(extension)) return FileImage;
  if (["md", "txt", "log", "properties", "conf"].includes(extension)) return FileText;
  return File;
};

const findDirectoryPath = (
  nodes: WorkflowResourceNode[],
  target: string,
  ancestors: WorkflowResourceNode[] = [],
): WorkflowResourceNode[] | null => {
  for (const node of nodes) {
    if (!isDirectoryNode(node)) continue;
    const next = [...ancestors, node];
    if (node.fullName === target) return next;
    const nested = findDirectoryPath(node.children ?? [], target, next);
    if (nested) return nested;
  }
  return null;
};

function DirectoryBranch({
  nodes,
  level,
  currentDir,
  expandedDirectories,
  onSelect,
  onToggle,
}: {
  nodes: WorkflowResourceNode[];
  level: number;
  currentDir: string;
  expandedDirectories: Set<string>;
  onSelect: (node: WorkflowResourceNode) => void;
  onToggle: (path: string) => void;
}) {
  return (
    <>
      {nodes.filter(isDirectoryNode).map((node) => {
        const path = node.fullName ?? "";
        const children = (node.children ?? []).filter(isDirectoryNode);
        const expanded = expandedDirectories.has(path);
        const selected = path === currentDir;

        return (
          <div key={path || node.name}>
            <div
              className={[
                "group flex h-8 items-center rounded-md pr-2 text-[12px] transition-colors",
                selected
                  ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                  : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
              ].join(" ")}
              style={{ paddingLeft: 6 + level * 14 }}
            >
              {children.length > 0 ? (
                <button
                  type="button"
                  aria-label={expanded ? "折叠文件夹" : "展开文件夹"}
                  title={expanded ? "折叠" : "展开"}
                  onClick={() => onToggle(path)}
                  className="flex size-5 shrink-0 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-black/5"
                >
                  {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                </button>
              ) : (
                <span className="size-5 shrink-0" />
              )}
              <button
                type="button"
                onClick={() => onSelect(node)}
                className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 text-left"
              >
                {expanded ? (
                  <FolderOpen size={15} className="shrink-0 text-[#d6a34a]" />
                ) : (
                  <Folder size={15} className="shrink-0 text-[#d6a34a]" />
                )}
                <span className="min-w-0 flex-1 truncate">{node.name || "未命名文件夹"}</span>
              </button>
            </div>
            {expanded && children.length > 0 ? (
              <DirectoryBranch
                nodes={children}
                level={level + 1}
                currentDir={currentDir}
                expandedDirectories={expandedDirectories}
                onSelect={onSelect}
                onToggle={onToggle}
              />
            ) : null}
          </div>
        );
      })}
    </>
  );
}

export function ResourceManagementPage() {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [currentDir, setCurrentDir] = useState("");
  const [expandedDirectories, setExpandedDirectories] = useState<Set<string>>(
    () => new Set(),
  );
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [dialog, setDialog] = useState<ResourceDialog | null>(null);
  const [dialogValue, setDialogValue] = useState("");
  const [actionPending, setActionPending] = useState(false);
  const [uploading, setUploading] = useState(false);
  const deferredKeyword = useDeferredValue(keyword.trim());

  const baseDirQuery = useQuery({
    queryKey: [...RESOURCE_QUERY_KEY, "base-dir"],
    queryFn: ({ signal }) => getResourceBaseDir(signal),
  });

  const treeQuery = useQuery({
    queryKey: [...RESOURCE_QUERY_KEY, "tree"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
  });

  useEffect(() => {
    if (baseDirQuery.data && !currentDir) {
      setCurrentDir(baseDirQuery.data);
    }
  }, [baseDirQuery.data, currentDir]);

  const itemsQuery = useQuery({
    queryKey: [
      ...RESOURCE_QUERY_KEY,
      "items",
      currentDir,
      deferredKeyword,
      pageNo,
      PAGE_SIZE,
    ],
    queryFn: ({ signal }) =>
      listResourceItems(
        {
          fullName: currentDir,
          searchVal: deferredKeyword,
          pageNo,
          pageSize: PAGE_SIZE,
        },
        signal,
      ),
    enabled: Boolean(currentDir),
  });

  const treeNodes = treeQuery.data ?? [];
  const directoryPath = useMemo(
    () => findDirectoryPath(treeNodes, currentDir) ?? [],
    [treeNodes, currentDir],
  );
  const resourceItems = useMemo(
    () =>
      [...(itemsQuery.data?.totalList ?? [])].sort((left, right) => {
        const leftDirectory = isDirectoryItem(left);
        const rightDirectory = isDirectoryItem(right);
        if (leftDirectory !== rightDirectory) return leftDirectory ? -1 : 1;
        return getName(left).localeCompare(getName(right), "zh-CN");
      }),
    [itemsQuery.data?.totalList],
  );

  const total = itemsQuery.data?.total ?? 0;
  const totalPage = Math.max(
    1,
    itemsQuery.data?.totalPage ?? Math.ceil(total / PAGE_SIZE),
  );

  useEffect(() => {
    if (
      itemsQuery.isSuccess &&
      resourceItems.length === 0 &&
      total > 0 &&
      pageNo > totalPage
    ) {
      setPageNo(totalPage);
    }
  }, [itemsQuery.isSuccess, resourceItems.length, total, pageNo, totalPage]);

  const invalidateResources = async () => {
    await queryClient.invalidateQueries({ queryKey: RESOURCE_QUERY_KEY });
  };

  const openDirectory = (node: WorkflowResourceNode) => {
    if (!node.fullName) return;
    const path = findDirectoryPath(treeNodes, node.fullName) ?? [];
    setExpandedDirectories((current) => {
      const next = new Set(current);
      path.forEach((item) => {
        if (item.fullName) next.add(item.fullName);
      });
      return next;
    });
    setCurrentDir(node.fullName);
    setPageNo(1);
  };

  const toggleDirectory = (path: string) => {
    setExpandedDirectories((current) => {
      const next = new Set(current);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  };

  const handleUpload = async (file?: File) => {
    if (!file || !currentDir || uploading) return;
    setUploading(true);
    try {
      await uploadResourceFile(file, currentDir);
      toast.success("文件上传成功");
      setPageNo(1);
      await invalidateResources();
    } catch {
      // The HTTP layer displays request and business errors.
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const openRenameDialog = (item: ResourceItemRecord) => {
    setDialogValue(getName(item));
    setDialog({ kind: "rename", item });
  };

  const confirmDialog = async () => {
    if (!dialog || actionPending) return;

    if (dialog.kind !== "delete") {
      const name = dialogValue.trim();
      if (!name) {
        toast.warning("请输入名称");
        return;
      }
      if (/[\\/]/.test(name)) {
        toast.warning("名称不能包含路径分隔符");
        return;
      }
    }

    setActionPending(true);
    try {
      if (dialog.kind === "create") {
        await createResourceDirectory(dialogValue.trim(), currentDir);
        toast.success("文件夹创建成功");
      } else if (dialog.kind === "rename") {
        let nextName = dialogValue.trim();
        const originalName = getName(dialog.item);
        if (!isDirectoryItem(dialog.item)) {
          const extension = getExtension(originalName);
          if (extension && !getExtension(nextName)) {
            nextName += `.${extension}`;
          }
        }
        await renameResource(dialog.item.fullName, nextName);
        toast.success("资源重命名成功");
      } else {
        await deleteResource(dialog.item.fullName);
        toast.success("资源删除成功");
      }

      setDialog(null);
      setDialogValue("");
      setPageNo(1);
      await invalidateResources();
    } catch {
      // Keep the dialog open so the user can review and retry.
    } finally {
      setActionPending(false);
    }
  };

  const handleDownload = async (item: ResourceItemRecord) => {
    try {
      const blob = await downloadResource(item.fullName);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = getName(item);
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      // The HTTP layer displays request and business errors.
    }
  };

  const refresh = async () => {
    await Promise.all([baseDirQuery.refetch(), treeQuery.refetch(), itemsQuery.refetch()]);
    toast.success("资源列表已刷新");
  };

  const isLoading =
    baseDirQuery.isPending ||
    treeQuery.isPending ||
    itemsQuery.isPending ||
    itemsQuery.isFetching;

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader
          title="资源管理"
          bordered
          className="bg-white px-6 max-md:px-4"
        />

        <div className="flex min-h-0 flex-1 px-6 pb-5 pt-5 max-md:px-3">
          <section className="flex min-h-[560px] min-w-0 flex-1 overflow-hidden border border-[var(--yak-components-border)] bg-white">
            <aside className="flex w-[248px] shrink-0 flex-col border-r border-[var(--yak-components-border)] max-md:w-[190px]">
              <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-border)] px-4">
                <span className="text-[13px] font-semibold">资源目录</span>
                <button
                  type="button"
                  title="刷新目录"
                  aria-label="刷新目录"
                  onClick={() => void refresh()}
                  className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
                >
                  <RefreshCw size={14} />
                </button>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto p-2">
                <button
                  type="button"
                  onClick={() => {
                    if (baseDirQuery.data) {
                      setCurrentDir(baseDirQuery.data);
                      setPageNo(1);
                    }
                  }}
                  className={[
                    "flex h-8 w-full cursor-pointer items-center gap-2 rounded-md px-2 text-left text-[12px] transition-colors",
                    currentDir === baseDirQuery.data
                      ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
                      : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]",
                  ].join(" ")}
                >
                  <FolderOpen size={15} className="shrink-0 text-[#d6a34a]" />
                  <span className="min-w-0 flex-1 truncate">资源文件</span>
                </button>

                {treeQuery.isPending ? (
                  <div className="flex items-center gap-2 px-3 py-4 text-xs text-[var(--yak-components-muted-text)]">
                    <LoaderCircle size={14} className="animate-spin" />
                    正在加载目录
                  </div>
                ) : treeQuery.isError ? (
                  <div className="px-3 py-4 text-xs text-[var(--yak-color-danger)]">
                    目录加载失败
                    <button
                      type="button"
                      onClick={() => void treeQuery.refetch()}
                      className="ml-2 cursor-pointer text-[var(--yak-color-primary)] hover:underline"
                    >
                      重试
                    </button>
                  </div>
                ) : (
                  <DirectoryBranch
                    nodes={treeNodes}
                    level={0}
                    currentDir={currentDir}
                    expandedDirectories={expandedDirectories}
                    onSelect={openDirectory}
                    onToggle={toggleDirectory}
                  />
                )}
              </div>

              <div className="border-t border-[var(--yak-components-border)] px-4 py-3 text-[11px] text-[var(--yak-components-muted-text)]">
                资源按当前租户统一管理
              </div>
            </aside>

            <main className="flex min-w-0 flex-1 flex-col">
              <div className="flex min-h-[58px] shrink-0 flex-wrap items-center justify-between gap-3 border-b border-[var(--yak-components-border)] px-4 py-3">
                <nav aria-label="资源路径" className="flex min-w-0 items-center gap-1 text-[12px]">
                  <button
                    type="button"
                    onClick={() => {
                      if (baseDirQuery.data) {
                        setCurrentDir(baseDirQuery.data);
                        setPageNo(1);
                      }
                    }}
                    className="shrink-0 cursor-pointer text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
                  >
                    资源文件
                  </button>
                  {directoryPath.map((node, index) => (
                    <span key={node.fullName || node.name} className="flex min-w-0 items-center gap-1">
                      <ChevronRight size={13} className="shrink-0 text-[var(--yak-components-muted-text)]" />
                      <button
                        type="button"
                        onClick={() => openDirectory(node)}
                        className={[
                          "min-w-0 max-w-36 truncate cursor-pointer",
                          index === directoryPath.length - 1
                            ? "font-medium text-[var(--yak-components-panel-text)]"
                            : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]",
                        ].join(" ")}
                      >
                        {node.name}
                      </button>
                    </span>
                  ))}
                </nav>

                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <div className="flex h-8 w-52 items-center gap-2 rounded-md border border-[var(--yak-components-input-border)] px-2.5 focus-within:border-[var(--yak-components-input-border-focus)]">
                    <Search size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
                    <Input
                      size="small"
                      variant="filled"
                      aria-label="搜索当前目录"
                      placeholder="搜索当前目录"
                      value={keyword}
                      onChange={(event) => {
                        setKeyword(event.target.value);
                        setPageNo(1);
                      }}
                      className="h-full min-w-0 flex-1 rounded-none border-0 bg-transparent px-0 text-xs shadow-none hover:border-transparent hover:bg-transparent focus:border-transparent focus:bg-transparent"
                    />
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    isDisabled={!currentDir || actionPending || uploading}
                    onClick={() => {
                      setDialogValue("");
                      setDialog({ kind: "create" });
                    }}
                  >
                    <FolderPlus size={14} />
                    新建文件夹
                  </Button>
                  <Button
                    size="sm"
                    isDisabled={!currentDir || actionPending || uploading}
                    isPending={uploading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Upload size={14} />
                    上传文件
                  </Button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    className="hidden"
                    onChange={(event) => void handleUpload(event.target.files?.[0])}
                  />
                </div>
              </div>

              {baseDirQuery.isError ? (
                <div className="flex flex-1 items-center justify-center text-sm text-[var(--yak-color-danger)]">
                  资源目录初始化失败，请刷新重试
                </div>
              ) : itemsQuery.isError ? (
                <div className="flex flex-1 items-center justify-center gap-3 text-sm text-[var(--yak-components-muted-text)]">
                  文件列表加载失败
                  <Button size="sm" variant="secondary" onClick={() => void itemsQuery.refetch()}>
                    重试
                  </Button>
                </div>
              ) : isLoading && resourceItems.length === 0 ? (
                <div className="flex flex-1 items-center justify-center gap-2 text-sm text-[var(--yak-components-muted-text)]">
                  <LoaderCircle size={16} className="animate-spin" />
                  正在加载资源
                </div>
              ) : resourceItems.length === 0 ? (
                <div className="flex flex-1 flex-col items-center justify-center gap-2 text-sm text-[var(--yak-components-muted-text)]">
                  <FolderOpen size={30} strokeWidth={1.3} />
                  <span>{deferredKeyword ? "没有找到匹配的资源" : "当前目录暂无资源"}</span>
                  <span className="text-xs">上传文件或新建文件夹，开始管理资源</span>
                </div>
              ) : (
                <div className="min-h-0 flex-1 overflow-auto">
                  <table className="w-full min-w-[680px] border-collapse text-left text-[12px]">
                    <thead className="sticky top-0 z-10 bg-[#fafafa] text-[var(--yak-components-muted-text)]">
                      <tr className="h-10 border-b border-[var(--yak-components-border)]">
                        <th className="px-4 font-medium">名称</th>
                        <th className="w-28 px-3 font-medium">类型</th>
                        <th className="w-28 px-3 font-medium">大小</th>
                        <th className="w-44 px-3 font-medium">修改时间</th>
                        <th className="w-28 px-4 text-right font-medium">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {resourceItems.map((item) => {
                        const directory = isDirectoryItem(item);
                        const name = getName(item);
                        const Icon = directory ? Folder : getFileIcon(name);

                        return (
                          <tr
                            key={item.fullName}
                            onDoubleClick={() => {
                              if (directory) openDirectory({
                                name,
                                fullName: item.fullName,
                                isDirectory: true,
                                children: [],
                              });
                            }}
                            className={[
                              "group h-11 border-b border-[var(--yak-components-border)] last:border-b-0 hover:bg-[#fafbfc]",
                              directory ? "cursor-pointer" : "",
                            ].join(" ")}
                          >
                            <td className="max-w-0 px-4">
                              <div className="flex min-w-0 items-center gap-2.5">
                                <Icon
                                  size={16}
                                  className={[
                                    "shrink-0",
                                    directory ? "text-[#d6a34a]" : "text-[var(--yak-components-muted-text)]",
                                  ].join(" ")}
                                />
                                <span className="min-w-0 truncate font-medium text-[var(--yak-components-panel-text)]" title={name}>
                                  {name}
                                </span>
                              </div>
                            </td>
                            <td className="px-3 text-[var(--yak-components-muted-text)]">
                              {directory ? "文件夹" : getExtension(name).toUpperCase() || "文件"}
                            </td>
                            <td className="px-3 tabular-nums text-[var(--yak-components-muted-text)]">
                              {directory ? "—" : formatSize(item.size)}
                            </td>
                            <td className="px-3 text-[var(--yak-components-muted-text)]">
                              {formatDate(item.updateTime)}
                            </td>
                            <td className="px-4">
                              <div className="flex items-center justify-end gap-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
                                {!directory ? (
                                  <button
                                    type="button"
                                    title="下载"
                                    aria-label={`下载${name}`}
                                    onClick={() => void handleDownload(item)}
                                    className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
                                  >
                                    <Download size={14} />
                                  </button>
                                ) : null}
                                <button
                                  type="button"
                                  title="重命名"
                                  aria-label={`重命名${name}`}
                                  onClick={() => openRenameDialog(item)}
                                  className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
                                >
                                  <Pencil size={14} />
                                </button>
                                <button
                                  type="button"
                                  title="删除"
                                  aria-label={`删除${name}`}
                                  onClick={() => setDialog({ kind: "delete", item })}
                                  className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-danger)]"
                                >
                                  <Trash2 size={14} />
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}

              <footer className="flex h-12 shrink-0 items-center justify-between border-t border-[var(--yak-components-border)] px-4 text-[11px] text-[var(--yak-components-muted-text)]">
                <span>共 {total} 个资源</span>
                <div className="flex items-center gap-2">
                  <span className="tabular-nums">{pageNo} / {totalPage}</span>
                  <button
                    type="button"
                    aria-label="上一页"
                    title="上一页"
                    disabled={pageNo <= 1 || itemsQuery.isFetching}
                    onClick={() => setPageNo((page) => Math.max(1, page - 1))}
                    className="flex size-7 cursor-pointer items-center justify-center rounded border border-[var(--yak-components-border)] disabled:cursor-not-allowed disabled:opacity-35 hover:bg-[var(--yak-color-hover)]"
                  >
                    <ChevronLeft size={14} />
                  </button>
                  <button
                    type="button"
                    aria-label="下一页"
                    title="下一页"
                    disabled={pageNo >= totalPage || itemsQuery.isFetching}
                    onClick={() => setPageNo((page) => Math.min(totalPage, page + 1))}
                    className="flex size-7 cursor-pointer items-center justify-center rounded border border-[var(--yak-components-border)] disabled:cursor-not-allowed disabled:opacity-35 hover:bg-[var(--yak-color-hover)]"
                  >
                    <ChevronRight size={14} />
                  </button>
                </div>
              </footer>
            </main>
          </section>
        </div>
      </div>

      <Dialog
        open={Boolean(dialog)}
        onOpenChange={(open) => {
          if (!open && !actionPending) {
            setDialog(null);
            setDialogValue("");
          }
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">
            {dialog?.kind === "create"
              ? "新建文件夹"
              : dialog?.kind === "rename"
                ? "重命名资源"
                : "删除资源"}
          </DialogTitle>

          {dialog?.kind === "delete" ? (
            <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              {isDirectoryItem(dialog.item)
                ? `确定删除文件夹“${getName(dialog.item)}”及其全部内容吗？此操作无法撤销。`
                : `确定删除文件“${getName(dialog.item)}”吗？此操作无法撤销。`}
            </DialogDescription>
          ) : (
            <div className="mt-4">
              <Input
                size="small"
                aria-label="资源名称"
                placeholder={dialog?.kind === "create" ? "请输入文件夹名称" : "请输入新名称"}
                value={dialogValue}
                onChange={(event) => setDialogValue(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    void confirmDialog();
                  }
                }}
              />
            </div>
          )}

          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              isDisabled={actionPending}
              onClick={() => {
                setDialog(null);
                setDialogValue("");
              }}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant={dialog?.kind === "delete" ? "danger" : undefined}
              isPending={actionPending}
              onClick={() => void confirmDialog()}
            >
              {dialog?.kind === "delete" ? "删除" : "确定"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default ResourceManagementPage;
