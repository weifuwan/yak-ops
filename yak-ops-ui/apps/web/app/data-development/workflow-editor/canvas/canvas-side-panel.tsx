import { useMemo, type ReactNode } from "react";
import {
  Code2,
  Copy,
  Download,
  List,
  RefreshCw,
  X,
  type LucideIcon,
} from "lucide-react";
import { toast } from "@yak-ops/yak-ui";

import type { WorkflowDagRecord } from "@/service/workflow/definition";

import { buildWorkflowSpec } from "../adapter/workflow-spec";

export type CanvasPanelKey = "nodes" | "spec";

const PANEL_CONTENT: Record<
  CanvasPanelKey,
  {
    title: string;
    description: string;
    placeholder: string;
    Icon: LucideIcon;
  }
> = {
  nodes: {
    title: "内部节点列表",
    description: "查看当前工作流中的节点。",
    placeholder:
      "内部节点列表区域已预留。后续在这里实现节点搜索、列表展示和画布定位。",
    Icon: List,
  },
  spec: {
    title: "Spec",
    description: "查看当前工作流的结构化定义。",
    placeholder: "",
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
  onRefresh,
  isRefreshing,
}: {
  activePanel: CanvasPanelKey | null;
  onClose: () => void;
  dag: WorkflowDagRecord;
  onRefresh: () => Promise<void>;
  isRefreshing: boolean;
}) {
  const content = activePanel ? PANEL_CONTENT[activePanel] : null;
  const Icon = content?.Icon;
  const specText = useMemo(
    () => JSON.stringify(buildWorkflowSpec(dag), null, 2) ?? "{}",
    [dag],
  );
  const specLines = useMemo(() => specText.split("\n"), [specText]);

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
        width: content ? "min(64%, 600px)" : "0px",
        flexBasis: content ? "min(64%, 600px)" : "0px",
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
          <div className="min-h-0 flex-1 overflow-auto p-4">
            <div className="space-y-3">
              <p className="text-xs leading-5 text-[#687282]">
                {content?.description}
              </p>
              <div className="rounded-md border border-dashed border-[#dfe3e8] bg-[#fafbfc] p-4">
                <p className="text-xs leading-6 text-[#7b8492]">
                  {content?.placeholder}
                </p>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </aside>
  );
}
