import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronRight,
  ExternalLink,
  File,
  FolderClosed,
  LoaderCircle,
  RefreshCw,
  Search,
  X,
} from "lucide-react";
import {
  Button,
  Input,
  Modal,
  PanelHeader,
} from "@yak-ops/yak-ui";

import {
  fetchResourceFileContent,
  listWorkflowResources,
  type WorkflowResourceNode,
} from "@/service/resources";

import { FileTypeIcon } from "../resources/components/file-type-icon";
import { getResourceFileTypeConfig } from "../resources/file-types";
import {
  VirtualTree,
  type VirtualTreeNode,
} from "./virtual-tree";

const RESOURCE_BROWSER_QUERY_KEY = ["resource-browser", "FILE"] as const;
const PREVIEW_LINE_LIMIT = 1000;
const PREVIEWABLE_EXTENSIONS = new Set([
  "sql",
  "py",
  "python",
  "sh",
  "bash",
  "zsh",
  "bat",
  "cmd",
  "cfg",
  "hql",
  "js",
  "mjs",
  "cjs",
  "java",
  "json",
  "jsonc",
  "yaml",
  "yml",
  "xml",
  "properties",
  "txt",
  "log",
  "conf",
  "ini",
  "md",
]);

type ResourceBrowserTreeItem = VirtualTreeNode & {
  kind: "directory" | "file";
  resource: WorkflowResourceNode;
};

const getResourceName = (resource: WorkflowResourceNode) => {
  if (resource.name) return resource.name;

  const pathParts = (resource.fullName || resource.currentDir || "")
    .split(/[\\/]/)
    .filter(Boolean);

  return pathParts[pathParts.length - 1] || "未命名资源";
};

const isDirectoryResource = (resource: WorkflowResourceNode) =>
  Boolean(
    resource.isDirctory ??
      resource.isDirectory ??
      resource.dirctory ??
      (resource.children && resource.children.length > 0),
  );

const getResourceId = (
  resource: WorkflowResourceNode,
  parentPath: string,
  index: number,
) =>
  "resource:" +
  (resource.fullName ||
    resource.currentDir ||
    [parentPath, getResourceName(resource), index].filter(Boolean).join("/"));

const getResourceExtension = (resource: WorkflowResourceNode) => {
  const name = getResourceName(resource);
  const dotIndex = name.lastIndexOf(".");
  return dotIndex > 0 && dotIndex < name.length - 1
    ? name.slice(dotIndex + 1).toLowerCase()
    : "";
};

const canPreviewResource = (resource: WorkflowResourceNode) =>
  PREVIEWABLE_EXTENSIONS.has(getResourceExtension(resource));

const hasMatchingResource = (
  resource: WorkflowResourceNode,
  keyword: string,
): boolean =>
  getResourceName(resource).toLocaleLowerCase().includes(keyword) ||
  (resource.children ?? []).some((child) =>
    hasMatchingResource(child, keyword),
  );

const buildResourceTreeItems = (
  resources: WorkflowResourceNode[],
  expandedIds: Set<string>,
  selectedId: string | null,
  keyword: string,
): ResourceBrowserTreeItem[] => {
  const items: ResourceBrowserTreeItem[] = [];

  const visit = (
    resource: WorkflowResourceNode,
    parentId: string | undefined,
    level: number,
    parentPath: string,
    index: number,
  ) => {
    const name = getResourceName(resource);
    const children = resource.children ?? [];
    const directory = isDirectoryResource(resource);
    const id = getResourceId(resource, parentPath, index);
    const matches = !keyword || name.toLocaleLowerCase().includes(keyword);
    const hasMatchingDescendant =
      Boolean(keyword) &&
      children.some((child) => hasMatchingResource(child, keyword));

    if (keyword && !matches && !hasMatchingDescendant) return;

    const expandable = directory && children.length > 0;
    const expanded =
      expandable &&
      (expandedIds.has(id) || hasMatchingDescendant);

    items.push({
      id,
      parentId,
      level,
      ariaLabel: name,
      expandable,
      expanded,
      selected: selectedId === id,
      kind: directory ? "directory" : "file",
      resource,
    });

    if (expanded) {
      children.forEach((child, childIndex) =>
        visit(child, id, level + 1, resource.fullName || name, childIndex),
      );
    }
  };

  resources.forEach((resource, index) =>
    visit(resource, undefined, 1, "", index),
  );

  return items;
};

