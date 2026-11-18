import { useEffect, useState, type MouseEvent as ReactMouseEvent } from "react";
import { createPortal } from "react-dom";
import {
  ChevronRight,
  Code2,
  Columns3,
  Copy,
  Database,
  Eye,
  Hash,
  ListTree,
  Layers3,
  Search,
  Table2,
  Workflow,
  Zap,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  Input,
  PanelHeader,
  toast,
} from "@yak-ops/yak-ui";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";

import {
  listDataSources,
  getDataSourceCatalogCapabilities,
  listDataSourceCatalogChildren,
  searchDataSourceCatalog,
  type CatalogTreeNode,
  type DataSourceRecord,
} from "@/service/datasource";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import { DataSyncSearchableSelect } from "@/app/data-sync/components/searchable-select";
import { ResourceTreeIcon } from "../../resources/components/resource-file-icon";
import { useDataDevelopmentWorkbench } from "../workbench-context";

type CatalogContextMenuState = {
  x: number;
  y: number;
  nodeName: string;
};

function CatalogNodeIcon({
  node,
  expanded = false,
}: {
  node: CatalogTreeNode;
  expanded?: boolean;
}) {
  const iconClass = "shrink-0";
  const iconSize = 15;

  switch (node.nodeType) {
    case "DATASOURCE":
    case "DATABASE":
    case "CATALOG":
      return (
        <Database
          size={iconSize}
          strokeWidth={1.7}
          className={iconClass + " text-[#8b98a8]"}
        />
      );
    case "SCHEMA":
    case "TABLE_GROUP":
    case "VIEW_GROUP":
    case "MATERIALIZED_VIEW_GROUP":
    case "FUNCTION_GROUP":
    case "PROCEDURE_GROUP":
    case "TRIGGER_GROUP":
    case "SEQUENCE_GROUP":
    case "COLUMN_GROUP":
    case "INDEX_GROUP":
      // Reuse the exact open/closed folder SVG used by Resource Management.
      return (
        <ResourceTreeIcon
          name={node.name}
          directory
          expanded={expanded}
          size={17}
        />
      );
    case "TABLE":
      return <Table2 size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#22a06b]"} />;
    case "VIEW":
      return <Eye size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#4b91d1]"} />;
    case "MATERIALIZED_VIEW":
      return <Layers3 size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#159e91]"} />;
    case "COLUMN":
      return <Columns3 size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#c18a2d]"} />;
    case "INDEX":
      return <ListTree size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#b9852b]"} />;
    case "FUNCTION":
      return <Code2 size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#9361d8]"} />;
    case "PROCEDURE":
      return <Workflow size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#6875d9]"} />;
    case "TRIGGER":
      return <Zap size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#d88a24]"} />;
    case "SEQUENCE":
      return <Hash size={iconSize} strokeWidth={1.8} className={iconClass + " text-[#1698b5]"} />;
    default:
      return <Database size={iconSize} strokeWidth={1.7} className={iconClass + " text-[#8b98a8]"} />;
  }
}

