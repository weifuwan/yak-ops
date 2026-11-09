import { useMemo, useState } from "react";
import {
  ChevronDown,
  ChevronRight,
  Code2,
  FileCode2,
  Folder,
  Search,
  Workflow,
  X,
  type LucideIcon,
} from "lucide-react";
import { Input } from "@yak-ops/yak-ui";

import {
  useDataDevelopmentWorkbench,
  type DataDevelopmentResource,
  type DataDevelopmentResourceType,
} from "./workbench-context";

type ResourceGroup = {
  id: DataDevelopmentResourceType;
  title: string;
  icon: LucideIcon;
  resources: DataDevelopmentResource[];
};

const RESOURCE_GROUPS: ResourceGroup[] = [
  {
    id: "workflow",
    title: "工作流",
    icon: Workflow,
    resources: [
      {
        id: "workflow:customer-sync",
        title: "客户同步工作流",
        type: "workflow",
        description: "这里将接入工作流编辑器。",
      },
      {
        id: "workflow:ods-sync",
        title: "ODS 同步工作流",
        type: "workflow",
        description: "这里将接入工作流编辑器。",
      },
    ],
  },
  {
    id: "sql",
    title: "SQL",
    icon: FileCode2,
    resources: [
      {
        id: "sql:daily-report",
        title: "日报汇总.sql",
        type: "sql",
        description: "这里将接入 SQL 编辑器。",
      },
      {
        id: "sql:user-profile",
        title: "用户画像.sql",
        type: "sql",
        description: "这里将接入 SQL 编辑器。",
      },
    ],
  },
  {
    id: "script",
    title: "脚本",
    icon: Code2,
    resources: [
      {
        id: "script:cleanup",
        title: "cleanup.py",
        type: "script",
        description: "这里将接入脚本编辑器。",
      },
    ],
  },
];

export function DataDevelopmentResourcePanel({ width }: { width: number }) {
  const { activeTabId, openResource } = useDataDevelopmentWorkbench();
  const [search, setSearch] = useState("");
  const [expandedGroups, setExpandedGroups] = useState<
    DataDevelopmentResourceType[]
  >(["workflow", "sql", "script"]);

  const groups = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return RESOURCE_GROUPS;

    return RESOURCE_GROUPS.map((group) => ({
      ...group,
      resources: group.resources.filter((resource) =>
        resource.title.toLowerCase().includes(keyword),
      ),
    })).filter((group) => group.resources.length > 0);
  }, [search]);

  const toggleGroup = (id: DataDevelopmentResourceType) => {
    setExpandedGroups((current) =>
      current.includes(id)
        ? current.filter((item) => item !== id)
        : [...current, id],
    );
  };

  return (
    <aside
      aria-label="项目目录"
      className="flex shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <div className="flex h-10 shrink-0 items-center border-b border-[var(--yak-components-border)] px-3">
        <span className="text-sm font-semibold">项目目录</span>
      </div>

      <div className="p-3">
        <div className="relative">
          <Search
            size={14}
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
          />
          <Input
            size="small"
            variant="outlined"
            aria-label="搜索目录"
            placeholder="搜索目录"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="pl-8"
          />
          {search ? (
            <button
              type="button"
              aria-label="清空搜索"
              onClick={() => setSearch("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]"
            >
              <X size={13} />
            </button>
          ) : null}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        <div className="mb-1 flex h-8 items-center gap-2 px-2 text-xs font-medium text-[var(--yak-components-muted-text)]">
          <Folder size={14} aria-hidden="true" />
          数据开发
        </div>

        {groups.map((group) => {
          const GroupIcon = group.icon;
          const expanded = expandedGroups.includes(group.id);

          return (
            <div key={group.id}>
              <button
                type="button"
                aria-expanded={expanded}
                onClick={() => toggleGroup(group.id)}
                className="flex h-8 w-full items-center gap-1 rounded px-1 text-left text-[13px] hover:bg-[var(--yak-color-hover)]"
              >
                {expanded ? (
                  <ChevronDown size={14} aria-hidden="true" />
                ) : (
                  <ChevronRight size={14} aria-hidden="true" />
                )}
                <GroupIcon
                  size={14}
                  aria-hidden="true"
                  className="text-[var(--yak-components-muted-text)]"
                />
                <span className="truncate">{group.title}</span>
              </button>

              {expanded ? (
                <div className="pb-1">
                  {group.resources.map((resource) => {
                    const selected = resource.id === activeTabId;
                    return (
                      <button
                        key={resource.id}
                        type="button"
                        onClick={() => openResource(resource)}
                        className={[
                          "flex h-8 w-full items-center gap-2 rounded pl-9 pr-2 text-left text-[13px] transition-colors",
                          selected
                            ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                            : "hover:bg-[var(--yak-color-hover)]",
                        ].join(" ")}
                      >
                        <span className="truncate">{resource.title}</span>
                      </button>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}

        {groups.length === 0 ? (
          <div className="px-3 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            没有匹配的开发资源
          </div>
        ) : null}
      </div>
    </aside>
  );
}