function ResourceBrowserIcon({
  item,
}: {
  item: ResourceBrowserTreeItem;
}) {
  if (item.kind === "directory") {
    return (
      <FolderClosed
        size={15}
        strokeWidth={1.65}
        fill="#F7D477"
        color="#D9A441"
        className="block shrink-0"
        aria-hidden="true"
      />
    );
  }

  const extension = getResourceExtension(item.resource);
  if (getResourceFileTypeConfig(extension)) {
    return <FileTypeIcon extension={extension} size={16} />;
  }

  return (
    <File
      size={15}
      strokeWidth={1.55}
      className="block shrink-0 text-[#8993a2]"
      aria-hidden="true"
    />
  );
}

type ResourceBrowserPanelProps = {
  width: number;
  directoryPaths: string[];
  onDirectoryPathsChange: (paths: string[]) => void;
};

export function ResourceBrowserPanel({
  width,
  directoryPaths,
  onDirectoryPathsChange,
}: ResourceBrowserPanelProps) {
  const [search, setSearch] = useState("");
  const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [previewResource, setPreviewResource] =
    useState<WorkflowResourceNode | null>(null);
  const deferredSearch = useDeferredValue(search.trim().toLocaleLowerCase());

  const resourcesQuery = useQuery({
    queryKey: RESOURCE_BROWSER_QUERY_KEY,
    queryFn: ({ signal }) => listWorkflowResources(signal),
    staleTime: 30_000,
  });

  const directoryStack = useMemo(() => {
    const stack: WorkflowResourceNode[] = [];
    let currentItems = resourcesQuery.data ?? [];

    for (const directoryPath of directoryPaths) {
      const directory = currentItems.find(
        (item) =>
          item.fullName === directoryPath ||
          item.currentDir === directoryPath,
      );

      if (!directory || !isDirectoryResource(directory)) break;

      stack.push(directory);
      currentItems = directory.children ?? [];
    }

    return stack;
  }, [directoryPaths, resourcesQuery.data]);

  const currentDirectory =
    directoryStack[directoryStack.length - 1] ?? null;
  const currentResources = currentDirectory
    ? currentDirectory.children ?? []
    : resourcesQuery.data ?? [];

  useEffect(() => {
    if (
      !resourcesQuery.isSuccess ||
      directoryStack.length === directoryPaths.length
    ) {
      return;
    }

    onDirectoryPathsChange(
      directoryStack
        .map((directory) => directory.fullName || directory.currentDir || "")
        .filter(Boolean),
    );
  }, [
    directoryPaths.length,
    directoryStack,
    onDirectoryPathsChange,
    resourcesQuery.isSuccess,
  ]);

  const previewFullName = previewResource?.fullName ?? "";
  const previewable = Boolean(
    previewResource && canPreviewResource(previewResource),
  );

  const previewQuery = useQuery({
    queryKey: ["resource-browser-preview", previewFullName],
    queryFn: () =>
      fetchResourceFileContent(previewFullName, 0, PREVIEW_LINE_LIMIT),
    enabled: Boolean(previewFullName && previewable),
    staleTime: 0,
    retry: 1,
  });

  const treeItems = useMemo(
    () =>
      buildResourceTreeItems(
        currentResources,
        expandedIds,
        selectedId,
        deferredSearch,
      ),
    [currentResources, deferredSearch, expandedIds, selectedId],
  );

  const closePreview = () => setPreviewResource(null);

  const handleToggle = (item: ResourceBrowserTreeItem) => {
    setExpandedIds((current) => {
      const next = new Set(current);
      if (next.has(item.id)) {
        next.delete(item.id);
      } else {
        next.add(item.id);
      }
      return next;
    });
  };

  const handleItemDoubleClick = (item: ResourceBrowserTreeItem) => {
    if (item.kind === "directory") {
      const directoryPath =
        item.resource.fullName || item.resource.currentDir;
      if (!directoryPath) return;

      onDirectoryPathsChange([...directoryPaths, directoryPath]);
      setSelectedId(null);
      return;
    }

    setSelectedId(item.id);
    setPreviewResource(item.resource);
  };

  return (
    <aside
      aria-label="资源管理"
      className="flex shrink-0 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader
        title="资源管理"
        ariaLabel="资源管理操作"
        actions={
          <>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              isIconOnly
              aria-label="刷新资源"
              title="刷新资源"
              className="size-[22px]"
              isDisabled={resourcesQuery.isFetching}
              onClick={() => void resourcesQuery.refetch()}
            >
              <RefreshCw
                size={14}
                className={resourcesQuery.isFetching ? "animate-spin" : ""}
              />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              isIconOnly
              aria-label="打开完整资源管理"
              title="打开完整资源管理"
              className="size-[22px]"
              onClick={() =>
                window.open("/resources", "_blank", "noopener,noreferrer")
              }
            >
              <ExternalLink size={14} />
            </Button>
          </>
        }
      />

      <div className="shrink-0 px-3 pb-2 pt-1.5">
        <div className="relative">
          <Search
            size={14}
            className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
            aria-hidden="true"
          />
          <Input
            size="small"
            variant="outlined"
            aria-label="搜索资源"
            placeholder={currentDirectory ? "搜索当前目录下的资源" : "搜索资源名称"}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="h-[27px] pl-8 pr-8 text-[11px]"
          />
          {search ? (
            <button
              type="button"
              aria-label="清空搜索"
              title="清空搜索"
              onClick={() => setSearch("")}
              className="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
            >
              <X size={12} />
            </button>
          ) : null}
        </div>
      </div>

      {directoryStack.length > 0 ? (
        <div className="shrink-0 overflow-hidden px-3 pb-2">
          <nav
            aria-label="资源目录路径"
            className="flex min-w-0 items-center gap-1 overflow-x-auto whitespace-nowrap text-[11px]"
          >
            <button
              type="button"
              onClick={() => onDirectoryPathsChange([])}
              className="shrink-0 cursor-pointer rounded px-1 py-0.5 text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
            >
              全部资源
            </button>
            {directoryStack.map((directory, index) => (
              <span
                key={directory.fullName || directory.currentDir || index}
                className="flex min-w-0 shrink-0 items-center gap-1"
              >
                <ChevronRight
                  size={12}
                  className="shrink-0 text-[var(--yak-components-muted-text)]"
                  aria-hidden="true"
                />
                <button
                  type="button"
                  onClick={() =>
                    onDirectoryPathsChange(
                      directoryStack
                        .slice(0, index + 1)
                        .map((item) => item.fullName || item.currentDir || "")
                        .filter(Boolean),
                    )
                  }
                  className={[
                    "max-w-[110px] truncate rounded px-1 py-0.5 transition-colors hover:bg-[var(--yak-color-hover)]",
                    index === directoryStack.length - 1
                      ? "cursor-default font-medium text-[var(--yak-components-panel-text)]"
                      : "cursor-pointer text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]",
                  ].join(" ")}
                  aria-current={
                    index === directoryStack.length - 1 ? "location" : undefined
                  }
                  title={getResourceName(directory)}
                >
                  {getResourceName(directory)}
                </button>
              </span>
            ))}
          </nav>
        </div>
      ) : null}

      {resourcesQuery.isPending ? (
        <div className="flex min-h-0 flex-1 items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          <LoaderCircle size={14} className="animate-spin" />
          正在加载资源
        </div>
      ) : resourcesQuery.isError ? (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 px-4 text-center">
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            资源加载失败
          </span>
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={() => void resourcesQuery.refetch()}
          >
            重试
          </Button>
        </div>
      ) : treeItems.length === 0 ? (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 px-4 text-center">
          <FolderClosed
            size={25}
            strokeWidth={1.35}
            className="text-[#c4cbd4]"
          />
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            {deferredSearch
              ? "当前目录下没有匹配的资源"
              : currentDirectory
                ? "此目录为空"
                : "暂无资源"}
          </span>
        </div>
      ) : (
        <VirtualTree<ResourceBrowserTreeItem>
          ariaLabel="资源文件列表"
          items={treeItems}
          rowHeight={28}
          className="min-h-0 flex-1"
          isItemInteractive={() => true}
          onToggle={handleToggle}
          onActivate={(item) => setSelectedId(item.id)}
          onItemDoubleClick={handleItemDoubleClick}
          renderItem={(item) => (
            <div className="flex min-w-0 flex-1 items-center gap-1.5 pr-2 leading-none">
              <ResourceBrowserIcon item={item} />
              <span
                className={[
                  "min-w-0 flex-1 truncate",
                  item.kind === "directory"
                    ? "font-medium text-[var(--yak-components-panel-text)]"
                    : "text-[var(--yak-components-tab-text-hover)]",
                ].join(" ")}
                title={item.resource.fullName || getResourceName(item.resource)}
              >
                {getResourceName(item.resource)}
              </span>
            </div>
          )}
        />
      )}

      <Modal
        open={Boolean(previewResource)}
        centered
        width={880}
        title={
          <span className="flex min-w-0 items-center gap-2">
            {previewResource ? (
              <FileTypeIcon
                extension={getResourceExtension(previewResource)}
                size={18}
              />
            ) : (
              <File size={15} className="shrink-0 text-[var(--yak-components-muted-text)]" />
            )}
            <span className="truncate">
              {previewResource ? getResourceName(previewResource) : "资源预览"}
            </span>
            <span className="shrink-0 text-[10px] font-normal text-[var(--yak-components-muted-text)]">
              只读
            </span>
          </span>
        }
        bodyClassName="py-3"
        onClose={closePreview}
        footer={
          <Button
            type="button"
            size="sm"
            variant="secondary"
            onClick={closePreview}
          >
            关闭
          </Button>
        }
      >
        {previewResource ? (
          <div className="flex min-w-0 flex-col gap-3">
            <div
              className="break-all rounded-md border border-[#edf0f3] bg-[#fafbfc] px-3 py-2 text-[11px] leading-5 text-[#687386]"
              title={previewResource.fullName}
            >
              {previewResource.fullName || getResourceName(previewResource)}
            </div>

            {!previewFullName ? (
              <div className="flex min-h-[220px] items-center justify-center rounded-md border border-[#edf0f3] bg-[#fafbfc] text-xs text-[var(--yak-components-muted-text)]">
                缺少资源路径，无法预览
              </div>
            ) : !previewable ? (
              <div className="flex min-h-[220px] items-center justify-center rounded-md border border-[#edf0f3] bg-[#fafbfc] text-xs text-[var(--yak-components-muted-text)]">
                当前文件类型暂不支持在线预览
              </div>
            ) : previewQuery.isPending ? (
              <div className="flex min-h-[280px] items-center justify-center gap-2 rounded-md border border-[#edf0f3] bg-[#fafbfc] text-xs text-[var(--yak-components-muted-text)]">
                <LoaderCircle size={15} className="animate-spin" />
                正在读取文件内容
              </div>
            ) : previewQuery.isError ? (
              <div className="flex min-h-[220px] flex-col items-center justify-center gap-3 rounded-md border border-[#edf0f3] bg-[#fafbfc] text-xs text-[var(--yak-components-muted-text)]">
                文件内容读取失败
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => void previewQuery.refetch()}
                >
                  重试
                </Button>
              </div>
            ) : (
              <div className="overflow-auto rounded-md border border-[#edf0f3] bg-[#fafbfc]">
                <div className="border-b border-[#edf0f3] px-3 py-1.5 text-[10px] text-[#929aa6]">
                  最多展示前 {PREVIEW_LINE_LIMIT} 行
                </div>
                <pre className="max-h-[62vh] min-h-[220px] overflow-auto whitespace-pre-wrap break-words px-4 py-3 font-mono text-[12px] leading-6 text-[#394150]">
                  {previewQuery.data?.content || "文件内容为空"}
                </pre>
              </div>
            )}
          </div>
        ) : null}
      </Modal>
    </aside>
  );
}