function CatalogTreeNodeView({
  datasourceId,
  node,
  depth,
  onOpenObject,
  onNodeContextMenu,
  defaultExpanded = false,
}: {
  datasourceId: string;
  node: CatalogTreeNode;
  depth: number;
  onOpenObject: (node: CatalogTreeNode) => void;
  onNodeContextMenu: (node: CatalogTreeNode, event: ReactMouseEvent<HTMLElement>) => void;
  defaultExpanded?: boolean;
}) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const childrenQuery = useInfiniteQuery({
    queryKey: ["data-catalog", "children", datasourceId, node.id, node.nodeType, node.path],
    initialPageParam: 1,
    queryFn: ({ pageParam, signal }) =>
      listDataSourceCatalogChildren(datasourceId, {
        ...node.path,
        nodeType: node.nodeType,
        pageNo: pageParam,
        pageSize: 100,
      }, signal),
    getNextPageParam: (lastPage) => lastPage.hasMore ? lastPage.pageNo + 1 : undefined,
    enabled: expanded && node.hasChildren,
  });
  const children = childrenQuery.data?.pages.flatMap((page) => page.items) ?? [];
  const isObject = ["TABLE", "VIEW", "MATERIALIZED_VIEW"].includes(node.nodeType);

  return (
    <div>
      <div
        className="flex min-w-0 items-center gap-1 rounded-[3px] pr-2 hover:bg-[#f4f6f9]"
        style={{ paddingLeft: 8 + depth * 14 }}
        onContextMenu={(event) => onNodeContextMenu(node, event)}
      >
        {node.hasChildren ? (
          <button
            type="button"
            aria-label={expanded ? "收起目录" : "展开目录"}
            onClick={() => setExpanded((value) => !value)}
            className="flex size-5 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[#89919c] hover:bg-[#e9edf3]"
          >
            <ChevronRight size={12} className={expanded ? "rotate-90 transition-transform" : "transition-transform"} />
          </button>
        ) : (
          <span className="size-5 shrink-0" />
        )}
        <button
          type="button"
          title={node.name}
          onClick={() => {
            if (isObject) onOpenObject(node);
            else if (node.hasChildren) setExpanded((value) => !value);
          }}
          className="flex h-[29px] min-w-0 flex-1 cursor-pointer items-center gap-1.5 text-left"
        >
          <CatalogNodeIcon node={node} expanded={expanded} />
          <span className="min-w-0 flex-1 truncate text-[11px] text-[#3f4752]">
            {node.name}
          </span>
          {node.metadata?.count != null ? (
            <span className="text-[10px] text-[#a0a6af]">{String(node.metadata.count)}</span>
          ) : null}
        </button>
      </div>

      {expanded && node.hasChildren ? (
        <div>
          {childrenQuery.isLoading ? (
            <div className="py-2 text-[11px] text-[#9aa0a8]" style={{ paddingLeft: 34 + depth * 14 }}>
              加载目录...
            </div>
          ) : null}
          {childrenQuery.isError ? (
            <button
              type="button"
              onClick={() => childrenQuery.refetch()}
              className="py-2 text-[11px] text-[#c15b52]"
              style={{ paddingLeft: 34 + depth * 14 }}
            >
              加载失败，点击重试
            </button>
          ) : null}
          {children.map((child) => (
            <CatalogTreeNodeView
              key={child.id}
              datasourceId={datasourceId}
              node={child}
              depth={depth + 1}
              onOpenObject={onOpenObject}
              onNodeContextMenu={onNodeContextMenu}
            />
          ))}
          {childrenQuery.hasNextPage ? (
            <button
              type="button"
              disabled={childrenQuery.isFetchingNextPage}
              onClick={() => childrenQuery.fetchNextPage()}
              className="py-2 text-[11px] text-[#6d83a5] hover:text-[#3e6598] disabled:opacity-50"
              style={{ paddingLeft: 34 + depth * 14 }}
            >
              {childrenQuery.isFetchingNextPage ? "加载中..." : "加载更多"}
            </button>
          ) : null}
          {!childrenQuery.isLoading && !childrenQuery.isError && children.length === 0 ? (
            <div className="py-2 text-[11px] text-[#9aa0a8]" style={{ paddingLeft: 34 + depth * 14 }}>
              暂无对象
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export function DataCatalogPanel({ width }: { width: number }) {
  const [selectedSourceId, setSelectedSourceId] = useState("");
  const [keyword, setKeyword] = useState("");
  const [contextMenu, setContextMenu] = useState<CatalogContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] = useState<HTMLElement | null>(null);
  const { openCatalogTable } = useDataDevelopmentWorkbench();

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  const handleNodeContextMenu = (
    node: CatalogTreeNode,
    event: ReactMouseEvent<HTMLElement>,
  ) => {
    event.preventDefault();
    event.stopPropagation();
    setContextMenu({
      x: event.clientX,
      y: event.clientY,
      nodeName: node.name,
    });
  };

  const copyNodeName = async () => {
    if (!contextMenu) return;
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard API unavailable");
      }
      await navigator.clipboard.writeText(contextMenu.nodeName);
      toast.success("名称已复制");
    } catch {
      toast.error("复制失败，请检查浏览器剪贴板权限");
    } finally {
      setContextMenu(null);
    }
  };

  const sourcesQuery = useQuery({
    queryKey: ["data-catalog", "sources"],
    queryFn: ({ signal }) =>
      listDataSources({ pageNo: 1, pageSize: 100 }, signal),
  });
  const sources: DataSourceRecord[] = sourcesQuery.data?.totalList ?? [];
  const sourceOptions = sources.flatMap((source) =>
    source.id
      ? [
          {
            value: source.id,
            label: source.name || source.id,
            icon: <DatabaseIcons dbType={source.dbType} width="16px" height="16px" />,
            searchText: [source.dbType, source.database, source.schema]
              .filter(Boolean)
              .join(" "),
          },
        ]
      : [],
  );
  const selectedSource = sources.find((source) => source.id === selectedSourceId);
  const capabilitiesQuery = useQuery({
    queryKey: ["data-catalog", "capabilities", selectedSourceId],
    queryFn: ({ signal }) => getDataSourceCatalogCapabilities(selectedSourceId, signal),
    enabled: Boolean(selectedSourceId),
  });

  useEffect(() => {
    if (!selectedSourceId && sources.length > 0 && sources[0].id) {
      setSelectedSourceId(sources[0].id);
    }
  }, [selectedSourceId, sources]);

  const searchQuery = useQuery({
    queryKey: ["data-catalog", "search", selectedSourceId, keyword.trim()],
    queryFn: ({ signal }) =>
      searchDataSourceCatalog(selectedSourceId, keyword.trim(), 100, signal),
    enabled: Boolean(selectedSourceId && keyword.trim().length >= 2),
  });

  const openObject = (node: CatalogTreeNode) => {
    if (!selectedSourceId || !node.path.objectName) return;
    openCatalogTable(
      selectedSourceId,
      selectedSource?.name || "数据源",
      node.path.database || "",
      node.path.objectName,
      node.path.schema,
      node.objectType || "TABLE",
    );
  };

  const rootNode: CatalogTreeNode | null = selectedSourceId && selectedSource ? {
    id: "datasource:" + selectedSourceId,
    name: selectedSource.name || "数据源",
    nodeType: "DATASOURCE",
    hasChildren: Boolean(
      (capabilitiesQuery.data?.namespaceLevels.length ?? 0) > 0
      || (capabilitiesQuery.data?.objectTypes.length ?? 0) > 0
    ),
    path: {},
  } : null;

  return (
    <aside
      aria-label="数据目录"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="数据目录" />
      <div className="space-y-2 border-b border-[#ececec] px-3 py-2">
        <DataSyncSearchableSelect
          value={selectedSourceId || null}
          options={sourceOptions}
          placeholder="请选择数据源"
          ariaLabel="选择数据源"
          searchPlaceholder="请输入关键字"
          emptyText="没有匹配的数据源"
          triggerClassName="!h-6 !w-full !rounded-[4px] !px-2.5"
          onValueChange={(value) => {
            setSelectedSourceId(value);
            setKeyword("");
          }}
        />
        <div className="relative">
          <Search
            size={13}
            className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
          />
          <Input
            size="small"
            variant="outlined"
            aria-label="搜索表和视图"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索表、视图..."
            className="!h-6 w-full !rounded-[4px] pl-8 pr-8 text-[11px]"
          />
          {keyword ? (
            <button
              type="button"
              aria-label="清除搜索"
              onClick={() => setKeyword("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[#9299a3] hover:text-[#424a55]"
            >
              ×
            </button>
          ) : null}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto py-1">
        {sourcesQuery.isLoading ? (
          <div className="flex h-24 items-center justify-center text-[12px] text-[#9aa0a8]">加载数据源...</div>
        ) : sources.length === 0 ? (
          <div className="px-4 py-10 text-center text-[12px] text-[#9aa0a8]">暂无数据源</div>
        ) : !selectedSourceId || !selectedSource ? (
          <div className="px-4 py-10 text-center text-[12px] text-[#9aa0a8]">请选择数据源</div>
        ) : keyword.trim().length >= 2 ? (
          searchQuery.isLoading ? (
            <div className="px-4 py-6 text-center text-[11px] text-[#9aa0a8]">搜索中...</div>
          ) : searchQuery.isError ? (
            <button type="button" onClick={() => searchQuery.refetch()} className="px-4 py-6 text-[11px] text-[#c15b52]">搜索失败，点击重试</button>
          ) : (searchQuery.data ?? []).length === 0 ? (
            <div className="px-4 py-6 text-center text-[11px] text-[#9aa0a8]">没有找到匹配对象</div>
          ) : (
            searchQuery.data!.map((node) => (
              <button
                key={node.id}
                type="button"
                onClick={() => openObject(node)}
                onContextMenu={(event) => handleNodeContextMenu(node, event)}
                className="flex w-full cursor-pointer items-center gap-2 px-3 py-2 text-left hover:bg-[#eef3ff]"
              >
                <CatalogNodeIcon node={node} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[11px] text-[#3e454f]">{node.name}</span>
                  <span className="block truncate text-[10px] text-[#9aa0a8]">
                    {[node.path.database, node.path.schema].filter(Boolean).join(" / ")}
                  </span>
                </span>
              </button>
            ))
          )
        ) : rootNode ? (
          <>
          {capabilitiesQuery.isLoading ? (
            <div className="px-4 py-4 text-[11px] text-[#9aa0a8]">读取数据源目录能力...</div>
          ) : capabilitiesQuery.isError ? (
            <button
              type="button"
              onClick={() => capabilitiesQuery.refetch()}
              className="px-4 py-4 text-[11px] text-[#c15b52]"
            >
              目录能力读取失败，点击重试
            </button>
          ) : !rootNode.hasChildren ? (
            <div className="px-4 py-4 text-[11px] text-[#9aa0a8]">
              当前数据源插件暂未提供元数据目录能力
            </div>
          ) : null}
          <CatalogTreeNodeView
            key={selectedSourceId}
            datasourceId={selectedSourceId}
            node={rootNode}
            depth={0}
            defaultExpanded
            onOpenObject={openObject}
            onNodeContextMenu={handleNodeContextMenu}
          />
          </>
        ) : null}
      </div>
      {contextMenu && contextMenuPortalRoot
        ? createPortal(
            <DropdownMenu
              open
              onOpenChange={(open) => {
                if (!open) setContextMenu(null);
              }}
            >
              <DropdownMenuTrigger
                aria-hidden="true"
                tabIndex={-1}
                className="pointer-events-none fixed size-px opacity-0"
                style={{ left: contextMenu.x, top: contextMenu.y }}
              />
              <DropdownMenuContent
                side="bottom"
                align="start"
                sideOffset={0}
                className="w-[144px] rounded-md p-1"
              >
                <DropdownMenuItem onClick={copyNodeName}>
                  <Copy size={14} />
                  复制名称
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>,
            contextMenuPortalRoot,
          )
        : null}
    </aside>
  );
}
