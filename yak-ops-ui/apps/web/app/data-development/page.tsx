import { useMemo, useState } from "react";
import {
  ChevronDown,
  ChevronRight,
  Code2,
  FileCode2,
  Folder,
  FolderOpen,
  GitBranch,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  Workflow,
  X,
} from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { Input } from "@yak-ops/yak-ui";

const directories = [
  { id: "workflow", name: "工作流开发", icon: Workflow },
  { id: "sql", name: "SQL 开发", icon: FileCode2 },
  { id: "script", name: "脚本开发", icon: Code2 },
] as const;

type DirectoryId = (typeof directories)[number]["id"];

export function DataDevelopmentPage() {
  const [params, setParams] = useSearchParams();
  const selected = directories.find((item) => item.id === params.get("type"))?.id ?? "workflow";
  const [search, setSearch] = useState("");
  const [expanded, setExpanded] = useState<DirectoryId[]>(["workflow", "sql", "script"]);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const visibleDirectories = useMemo(
    () => directories.filter((item) => item.name.toLowerCase().includes(search.trim().toLowerCase())),
    [search],
  );

  const selectDirectory = (id: DirectoryId) => {
    setParams({ type: id });
    setExpanded((items) => (items.includes(id) ? items : [...items, id]));
  };

  return (
    <div className="flex h-full min-h-[420px] overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      {sidebarOpen ? (
        <aside aria-label="数据开发项目目录" className="flex w-64 shrink-0 flex-col border-r border-[var(--yak-components-border)] max-md:w-52">
          <div className="flex h-11 items-center justify-between border-b border-[var(--yak-components-border)] px-3">
            <span className="text-sm font-semibold">项目目录</span>
            <button type="button" aria-label="收起项目目录" onClick={() => setSidebarOpen(false)} className="rounded p-1 text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]">
              <PanelLeftClose size={16} />
            </button>
          </div>
          <div className="p-3">
            <div className="relative">
              <Search size={15} aria-hidden="true" className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
              <Input aria-label="搜索目录" placeholder="搜索目录" value={search} onChange={(event) => setSearch(event.target.value)} className="pl-8" />
              {search ? (
                <button type="button" aria-label="清空搜索" onClick={() => setSearch("")} className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]">
                  <X size={14} />
                </button>
              ) : null}
            </div>
          </div>
          <nav aria-label="开发类型" className="min-h-0 flex-1 overflow-y-auto px-2">
            {visibleDirectories.map((item) => {
              const Icon = item.icon;
              const isExpanded = expanded.includes(item.id);
              const isSelected = selected === item.id;
              return (
                <div key={item.id}>
                  <div className={`flex h-9 items-center rounded-md ${isSelected ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]" : "hover:bg-[var(--yak-color-hover)]"}`}>
                    <button
                      type="button"
                      aria-label={isExpanded ? `折叠${item.name}` : `展开${item.name}`}
                      aria-expanded={isExpanded}
                      onClick={() => setExpanded((items) => isExpanded ? items.filter((id) => id !== item.id) : [...items, item.id])}
                      className="flex h-8 w-7 shrink-0 items-center justify-center"
                    >
                      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                    </button>
                    <button
                      type="button"
                      aria-current={isSelected ? "page" : undefined}
                      onClick={() => selectDirectory(item.id)}
                      className="flex min-w-0 flex-1 items-center gap-2 py-2 pr-2 text-left text-[13px]"
                    >
                      <Icon size={15} aria-hidden="true" />
                      <span className="truncate">{item.name}</span>
                    </button>
                  </div>
                  {isExpanded ? (
                    <div className="flex items-center gap-2 py-2 pl-9 text-xs text-[var(--yak-components-muted-text)]">
                      <Folder size={13} aria-hidden="true" />
                      暂无开发节点
                    </div>
                  ) : null}
                </div>
              );
            })}
            {visibleDirectories.length === 0 ? (
              <p className="px-2 py-6 text-center text-xs text-[var(--yak-components-muted-text)]">没有匹配的目录</p>
            ) : null}
          </nav>
        </aside>
      ) : null}

      <section className="flex min-w-0 flex-1 flex-col" aria-label="数据开发工作区">
        <header className="flex h-11 shrink-0 items-center gap-2 border-b border-[var(--yak-components-border)] px-3">
          {!sidebarOpen ? (
            <button type="button" aria-label="展开项目目录" onClick={() => setSidebarOpen(true)} className="rounded p-1 hover:bg-[var(--yak-color-hover)]">
              <PanelLeftOpen size={17} />
            </button>
          ) : null}
          <FolderOpen size={16} className="text-[var(--yak-color-primary)]" aria-hidden="true" />
          <span className="text-sm font-medium">数据开发</span>
          <span className="text-xs text-[var(--yak-components-muted-text)]">/ {directories.find((item) => item.id === selected)?.name}</span>
        </header>
        <div className="flex min-h-0 flex-1 items-center justify-center p-6">
          <div className="max-w-sm text-center">
            <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-2xl bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
              <GitBranch size={26} strokeWidth={1.5} aria-hidden="true" />
            </div>
            <h1 className="text-base font-semibold">数据开发工作区</h1>
            <p className="mt-2 text-[13px] leading-6 text-[var(--yak-components-muted-text)]">
              从左侧项目目录选择开发类型。工作流、SQL 和脚本节点将在接入对应的开发能力后显示在这里。
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
