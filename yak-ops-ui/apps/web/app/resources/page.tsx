import { useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Archive,
  ArrowDownWideNarrow,
  Check,
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
  Grid2X2,
  List,
  LoaderCircle,
  MoreHorizontal,
  Pencil,
  RefreshCw,
  Search,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Input,
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
const DEFAULT_PAGE_SIZE = 10;

type ResourceTypeFilter =
  | "ALL"
  | "FOLDER"
  | "FILE"
  | "SQL"
  | "JAR"
  | "PYTHON"
  | "SHELL"
  | "IMAGE"
  | "DOCUMENT"
  | "OTHER";

type ResourceSort = "name" | "updated" | "size";
type ViewMode = "list" | "grid";

type ResourceDialog =
  | { kind: "create" }
  | { kind: "rename"; item: ResourceItemRecord }
  | { kind: "delete"; item: ResourceItemRecord }
  | { kind: "delete-selected"; items: ResourceItemRecord[] };

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

const getResourceCategory = (name: string): ResourceTypeFilter => {
  const extension = getExtension(name);
  if (extension === "sql") return "SQL";
  if (extension === "jar") return "JAR";
  if (["py", "python"].includes(extension)) return "PYTHON";
  if (["sh", "bash", "zsh"].includes(extension)) return "SHELL";
  if (["png", "jpg", "jpeg", "gif", "webp", "svg", "bmp"].includes(extension)) return "IMAGE";
  if (["md", "txt", "log", "pdf", "doc", "docx", "properties", "conf", "json", "xml", "yml", "yaml"].includes(extension)) return "DOCUMENT";
  if (extension) return "FILE";
  return "OTHER";
};

const formatSize = (size?: number) => {
  if (size === undefined || size <= 0) return "—";
  if (size < 1024) return size + " B";
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + " KB";
  if (size < 1024 * 1024 * 1024) return (size / (1024 * 1024)).toFixed(1) + " MB";
  return (size / (1024 * 1024 * 1024)).toFixed(1) + " GB";
};

const getDateParts = (value?: string | number | Date) => {
  if (!value) return { date: "—", time: "" };
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return { date: "—", time: "" };
  return {
    date: date.toLocaleDateString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }),
    time: date.toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }),
  };
};

