import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

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

import { DEFAULT_PAGE_SIZE, RESOURCE_QUERY_KEY } from "../constants";
import type {
  ResourceDialog,
  ResourceSort,
  ResourceTypeFilter,
  ViewMode,
} from "../types";
import {
  findDirectoryPath,
  getExtension,
  getName,
  isDirectoryItem,
  matchesTypeFilter,
} from "../utils";

export function useResourceManagement() {
  const queryClient = useQueryClient();
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
    const baseDir = baseDirQuery.data;

    if (!baseDir) return;

    const normalizePath = (path: string) =>
      path.replace(/[\\/]+/g, "\\").replace(/\\+$/, "").toLowerCase();

    const normalizedBase = normalizePath(baseDir);

    setCurrentDir((current) => {
      if (!current) return baseDir;

      const normalizedCurrent = normalizePath(current);
      const isWithinBase =
        normalizedCurrent === normalizedBase ||
        normalizedCurrent.startsWith(`${normalizedBase}\\`);

      return isWithinBase ? current : baseDir;
    });
  }, [baseDirQuery.data]);

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
      // The shared HTTP layer displays request and business errors.
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
      // The shared HTTP layer displays request and business errors.
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

  const openCreateDialog = () => {
    setDialogValue("");
    setDialog({ kind: "create" });
  };

  const openDeleteDialog = (item: ResourceItemRecord) => {
    setDialog({ kind: "delete", item });
  };

  const openDeleteSelectedDialog = () => {
    if (selectedItems.length > 0) {
      setDialog({ kind: "delete-selected", items: selectedItems });
    }
  };

  const openRootDirectory = () => {
    if (!baseDirQuery.data) return;
    setCurrentDir(baseDirQuery.data);
    setPageNo(1);
    setSelectedFullNames(new Set());
  };

  const handleKeywordChange = (value: string) => {
    setKeyword(value);
    setPageNo(1);
    setSelectedFullNames(new Set());
  };

  const clearKeyword = () => {
    setKeyword("");
    setPageNo(1);
    setSelectedFullNames(new Set());
  };

  const handleTypeFilterChange = (value: ResourceTypeFilter) => {
    setTypeFilter(value);
    setPageNo(1);
    setSelectedFullNames(new Set());
  };

  const clearSelection = () => setSelectedFullNames(new Set());

  const handleSelectionChange = (keys: Set<string>) => {
    setSelectedFullNames(keys);
  };

  const handlePageChange = (nextPage: number, nextPageSize: number) => {
    setPageNo(nextPage);
    setPageSize(nextPageSize);
    setSelectedFullNames(new Set());
  };

  const closeDialog = () => {
    if (actionPending) return;
    setDialog(null);
    setDialogValue("");
  };

  const isLoading = baseDirQuery.isPending || itemsQuery.isPending;

  return {
    currentDir,
    keyword,
    pageNo,
    pageSize,
    typeFilter,
    sortBy,
    viewMode,
    selectedFullNames,
    selectedItems,
    dialog,
    dialogValue,
    actionPending,
    uploading,
    directoryPath,
    visibleItems,
    total,
    deferredKeyword,
    isLoading,
    isFetching: itemsQuery.isFetching,
    isTreeError: treeQuery.isError,
    isBaseDirError: baseDirQuery.isError,
    isItemsError: itemsQuery.isError,
    openDirectory,
    openBreadcrumb,
    openRootDirectory,
    openCreateDialog,
    openRenameDialog,
    openDeleteDialog,
    openDeleteSelectedDialog,
    setDialogValue,
    closeDialog,
    confirmDialog,
    uploadFiles: handleUpload,
    downloadFile: handleDownload,
    refresh,
    retryItems: () => itemsQuery.refetch(),
    toggleItemSelection,
    handleSelectionChange,
    handleKeywordChange,
    clearKeyword,
    handleTypeFilterChange,
    clearSelection,
    handlePageChange,
    setSortBy,
    setViewMode,
  };
}
