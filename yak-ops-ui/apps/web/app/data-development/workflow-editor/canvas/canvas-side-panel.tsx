import { useMemo, useState, type ReactNode } from "react";
import {
  Code2,
  Copy,
  Download,
  List,
  LocateFixed,
  RefreshCw,
  Search,
  X,
  type LucideIcon,
} from "lucide-react";
import { toast } from "@yak-ops/yak-ui";

import type { WorkflowDagRecord } from "@/service/workflow/definition";

import { TaskTypeBadgeIcon } from "../components/task-type-badge-icon";
import { buildWorkflowSpec } from "../adapter/workflow-spec";
import type { WorkflowCanvasNode } from "../types";

export type CanvasPanelKey = "nodes" | "spec";

const PANEL_CONTENT: Record<
  CanvasPanelKey,
  {
    title: string;
    description: string;
    Icon: LucideIcon;
  }
> = {
  nodes: {
    title: "内部节点列表",
    description: "点击节点，在画布中快速定位。",
    Icon: List,
  },
  spec: {
    title: "Spec",
    description: "查看当前工作流的结构化定义。",
    Icon: Code2,
  },
};

function renderJsonLine(line: string): ReactNode[] {
  const pattern =
    /"(?:\\.|[^"\\])*"(?=\s*:)|"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b|[\{\}\[\],:]/g;
  const tokens: ReactNode[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(line)) !== null) {
    if (match.index > cursor) {
      tokens.push(line.slice(cursor, match.index));
    }

    const token = match[0];
    const rest = line.slice(pattern.lastIndex).trimStart();
    const className = token.startsWith('"')
      ? rest.startsWith(":")
        ? "text-[#7c3aed]"
        : "text-[#0f766e]"
      : /^(?:true|false|null)$/.test(token)
        ? "text-[#c2410c]"
        : /^-?\d/.test(token)
          ? "text-[#2563eb]"
          : "text-[#94a3b8]";

    tokens.push(
      <span key={match.index} className={className}>
        {token}
      </span>,
    );
    cursor = pattern.lastIndex;
  }

  if (cursor < line.length) {
    tokens.push(line.slice(cursor));
  }

  return tokens.length > 0 ? tokens : [line || " "];
}

