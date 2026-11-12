import { useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import {
  createResourceDirectory,
  createResourceFile,
  deleteResource,
  downloadResource,
  fetchResourceFileContent,
  getResourceBaseDir,
  listResourceItems,
  listWorkflowResources,
  renameResource,
  replaceResourceFile,
  updateResourceFileContent,
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
  resolveCurrentResourceDirectory,
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
  const [createFileOpen, setCreateFileOpen] = useState(false);
  const [createFileName, setCreateFileName] = useState("");
  const [createFileSuffix, setCreateFileSuffix] = useState("sql");
  const [createFileContent, setCreateFileContent] = useState("");
  const [createFileSaving, setCreateFileSaving] = useState(false);
  const [editorItem, setEditorItem] = useState<ResourceItemRecord | null>(null);
  const [editorContent, setEditorContent] = useState("");
  const [editorInitialContent, setEditorInitialContent] = useState("");
  const [editorLoading, setEditorLoading] = useState(false);
  const [editorSaving, setEditorSaving] = useState(false);
  const [editorError, setEditorError] = useState("");
  const editorRequestId = useRef(0);
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

    setCurrentDir((current) =>
      resolveCurrentResourceDirectory(current, baseDir),
    );
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

  const openResourceFile = async (item: ResourceItemRecord) => {
    if (isDirectoryItem(item)) return;

    const requestId = ++editorRequestId.current;
    setEditorItem(item);
    setEditorItem(item);
    setEditorContent("");
    setEditorInitialContent("");
    setEditorError("");
    setEditorLoading(true);

    try {
      const response = await fetchResourceFileContent(item.fullName, 0, -1);
      if (editorRequestId.current !== requestId) return;
      const nextContent = response?.content ?? "";
      setEditorContent(nextContent);
      setEditorInitialContent(nextContent);
    } catch (error) {
      if (editorRequestId.current !== requestId) return;
      setEditorError(
        error instanceof Error ? error.message : "文件内容加载失败",
      );
    } finally {
      if (editorRequestId.current === requestId) setEditorLoading(false);
    }
  };

  const openCreateFile = () => {
    setCreateFileName("");
    setCreateFileSuffix("sql");
    setCreateFileContent("");
    setCreateFileOpen(true);
  };

  const closeCreateFileDialog = () => {
    if (createFileSaving) return;
    setCreateFileOpen(false);
    setCreateFileName("");
    setCreateFileSuffix("sql");
    setCreateFileContent("");
  };

  const saveNewResourceFile = async () => {
    if (createFileSaving) return;

    const typedName = createFileName.trim();
    const baseName =
      getExtension(typedName) === createFileSuffix
        ? typedName.slice(0, -(createFileSuffix.length + 1)).trim()
        : typedName;

    if (!baseName) {
      toast.warning("请输入文件名称");
      return;
    }
    if (/[\\/]/.test(baseName)) {
      toast.warning("文件名称不能包含路径分隔符");
      return;
    }
    if (!createFileContent.length) {
      toast.warning("文件内容不能为空");
      return;
    }
    if (!currentDir) {
      toast.warning("资源目录尚未加载，请稍后重试");
      return;
    }

    setCreateFileSaving(true);
    try {
      await createResourceFile(
        baseName,
        createFileSuffix,
        createFileContent,
        currentDir,
      );
      toast.success("文件创建成功");
      setCreateFileOpen(false);
      setCreateFileName("");
      setCreateFileSuffix("sql");
      setCreateFileContent("");
      setPageNo(1);
      await invalidateResources();
    } catch {
      // The shared HTTP layer displays request and business errors.
    } finally {
      setCreateFileSaving(false);
    }
  };

  const closeResourceEditor = () => {
    editorRequestId.current += 1;
    setEditorItem(null);
    setEditorContent("");
    setEditorInitialContent("");
    setEditorError("");
    setEditorLoading(false);
  };

  const saveResourceEditor = async () => {
    if (!editorItem || editorLoading || editorSaving) return;

    if (!editorItem) return;
    if (!editorContent.length) {
      toast.warning("文件内容不能为空");
      return;
    }

    setEditorSaving(true);
    try {
      await updateResourceFileContent(editorItem.fullName, editorContent);
      setEditorInitialContent(editorContent);
      toast.success("文件保存成功");
      await queryClient.invalidateQueries({ queryKey: RESOURCE_QUERY_KEY });
    } catch {
      // The shared HTTP layer displays request and business errors.
    } finally {
      setEditorSaving(false);
    }
  };

  const replaceFile = async (item: ResourceItemRecord, file: File) => {
    const currentName = getName(item);
    const currentDot = currentName.lastIndexOf(".");
    const replacementDot = file.name.lastIndexOf(".");
    const currentExtension =
      currentDot > 0 && currentDot < currentName.length - 1
        ? currentName.slice(currentDot + 1)
        : "";
    const replacementExtension =
      replacementDot > 0 && replacementDot < file.name.length - 1
        ? file.name.slice(replacementDot + 1)
        : "";
    if (!currentExtension || currentExtension !== replacementExtension) {
      toast.warning("替换文件的扩展名必须与原文件一致");
      return;
    }

    try {
      await replaceResourceFile(item.fullName, getName(item), file);
      toast.success("文件替换成功");
      await invalidateResources();
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

  const selectItem = (item: ResourceItemRecord) => {
    setSelectedFullNames(new Set([item.fullName]));
  };

  const copyResourcePath = async (item: ResourceItemRecord) => {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard API unavailable");
      }
      await navigator.clipboard.writeText(item.fullName);
      toast.success("资源路径已复制");
    } catch {
      toast.error("复制失败，请检查浏览器剪贴板权限");
    }
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

  const openParentDirectory = () => {
    if (directoryPath.length <= 1) {
      openRootDirectory();
      return;
    }

    const parent = directoryPath[directoryPath.length - 2];
    if (!parent?.fullName) {
      openRootDirectory();
      return;
    }

    setCurrentDir(parent.fullName);
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
    createFileOpen,
    createFileName,
    createFileSuffix,
    createFileContent,
    createFileSaving,
    editorItem,
    editorContent,
    editorInitialContent,
    editorLoading,
    editorSaving,
    editorError,
    isEditorDirty: editorContent !== editorInitialContent,
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
    openParentDirectory,
    canGoParent: Boolean(currentDir && baseDirQuery.data && currentDir !== baseDirQuery.data),
    openCreateDialog,
    openRenameDialog,
    openDeleteDialog,
    openDeleteSelectedDialog,
    setDialogValue,
    closeDialog,
    confirmDialog,
    uploadFiles: handleUpload,
    downloadFile: handleDownload,
    openResourceFile,
    openCreateFile,
    closeCreateFileDialog,
    saveNewResourceFile,
    setCreateFileName,
    setCreateFileSuffix,
    setCreateFileContent,
    closeResourceEditor,
    saveResourceEditor,
    setEditorContent,
    replaceFile,
    refresh,
    retryItems: () => itemsQuery.refetch(),
    toggleItemSelection,
    selectItem,
    copyResourcePath,
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
