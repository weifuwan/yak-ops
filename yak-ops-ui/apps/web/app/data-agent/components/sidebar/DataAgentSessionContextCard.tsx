import type { CSSProperties } from "react";

import type { AgentSessionContext } from "@/service/data-agent";

type ContextPosition = {
  top: number;
  left: number;
};

type DataAgentSessionContextCardProps = {
  context: AgentSessionContext | null;
  loading: boolean;
  error: string;
  position: ContextPosition;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
};

const CATEGORY_COLORS: Record<string, string> = {
  messages: "#60a5fa",
  tools: "#3b82f6",
  mcp: "#4f83cc",
  other: "#2563eb",
  skills: "#1d4ed8",
  systemPrompt: "#64748b",
};

function formatTokenCount(value: number) {
  const tokens = Math.max(0, Number(value) || 0);
  if (tokens >= 10_000) {
    return (tokens / 10_000).toFixed(1).replace(/\.0$/, "") + "万";
  }
  return Math.round(tokens).toLocaleString("zh-CN");
}

function formatPercent(value: number) {
  return (Math.max(0, Math.min(1, Number(value) || 0)) * 100)
    .toFixed(1)
    .replace(/\.0$/, "") + "%";
}

function ContextLoadingState() {
  return (
    <div className="animate-pulse space-y-4 px-4 pb-4 pt-4" aria-label="正在加载上下文统计">
      <div className="h-4 w-32 rounded bg-[#414141]" />
      <div className="h-2.5 rounded-full bg-[#3a3a3a]" />
      <div className="space-y-3 pt-1">
        {Array.from({ length: 5 }, (_, index) => (
          <div key={index} className="flex items-center justify-between">
            <div className="h-3 w-20 rounded bg-[#414141]" />
            <div className="h-3 w-10 rounded bg-[#414141]" />
          </div>
        ))}
      </div>
      <div className="border-t border-[#3d3d3d] pt-3">
        <div className="h-3 w-full rounded bg-[#414141]" />
      </div>
    </div>
  );
}

export function DataAgentSessionContextCard({
  context,
  loading,
  error,
  position,
  onMouseEnter,
  onMouseLeave,
}: DataAgentSessionContextCardProps) {
  const style: CSSProperties = {
    top: position.top,
    left: position.left,
    width: 360,
    maxHeight: "min(420px, calc(100vh - 24px))",
  };

  const ratio = context
    ? Math.max(0, Math.min(1, Number(context.ratio) || 0))
    : 0;
  const triggerRatio = context?.limitTokens
    ? Math.max(0, Math.min(1, context.triggerTokens / context.limitTokens))
    : 0;

  return (
    <div
      role="tooltip"
      aria-label="上下文容量"
      className="fixed z-[120] overflow-hidden rounded-[14px] border border-[#3e3e3e] bg-[#242424] text-[#f5f5f5] shadow-[0_16px_42px_rgba(0,0,0,0.34)] transition-[opacity,transform] duration-150"
      style={style}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      {loading ? (
        <ContextLoadingState />
      ) : error ? (
        <div className="px-4 py-5 text-xs leading-5 text-[#a8a8a8]">{error}</div>
      ) : context ? (
        <div className="px-4 pb-4 pt-4">
          <div className="flex items-baseline justify-between gap-3">
            <span className="text-sm font-medium text-[#eeeeee]">上下文容量</span>
            <span className="whitespace-nowrap text-xs tabular-nums text-[#a7a7a7]">
              {formatTokenCount(context.usedTokens)}/{formatTokenCount(context.limitTokens)}
              <span className="ml-1.5 text-[#e4e4e4]">({formatPercent(ratio)})</span>
            </span>
          </div>

          <div
            className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[#3b3b3b]"
            aria-label={"上下文使用率 " + formatPercent(ratio)}
          >
            <div
              className="h-full rounded-full bg-gradient-to-r from-[#3898ff] to-[#63b5ff] transition-[width] duration-300"
              style={{ width: formatPercent(ratio) }}
            />
            {triggerRatio > 0 ? (
              <span
                aria-hidden="true"
                className="absolute bottom-0 top-0 w-px bg-[#d4e6ff]/80"
                style={{ left: formatPercent(triggerRatio) }}
              />
            ) : null}
          </div>

          {context.breakdown.length > 0 ? (
            <div className="mt-4 flex flex-col gap-3">
              {context.breakdown.map((item) => (
                <div key={item.key} className="flex items-center justify-between gap-4">
                  <div className="flex min-w-0 items-center gap-2.5 text-[13px] text-[#a8a8a8]">
                    <span
                      aria-hidden="true"
                      className="size-2 shrink-0 rounded-full"
                      style={{ backgroundColor: CATEGORY_COLORS[item.key] ?? "#64748b" }}
                    />
                    <span className="truncate">{item.label}</span>
                  </div>
                  <span className="shrink-0 text-[13px] font-semibold tabular-nums text-[#eeeeee]">
                    {formatPercent(item.ratio)}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="mb-0 mt-4 text-xs text-[#8f8f8f]">暂无上下文明细</p>
          )}

          <div className="mt-4 flex items-center justify-between border-t border-[#3d3d3d] pt-3">
            <span className="text-xs text-[#929292]">平均缓存命中率</span>
            <span className="text-xs font-semibold tabular-nums text-[#eeeeee]">
              {formatPercent(context.averageCacheHitRate)}
            </span>
          </div>
        </div>
      ) : (
        <div className="px-4 py-5 text-xs text-[#a8a8a8]">暂无上下文数据</div>
      )}
    </div>
  );
}

export default DataAgentSessionContextCard;