export function CanvasSidePanel({
  activePanel,
  onClose,
  dag,
  nodes,
  focusedNodeId,
  onLocateNode,
  onRefresh,
  isRefreshing,
}: {
  activePanel: CanvasPanelKey | null;
  onClose: () => void;
  dag: WorkflowDagRecord;
  nodes: WorkflowCanvasNode[];
  focusedNodeId: string | null;
  onLocateNode: (nodeId: string) => void;
  onRefresh: () => Promise<void>;
  isRefreshing: boolean;
}) {
  const [searchValue, setSearchValue] = useState("");
  const content = activePanel ? PANEL_CONTENT[activePanel] : null;
  const Icon = content?.Icon;
  const specText = useMemo(
    () => JSON.stringify(buildWorkflowSpec(dag), null, 2) ?? "{}",
    [dag],
  );
  const specLines = useMemo(() => specText.split("\n"), [specText]);
  const filteredNodes = useMemo(() => {
    const keyword = searchValue.trim().toLocaleLowerCase();
    if (!keyword) return nodes;

    return nodes.filter((node) => {
      const searchableText = [
        node.id,
        node.data.name,
        node.data.taskType,
        node.data.taskCode === null ? "" : String(node.data.taskCode),
      ]
        .join(" ")
        .toLocaleLowerCase();

      return searchableText.includes(keyword);
    });
  }, [nodes, searchValue]);

  const handleCopy = async () => {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard API unavailable");
      }
      await navigator.clipboard.writeText(specText);
      toast.success("Spec 已复制");
    } catch {
      toast.error("复制失败，请检查浏览器剪贴板权限");
    }
  };

  const handleDownload = () => {
    const workflowName =
      dag.workflowDefinition.name ||
      "workflow-" + dag.workflowDefinition.code;
    const safeName = workflowName.replace(/[\\/:*?"<>|]/g, "_");
    const url = URL.createObjectURL(
      new Blob([specText], { type: "application/json;charset=utf-8" }),
    );
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = safeName + ".spec.json";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
    toast.success("Spec 已下载");
  };

  const handleRefresh = async () => {
    try {
      await onRefresh();
      toast.success("Spec 已刷新");
    } catch {
      toast.error("Spec 刷新失败");
    }
  };

  return (
    <aside
      id="workflow-canvas-side-panel"
      role="complementary"
      aria-label={content?.title ?? "画布辅助面板"}
      aria-hidden={!content}
      className={[
        "h-full min-h-0 shrink-0 overflow-hidden bg-white transition-[width,flex-basis] duration-200 ease-out",
        content ? "border-l border-[#e5e7eb]" : "",
      ].join(" ")}
      style={{
        width: content
          ? activePanel === "nodes"
            ? "min(320px, 36%)"
            : "min(64%, 600px)"
          : "0px",
        flexBasis: content
          ? activePanel === "nodes"
            ? "min(320px, 36%)"
            : "min(64%, 600px)"
          : "0px",
        pointerEvents: content ? "auto" : "none",
      }}
    >
      <div className="flex h-full min-h-0 w-full flex-col">
        <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
          <div className="flex min-w-0 items-center gap-2">
            {Icon ? (
              <Icon size={15} className="shrink-0 text-[#687385]" />
            ) : null}
            <span className="truncate text-sm font-semibold text-[#303846]">
              {content?.title ?? "画布辅助面板"}
            </span>
            {activePanel === "nodes" ? (
              <span className="rounded bg-[#f3f5f8] px-1.5 py-0.5 text-[10px] tabular-nums text-[#7b8492]">
                {nodes.length}
              </span>
            ) : null}
          </div>

          <div className="flex shrink-0 items-center gap-1">
            {activePanel === "spec" ? (
              <>
                <button
                  type="button"
                  title="刷新 Spec"
                  aria-label="刷新 Spec"
                  disabled={isRefreshing}
                  onClick={() => void handleRefresh()}
                  className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[#7b8492] transition-colors hover:bg-[#f3f5f8] hover:text-[#303846] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <RefreshCw
                    size={14}
                    className={isRefreshing ? "animate-spin" : ""}
                  />
                </button>
                <button
                  type="button"
                  title="复制 JSON"
                  aria-label="复制 JSON"
                  onClick={() => void handleCopy()}
                  className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[#7b8492] transition-colors hover:bg-[#f3f5f8] hover:text-[#303846]"
                >
                  <Copy size={14} />
                </button>
                <button
                  type="button"
                  title="下载 JSON"
                  aria-label="下载 JSON"
                  onClick={handleDownload}
                  className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[#7b8492] transition-colors hover:bg-[#f3f5f8] hover:text-[#303846]"
                >
                  <Download size={14} />
                </button>
              </>
            ) : null}
            <button
              type="button"
              title="关闭面板"
              aria-label="关闭面板"
              tabIndex={content ? 0 : -1}
              onClick={onClose}
              className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-[#7b8492] transition-colors hover:bg-[#f3f5f8] hover:text-[#303846]"
            >
              <X size={15} />
            </button>
          </div>
        </header>

        {activePanel === "spec" ? (
          <div className="flex min-h-0 flex-1 flex-col">
            <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#eef0f3] px-4 text-xs">
              <span className="text-[#687282]">
                {content?.description}
              </span>
              <span className="rounded bg-[#f3f5f8] px-2 py-0.5 text-[#7b8492]">
                JSON · 只读
              </span>
            </div>
            <div className="min-h-0 flex-1 overflow-auto bg-[#fbfcfe] font-mono text-[12px] leading-6">
              <div className="min-w-max py-3">
                {specLines.map((line, index) => (
                  <div
                    key={index}
                    className="grid grid-cols-[3.25rem_minmax(0,1fr)]"
                  >
                    <span className="select-none border-r border-[#edf0f4] pr-3 text-right text-[#a0a8b4]">
                      {index + 1}
                    </span>
                    <span className="whitespace-pre pl-4 pr-6 text-[#374151]">
                      {renderJsonLine(line)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
            <footer className="flex h-8 shrink-0 items-center justify-between border-t border-[#eef0f3] px-4 text-[11px] text-[#98a1ad]">
              <span>共 {specLines.length} 行</span>
              <span>数据来自当前工作流定义</span>
            </footer>
          </div>
        ) : activePanel === "nodes" ? (
          <div className="flex min-h-0 flex-1 flex-col">
            <div className="shrink-0 border-b border-[#eef0f3] p-3">
              <div className="relative">
                <Search
                  size={14}
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#98a1ad]"
                />
                <input
                  type="search"
                  value={searchValue}
                  onChange={(event) => setSearchValue(event.target.value)}
                  placeholder="搜索节点名称 / 类型 / ID"
                  aria-label="搜索内部节点"
                  autoComplete="off"
                  className="h-9 w-full rounded-md border border-[#e5e7eb] bg-white pl-9 pr-9 text-xs text-[#303846] outline-none transition-colors placeholder:text-[#a0a8b4] focus:border-[#9bb5ff] focus:ring-2 focus:ring-[#315efb]/10"
                />
                {searchValue ? (
                  <button
                    type="button"
                    title="清空搜索"
                    aria-label="清空搜索"
                    onClick={() => setSearchValue("")}
                    className="absolute right-2 top-1/2 flex size-6 -translate-y-1/2 cursor-pointer items-center justify-center rounded text-[#98a1ad] transition-colors hover:bg-[#f3f5f8] hover:text-[#475467]"
                  >
                    <X size={13} />
                  </button>
                ) : null}
              </div>
              <div className="mt-2 flex items-center justify-end">
                <span className="text-[11px] tabular-nums text-[#98a1ad]">
                  {filteredNodes.length} / {nodes.length}
                </span>
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-2">
              {nodes.length === 0 ? (
                <div className="flex h-full min-h-32 flex-col items-center justify-center gap-2 text-center">
                  <List size={22} className="text-[#c0c7d1]" />
                  <span className="text-xs text-[#98a1ad]">
                    当前工作流还没有节点
                  </span>
                </div>
              ) : filteredNodes.length === 0 ? (
                <div className="flex h-full min-h-32 flex-col items-center justify-center gap-2 text-center">
                  <Search size={20} className="text-[#c0c7d1]" />
                  <span className="text-xs text-[#98a1ad]">
                    没有找到匹配的节点
                  </span>
                </div>
              ) : (
                <div className="space-y-1">
                  {filteredNodes.map((node) => {
                    const isFocused = focusedNodeId === node.id;

                    return (
                      <button
                        key={node.id}
                        type="button"
                        title={`定位到 ${node.data.name}`}
                        aria-pressed={isFocused}
                        onClick={() => onLocateNode(node.id)}
                        className={[
                          "group flex w-full cursor-pointer items-center gap-2.5 rounded-md border px-2.5 py-2 text-left transition-colors",
                          isFocused
                            ? "border-[#cbd8ff] bg-[#f4f7ff]"
                            : "border-transparent hover:border-[#e8edf5] hover:bg-[#f8fafc]",
                        ].join(" ")}
                      >
                        <TaskTypeBadgeIcon
                          taskType={node.data.taskType || "TASK"}
                          size={24}
                        />
                        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                          <span
                            className={[
                              "truncate text-xs font-medium",
                              isFocused ? "text-[#315efb]" : "text-[#303846]",
                            ].join(" ")}
                          >
                            {node.data.name}
                          </span>
                          <span className="truncate text-[10px] text-[#98a1ad]">
                            {node.data.taskType || "TASK"}
                            {node.data.draft
                              ? " · 未保存"
                              : node.data.taskCode !== null
                                ? ` · ${node.data.taskCode}`
                                : ""}
                          </span>
                        </span>
                        <LocateFixed
                          size={14}
                          className={[
                            "shrink-0 transition-colors",
                            isFocused
                              ? "text-[#315efb]"
                              : "text-[#a0a8b4] opacity-0 group-hover:opacity-100",
                          ].join(" ")}
                        />
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            <footer className="flex h-8 shrink-0 items-center border-t border-[#eef0f3] px-3 text-[11px] text-[#98a1ad]">
              点击节点可定位到画布
            </footer>
          </div>
        ) : null}
      </div>
    </aside>
  );
}