const getFileVisual = (name: string) => {
  const extension = getExtension(name);
  if (["jar", "zip", "gz", "tar", "war", "7z"].includes(extension)) {
    return { Icon: Archive, color: "text-[#2563eb]", background: "bg-[#eff6ff]" };
  }
  if (["sql", "py", "java", "js", "ts", "sh", "json", "xml", "yml", "yaml"].includes(extension)) {
    return { Icon: FileCode2, color: "text-[#2563eb]", background: "bg-[#eff6ff]" };
  }
  if (["xlsx", "xls", "csv"].includes(extension)) {
    return { Icon: FileSpreadsheet, color: "text-[#15966b]", background: "bg-[#eaf8f1]" };
  }
  if (["png", "jpg", "jpeg", "gif", "webp", "svg"].includes(extension)) {
    return { Icon: FileImage, color: "text-[#a855f7]", background: "bg-[#f5edff]" };
  }
  if (["pdf"].includes(extension)) {
    return { Icon: FileText, color: "text-[#e05252]", background: "bg-[#fff0f0]" };
  }
  if (["md", "txt", "log", "properties", "conf", "doc", "docx"].includes(extension)) {
    return { Icon: FileText, color: "text-[#15966b]", background: "bg-[#eaf8f1]" };
  }
  return { Icon: File, color: "text-[#64748b]", background: "bg-[#f1f5f9]" };
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

const matchesTypeFilter = (item: ResourceItemRecord, filter: ResourceTypeFilter) => {
  if (filter === "ALL") return true;
  if (filter === "FOLDER") return isDirectoryItem(item);
  if (filter === "FILE") return !isDirectoryItem(item);
  if (isDirectoryItem(item)) return false;
  const category = getResourceCategory(getName(item));
  return filter === "OTHER" ? category === "OTHER" : category === filter;
};

function ResourceFileIcon({ item, large = false }: { item: ResourceItemRecord; large?: boolean }) {
  if (isDirectoryItem(item)) {
    const Icon = Folder;
    return (
      <span className={[
        "flex shrink-0 items-center justify-center rounded-md bg-[#fff7e6] text-[#e5ad35]",
        large ? "size-10" : "size-8",
      ].join(" ")}>
        <Icon size={large ? 23 : 19} strokeWidth={1.65} />
      </span>
    );
  }

  const visual = getFileVisual(getName(item));
  const Icon = visual.Icon;
  return (
    <span className={[
      "flex shrink-0 items-center justify-center rounded-md",
      visual.background,
      visual.color,
      large ? "size-10" : "size-8",
    ].join(" ")}>
      <Icon size={large ? 21 : 18} strokeWidth={1.65} />
    </span>
  );
}

function ResourceActions({
  item,
  onOpen,
  onDownload,
  onRename,
  onDelete,
}: {
  item: ResourceItemRecord;
  onOpen: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onDelete: (item: ResourceItemRecord) => void;
}) {
  const directory = isDirectoryItem(item);
  const name = getName(item);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={"更多操作：" + name}
        title="更多操作"
        className="inline-flex size-8 cursor-pointer items-center justify-center rounded-md border-0 bg-transparent text-[#89919d] outline-none transition-colors hover:bg-[#f1f3f6] hover:text-[#303846] focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
      >
        <MoreHorizontal size={17} />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" side="bottom" className="w-36">
        {directory ? (
          <DropdownMenuItem onClick={() => onOpen(item)}>
            <FolderOpen size={14} />
            打开文件夹
          </DropdownMenuItem>
        ) : (
          <DropdownMenuItem onClick={() => onDownload(item)}>
            <Download size={14} />
            下载
          </DropdownMenuItem>
        )}
        <DropdownMenuItem onClick={() => onRename(item)}>
          <Pencil size={14} />
          重命名
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem tone="danger" onClick={() => onDelete(item)}>
          <Trash2 size={14} />
          删除
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function ResourceManagementPage() {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [currentDir, setCurrentDir] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [typeFilter, setTypeFilter] = useState<ResourceTypeFilter>("ALL");
  const [sortBy, setSortBy] = useState<ResourceSort>("name");
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [selectedFullNames, setSelectedFullNames] = useState<Set<string>>(() => new Set());
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
      pageSize,
    ],
    queryFn: ({ signal }) =>
      listResourceItems(
        {
          fullName: currentDir,
          searchVal: deferredKeyword,
          pageNo,
          pageSize,
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
    () => itemsQuery.data?.totalList ?? [],
    [itemsQuery.data?.totalList],
  );

  const visibleItems = useMemo(() => {
    const result = resourceItems.filter((item) => matchesTypeFilter(item, typeFilter));
    result.sort((left, right) => {
      const leftDirectory = isDirectoryItem(left);
      const rightDirectory = isDirectoryItem(right);
      if (leftDirectory !== rightDirectory) return leftDirectory ? -1 : 1;

      if (sortBy === "updated") {
        const leftTime = left.updateTime ? new Date(left.updateTime).getTime() : 0;
        const rightTime = right.updateTime ? new Date(right.updateTime).getTime() : 0;
        return rightTime - leftTime;
      }
      if (sortBy === "size") return (right.size ?? 0) - (left.size ?? 0);
      return getName(left).localeCompare(getName(right), "zh-CN");
    });
    return result;
  }, [resourceItems, sortBy, typeFilter]);

  const total = itemsQuery.data?.total ?? 0;
  const totalPage = Math.max(1, itemsQuery.data?.totalPage ?? Math.ceil(total / pageSize));
  const selectedItems = visibleItems.filter((item) => selectedFullNames.has(item.fullName));
  const allVisibleSelected =
    visibleItems.length > 0 &&
    visibleItems.every((item) => selectedFullNames.has(item.fullName));
  const someVisibleSelected = visibleItems.some((item) => selectedFullNames.has(item.fullName));

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
    setSelectedFullNames(new Set());
    await queryClient.invalidateQueries({ queryKey: RESOURCE_QUERY_KEY });
  };

  const openDirectory = (item: ResourceItemRecord | WorkflowResourceNode) => {
    if (!item.fullName) return;
    setCurrentDir(item.fullName);
    setPageNo(1);
    setSelectedFullNames(new Set());
  };

  const openBreadcrumb = (node: WorkflowResourceNode) => {
    if (!node.fullName) return;
    setCurrentDir(node.fullName);
    setPageNo(1);
    setSelectedFullNames(new Set());
  };

  const handleUpload = async (files: File[]) => {
    if (files.length === 0 || !currentDir || uploading) return;
    setUploading(true);
    let uploaded = 0;
    let failed = 0;
    try {
      for (const file of files) {
        try {
          await uploadResourceFile(file, currentDir);
          uploaded += 1;
        } catch {
          failed += 1;
        }
      }
      if (uploaded > 0) {
        toast.success("成功上传 " + uploaded + " 个文件");
        setPageNo(1);
        await invalidateResources();
      }
      if (failed > 0) toast.error(failed + " 个文件上传失败");
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

    if (dialog.kind === "create" || dialog.kind === "rename") {
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
          if (extension && !getExtension(nextName)) nextName += "." + extension;
        }
        await renameResource(dialog.item.fullName, nextName);
        toast.success("资源重命名成功");
      } else if (dialog.kind === "delete") {
        await deleteResource(dialog.item.fullName);
        toast.success("资源删除成功");
      } else {
        let deleted = 0;
        let failed = 0;
        for (const item of dialog.items) {
          try {
            await deleteResource(item.fullName);
            deleted += 1;
          } catch {
            failed += 1;
          }
        }
        if (deleted > 0) toast.success("成功删除 " + deleted + " 项资源");
        if (failed > 0) toast.error(failed + " 项资源删除失败");
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
    await Promise.all([baseDirQuery.refetch(), treeQuery.refetch()]);
    if (currentDir) await itemsQuery.refetch();
    toast.success("资源列表已刷新");
  };

  const toggleItemSelection = (item: ResourceItemRecord) => {
    setSelectedFullNames((current) => {
      const next = new Set(current);
      if (next.has(item.fullName)) next.delete(item.fullName);
      else next.add(item.fullName);
      return next;
    });
  };

  const toggleVisibleSelection = () => {
    setSelectedFullNames((current) => {
      const next = new Set(current);
      if (allVisibleSelected) {
        visibleItems.forEach((item) => next.delete(item.fullName));
      } else {
        visibleItems.forEach((item) => next.add(item.fullName));
      }
      return next;
    });
  };

  const isLoading = baseDirQuery.isPending || itemsQuery.isPending;
  const rangeStart = total === 0 ? 0 : (pageNo - 1) * pageSize + 1;
  const rangeEnd = Math.min(pageNo * pageSize, total);

  const renderResourceCard = (item: ResourceItemRecord) => {
    const directory = isDirectoryItem(item);
    const name = getName(item);
    const date = getDateParts(item.updateTime);
    const selected = selectedFullNames.has(item.fullName);

    return (
      <article
        key={item.fullName}
        className={[
          "group relative flex min-w-0 items-center gap-3 rounded-lg border bg-white p-3 transition-colors",
          selected ? "border-[var(--yak-color-primary)] bg-[#fffafa]" : "border-[#edf0f3] hover:border-[#dfe4ea] hover:bg-[#fcfcfd]",
        ].join(" ")}
        onDoubleClick={() => {
          if (directory) openDirectory(item);
        }}
      >
        <input
          type="checkbox"
          aria-label={"选择" + name}
          checked={selected}
          onChange={() => toggleItemSelection(item)}
          className="size-4 shrink-0 cursor-pointer accent-[var(--yak-color-primary)]"
        />
        <ResourceFileIcon item={item} large />
        <button
          type="button"
          onClick={() => {
            if (directory) openDirectory(item);
          }}
          disabled={!directory}
          title={name}
          className={[
            "min-w-0 flex-1 text-left",
            directory ? "cursor-pointer" : "cursor-default",
          ].join(" ")}
        >
          <span className="block truncate text-[13px] font-medium text-[#303846]">{name}</span>
          <span className="mt-1 block text-[11px] text-[#9aa2ad]">
            {directory ? "文件夹" : formatSize(item.size)}
          </span>
        </button>
        <div className="flex shrink-0 flex-col items-end gap-1">
          <span className="text-[11px] text-[#687386]">{date.date}</span>
          <span className="text-[10px] text-[#a0a7b2]">{date.time}</span>
        </div>
        <ResourceActions
          item={item}
          onOpen={openDirectory}
          onDownload={handleDownload}
          onRename={openRenameDialog}
          onDelete={(resource) => setDialog({ kind: "delete", item: resource })}
        />
      </article>
    );
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-white text-[#252b35]">
        <header className="flex min-h-[76px] shrink-0 items-center justify-between gap-4 border-b border-[#edf0f3] px-7 py-4 max-md:px-4">
          <div className="min-w-0">
            <h1 className="text-[18px] font-semibold tracking-[-0.02em] text-[#202631]">
              资源管理
            </h1>
            <p className="mt-1 text-[11px] text-[#9aa2ad]">
              管理工作流运行所需的文件与脚本
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
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
              multiple
              className="hidden"
              onChange={(event) => void handleUpload(Array.from(event.target.files ?? []))}
            />
          </div>
        </header>

        <main className="flex min-h-0 flex-1 flex-col px-7 pb-5 pt-5 max-md:px-4">
          <div className="mb-4 flex min-h-9 flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 flex-1 items-center gap-3">
              <div className="flex h-9 w-[280px] max-w-full min-w-[180px] items-center gap-2 rounded-md border border-[#e6e9ee] bg-white px-3 transition-colors focus-within:border-[#cbd2dc]">
                <Search size={15} className="shrink-0 text-[#9aa2ad]" />
                <Input
                  size="small"
                  variant="filled"
                  aria-label="搜索资源"
                  placeholder="搜索文件或文件夹"
                  value={keyword}
                  onChange={(event) => {
                    setKeyword(event.target.value);
                    setPageNo(1);
                    setSelectedFullNames(new Set());
                  }}
                  className="h-full min-w-0 flex-1 rounded-none border-0 bg-transparent px-0 text-[12px] shadow-none hover:border-transparent hover:bg-transparent focus:border-transparent focus:bg-transparent"
                />
                {keyword ? (
                  <button
                    type="button"
                    aria-label="清除搜索"
                    title="清除搜索"
                    onClick={() => {
                      setKeyword("");
                      setPageNo(1);
                    }}
                    className="flex size-5 cursor-pointer items-center justify-center rounded text-[#9aa2ad] hover:bg-[#f1f3f6] hover:text-[#3f4855]"
                  >
                    <X size={13} />
                  </button>
                ) : null}
              </div>
              {selectedFullNames.size > 0 ? (
                <div className="flex items-center gap-2 text-[12px] text-[#687386]">
                  <span>已选择 {selectedFullNames.size} 项</span>
                  <button
                    type="button"
                    onClick={() => setSelectedFullNames(new Set())}
                    className="cursor-pointer text-[#8b94a2] hover:text-[#303846]"
                  >
                    取消选择
                  </button>
                  <button
                    type="button"
                    onClick={() => setDialog({ kind: "delete-selected", items: selectedItems })}
                    disabled={selectedItems.length === 0}
                    className="inline-flex h-7 cursor-pointer items-center gap-1 rounded-md px-2 text-[#d94a4a] transition-colors hover:bg-[#fff1f1] disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <Trash2 size={13} />
                    批量删除
                  </button>
                </div>
              ) : null}
            </div>

            <div className="flex shrink-0 items-center gap-2">
              <label className="flex h-8 items-center gap-1.5 rounded-md border border-[#e6e9ee] bg-white px-2.5 text-[11px] text-[#687386]">
                <span>全部类型</span>
                <ChevronDown size={13} className="text-[#9aa2ad]" />
                <select
                  aria-label="资源类型筛选"
                  value={typeFilter}
                  onChange={(event) => {
                    setTypeFilter(event.target.value as ResourceTypeFilter);
                    setSelectedFullNames(new Set());
                  }}
                  className="absolute h-px w-px opacity-0"
                >
                  <option value="ALL">全部类型</option>
                  <option value="FOLDER">文件夹</option>
                  <option value="FILE">所有文件</option>
                  <option value="SQL">SQL</option>
                  <option value="JAR">JAR</option>
                  <option value="PYTHON">Python</option>
                  <option value="SHELL">Shell</option>
                  <option value="IMAGE">图片</option>
                  <option value="DOCUMENT">文档与配置</option>
                  <option value="OTHER">其他</option>
                </select>
              </label>

              <label className="flex h-8 items-center gap-1.5 rounded-md border border-[#e6e9ee] bg-white px-2.5 text-[11px] text-[#687386]">
                <ArrowDownWideNarrow size={13} className="text-[#9aa2ad]" />
                <select
                  aria-label="资源排序"
                  value={sortBy}
                  onChange={(event) => setSortBy(event.target.value as ResourceSort)}
                  className="cursor-pointer border-0 bg-transparent outline-none"
                >
                  <option value="name">名称排序</option>
                  <option value="updated">最近修改</option>
                  <option value="size">文件大小</option>
                </select>
              </label>

              <div className="flex h-8 items-center rounded-md border border-[#e6e9ee] bg-white p-0.5">
                <button
                  type="button"
                  aria-label="列表视图"
                  aria-pressed={viewMode === "list"}
                  title="列表视图"
                  onClick={() => setViewMode("list")}
                  className={[
                    "flex size-7 cursor-pointer items-center justify-center rounded",
                    viewMode === "list" ? "bg-[#eef2f7] text-[#303846]" : "text-[#9aa2ad] hover:bg-[#f6f7f9]",
                  ].join(" ")}
                >
                  <List size={15} />
                </button>
                <button
                  type="button"
                  aria-label="网格视图"
                  aria-pressed={viewMode === "grid"}
                  title="网格视图"
                  onClick={() => setViewMode("grid")}
                  className={[
                    "flex size-7 cursor-pointer items-center justify-center rounded",
                    viewMode === "grid" ? "bg-[#eef2f7] text-[#303846]" : "text-[#9aa2ad] hover:bg-[#f6f7f9]",
                  ].join(" ")}
                >
                  <Grid2X2 size={14} />
                </button>
              </div>

              <button
                type="button"
                aria-label="刷新资源"
                title="刷新"
                onClick={() => void refresh()}
                disabled={isLoading}
                className="flex size-8 cursor-pointer items-center justify-center rounded-md border border-[#e6e9ee] text-[#87909d] transition-colors hover:bg-[#f7f8fa] hover:text-[#303846] disabled:cursor-not-allowed disabled:opacity-50"
              >
                <RefreshCw size={14} className={itemsQuery.isFetching ? "animate-spin" : ""} />
              </button>
            </div>
          </div>

          <nav aria-label="资源路径" className="mb-3 flex min-h-7 min-w-0 items-center gap-1 text-[11px]">
            <button
              type="button"
              onClick={() => {
                if (baseDirQuery.data) {
                  setCurrentDir(baseDirQuery.data);
                  setPageNo(1);
                  setSelectedFullNames(new Set());
                }
              }}
              className={[
                "shrink-0 cursor-pointer rounded px-1.5 py-1 transition-colors hover:bg-[#f4f6f8]",
                directoryPath.length === 0 ? "font-medium text-[#394150]" : "text-[#8b94a2]",
              ].join(" ")}
            >
              资源文件
            </button>
            {directoryPath.map((node, index) => (
              <span key={node.fullName || node.name} className="flex min-w-0 items-center gap-1">
                <ChevronRight size={12} className="shrink-0 text-[#c0c6cf]" />
                <button
                  type="button"
                  onClick={() => openBreadcrumb(node)}
                  className={[
                    "min-w-0 max-w-40 truncate rounded px-1.5 py-1 cursor-pointer transition-colors hover:bg-[#f4f6f8]",
                    index === directoryPath.length - 1 ? "font-medium text-[#394150]" : "text-[#8b94a2]",
                  ].join(" ")}
                >
                  {node.name}
                </button>
              </span>
            ))}
            <span className="ml-auto hidden shrink-0 text-[10px] text-[#a0a7b2] sm:inline">
              {total} 项资源
            </span>
          </nav>

          <section className="flex min-h-[420px] min-w-0 flex-1 flex-col overflow-hidden rounded-lg border border-[#e9edf1] bg-white">
            {treeQuery.isError || baseDirQuery.isError ? (
              <div className="flex flex-1 items-center justify-center gap-3 text-[13px] text-[#87909d]">
                资源目录加载失败
                <Button size="sm" variant="secondary" onClick={() => void refresh()}>
                  重试
                </Button>
              </div>
            ) : itemsQuery.isError ? (
              <div className="flex flex-1 items-center justify-center gap-3 text-[13px] text-[#87909d]">
                文件列表加载失败
                <Button size="sm" variant="secondary" onClick={() => void itemsQuery.refetch()}>
                  重试
                </Button>
              </div>
            ) : isLoading && resourceItems.length === 0 ? (
              <div className="flex flex-1 items-center justify-center gap-2 text-[13px] text-[#9aa2ad]">
                <LoaderCircle size={16} className="animate-spin" />
                正在加载资源
              </div>
            ) : visibleItems.length === 0 ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-2 text-[13px] text-[#9aa2ad]">
                <FolderOpen size={34} strokeWidth={1.3} className="text-[#c4cbd4]" />
                <span>{deferredKeyword ? "没有找到匹配的资源" : typeFilter !== "ALL" ? "当前目录没有符合类型的资源" : "当前目录暂无资源"}</span>
                <span className="text-[11px] text-[#b0b7c1]">
                  上传文件或新建文件夹，开始管理资源
                </span>
              </div>
            ) : viewMode === "grid" ? (
              <div className="min-h-0 flex-1 overflow-auto p-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
                  {visibleItems.map(renderResourceCard)}
                </div>
              </div>
            ) : (
              <div className="min-h-0 flex-1 overflow-auto">
                <table className="w-full min-w-[760px] border-collapse text-left text-[12px]">
                  <thead className="sticky top-0 z-10 bg-[#f6f7f9] text-[#7f8997]">
                    <tr className="h-10 border-b border-[#e9edf1]">
                      <th className="w-11 px-4">
                        <input
                          type="checkbox"
                          aria-label="选择当前页全部资源"
                          checked={allVisibleSelected}
                          ref={(node) => {
                            if (node) node.indeterminate = someVisibleSelected && !allVisibleSelected;
                          }}
                          onChange={toggleVisibleSelection}
                          className="size-3.5 cursor-pointer accent-[var(--yak-color-primary)]"
                        />
                      </th>
                      <th className="px-2 font-medium">名称</th>
                      <th className="w-28 px-3 font-medium">大小</th>
                      <th className="w-28 px-3 font-medium">类型</th>
                      <th className="w-44 px-3 font-medium">修改时间</th>
                      <th className="w-14 px-3 text-center font-medium">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleItems.map((item) => {
                      const directory = isDirectoryItem(item);
                      const name = getName(item);
                      const date = getDateParts(item.updateTime);
                      const selected = selectedFullNames.has(item.fullName);

                      return (
                        <tr
                          key={item.fullName}
                          onDoubleClick={() => {
                            if (directory) openDirectory(item);
                          }}
                          className={[
                            "group h-[58px] border-b border-[#f0f2f5] last:border-b-0 transition-colors hover:bg-[#fafbfc]",
                            selected ? "bg-[#fffafa]" : "",
                            directory ? "cursor-pointer" : "",
                          ].join(" ")}
                        >
                          <td className="px-4">
                            <input
                              type="checkbox"
                              aria-label={"选择" + name}
                              checked={selected}
                              onChange={() => toggleItemSelection(item)}
                              onClick={(event) => event.stopPropagation()}
                              className="size-3.5 cursor-pointer accent-[var(--yak-color-primary)]"
                            />
                          </td>
                          <td className="max-w-0 px-2">
                            <div className="flex min-w-0 items-center gap-3">
                              <ResourceFileIcon item={item} />
                              <button
                                type="button"
                                title={name}
                                disabled={!directory}
                                onClick={() => {
                                  if (directory) openDirectory(item);
                                }}
                                className={[
                                  "min-w-0 truncate text-left text-[12px] font-medium",
                                  directory ? "cursor-pointer text-[#303846] hover:text-[var(--yak-color-primary)]" : "cursor-default text-[#394150]",
                                ].join(" ")}
                              >
                                {name}
                              </button>
                            </div>
                          </td>
                          <td className="px-3 text-[11px] tabular-nums text-[#7f8997]">
                            {directory ? "—" : formatSize(item.size)}
                          </td>
                          <td className="px-3 text-[11px] text-[#7f8997]">
                            {directory ? "文件夹" : getExtension(name).toUpperCase() || "文件"}
                          </td>
                          <td className="px-3">
                            <div className="flex flex-col gap-0.5 text-[11px] text-[#687386]">
                              <span>{date.date}</span>
                              <span className="text-[10px] text-[#a0a7b2]">{date.time}</span>
                            </div>
                          </td>
                          <td className="px-3 text-center">
                            <ResourceActions
                              item={item}
                              onOpen={openDirectory}
                              onDownload={handleDownload}
                              onRename={openRenameDialog}
                              onDelete={(resource) => setDialog({ kind: "delete", item: resource })}
                            />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            <footer className="flex min-h-[48px] shrink-0 flex-wrap items-center justify-between gap-3 border-t border-[#edf0f3] px-4 py-2 text-[11px] text-[#8b94a2]">
              <div className="flex items-center gap-2">
                <span>每页</span>
                <select
                  aria-label="每页显示数量"
                  value={pageSize}
                  onChange={(event) => {
                    setPageSize(Number(event.target.value));
                    setPageNo(1);
                    setSelectedFullNames(new Set());
                  }}
                  className="h-7 cursor-pointer rounded border border-[#e6e9ee] bg-white px-2 text-[11px] text-[#687386] outline-none hover:border-[#cbd2dc]"
                >
                  <option value={10}>10</option>
                  <option value={20}>20</option>
                  <option value={50}>50</option>
                </select>
                <span>条</span>
                <span className="ml-2 tabular-nums">
                  显示 {rangeStart}-{rangeEnd} 条，共 {total} 条
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className="mr-1 tabular-nums">{pageNo} / {totalPage}</span>
                <button
                  type="button"
                  aria-label="上一页"
                  title="上一页"
                  disabled={pageNo <= 1 || itemsQuery.isFetching}
                  onClick={() => {
                    setPageNo((page) => Math.max(1, page - 1));
                    setSelectedFullNames(new Set());
                  }}
                  className="flex size-7 cursor-pointer items-center justify-center rounded border border-[#e6e9ee] text-[#687386] transition-colors hover:bg-[#f6f7f9] disabled:cursor-not-allowed disabled:opacity-35"
                >
                  <ChevronLeft size={14} />
                </button>
                <button
                  type="button"
                  aria-label="下一页"
                  title="下一页"
                  disabled={pageNo >= totalPage || itemsQuery.isFetching}
                  onClick={() => {
                    setPageNo((page) => Math.min(totalPage, page + 1));
                    setSelectedFullNames(new Set());
                  }}
                  className="flex size-7 cursor-pointer items-center justify-center rounded border border-[#e6e9ee] text-[#687386] transition-colors hover:bg-[#f6f7f9] disabled:cursor-not-allowed disabled:opacity-35"
                >
                  <ChevronRight size={14} />
                </button>
              </div>
            </footer>
          </section>
        </main>
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
                : dialog?.kind === "delete-selected"
                  ? "批量删除资源"
                  : "删除资源"}
          </DialogTitle>

          {dialog?.kind === "delete" ? (
            <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              {isDirectoryItem(dialog.item)
                ? "确定删除文件夹“" + getName(dialog.item) + "”及其全部内容吗？此操作无法撤销。"
                : "确定删除文件“" + getName(dialog.item) + "”吗？此操作无法撤销。"}
            </DialogDescription>
          ) : dialog?.kind === "delete-selected" ? (
            <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              确定删除选中的 {dialog.items.length} 项资源吗？删除文件夹时，其下的内容也会一并删除。
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
              variant={dialog?.kind === "delete" || dialog?.kind === "delete-selected" ? "danger" : undefined}
              isPending={actionPending}
              onClick={() => void confirmDialog()}
            >
              {dialog?.kind === "delete" || dialog?.kind === "delete-selected" ? "删除" : "确定"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default ResourceManagementPage;
