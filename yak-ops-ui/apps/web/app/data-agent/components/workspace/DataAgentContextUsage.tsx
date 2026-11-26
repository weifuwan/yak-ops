import { useCallback, useEffect, useRef, useState, type CSSProperties, type RefObject } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

import type { AgentSessionContext, AgentSessionContextBreakdown } from "@/service/data-agent";

type FloatingPosition = {
  top: number;
  left: number;
  width: number;
};

type DataAgentContextUsageProps = {
  context: AgentSessionContext | null;
  loading: boolean;
  error: string;
  hasSession: boolean;
};

const CATEGORY_ORDER = ["systemPrompt", "tools", "messages", "other", "skills"];
const CATEGORY_COLORS: Record<string, string> = {
  systemPrompt: "#625BFF",
  tools: "#10B981",
  messages: "#F59E0B",
  other: "#8B5CF6",
  mcp: "#A855F7",
  skills: "#EC4899",
};

function clampRatio(value: number) {
  return Math.max(0, Math.min(1, Number(value) || 0));
}

function formatPercent(value: number) {
  return (clampRatio(value) * 100).toFixed(1) + "%";
}

function formatTokenCount(value: number) {
  const tokens = Math.max(0, Number(value) || 0);
  if (tokens >= 1_000_000) return (tokens / 1_000_000).toFixed(1) + "M";
  if (tokens >= 1_000) return (tokens / 1_000).toFixed(1) + "K";
  return Math.round(tokens).toLocaleString("zh-CN");
}

function orderedBreakdown(breakdown: AgentSessionContextBreakdown[]) {
  return [...breakdown].sort((left, right) => {
    const leftIndex = CATEGORY_ORDER.indexOf(left.key);
    const rightIndex = CATEGORY_ORDER.indexOf(right.key);
    return (leftIndex < 0 ? CATEGORY_ORDER.length : leftIndex) -
      (rightIndex < 0 ? CATEGORY_ORDER.length : rightIndex);
  });
}

function ringBackground(context: AgentSessionContext | null) {
  if (!context) return "conic-gradient(#e7e9ed 0deg 360deg)";

  const usedRatio = clampRatio(context.ratio);
  let cursor = 0;
  const stops: string[] = [];

  orderedBreakdown(context.breakdown).forEach((item) => {
    const segment = usedRatio * clampRatio(item.ratio) * 360;
    if (segment <= 0) return;
    const end = Math.min(360, cursor + segment);
    stops.push(
      (CATEGORY_COLORS[item.key] ?? "#64748b") +
        " " + cursor.toFixed(2) + "deg " + end.toFixed(2) + "deg",
    );
    cursor = end;
  });

  if (cursor < 360) stops.push("#e7e9ed " + cursor.toFixed(2) + "deg 360deg");
  return "conic-gradient(from -90deg, " + stops.join(", ") + ")";
}

function getPopoverPosition(rect: DOMRect): FloatingPosition {
  const margin = 12;
  const width = Math.min(352, window.innerWidth - margin * 2);
  const height = Math.min(420, window.innerHeight - margin * 2);
  const left = Math.max(
    margin,
    Math.min(rect.right - width, window.innerWidth - width - margin),
  );
  const above = rect.top - height - 10;
  const top = above >= margin
    ? above
    : Math.min(window.innerHeight - height - margin, rect.bottom + 10);
  return { top, left, width };
}

function getTooltipPosition(rect: DOMRect): CSSProperties {
  const width = Math.min(300, window.innerWidth - 16);
  return {
    top: Math.max(8, rect.top - 38),
    left: Math.max(8, Math.min(rect.left + rect.width / 2 - width / 2, window.innerWidth - width - 8)),
    width,
  };
}

function ContextUsageSkeleton() {
  return (
    <div className="animate-pulse space-y-4 px-5 pb-5 pt-4" aria-label="正在加载上下文详情">
      <div className="h-7 w-24 rounded bg-[#f0f1f4]" />
      <div className="h-2.5 rounded-full bg-[#f0f1f4]" />
      <div className="space-y-3 pt-1">
        {Array.from({ length: 5 }, (_, index) => (
          <div key={index} className="flex items-center justify-between">
            <div className="h-3 w-28 rounded bg-[#f0f1f4]" />
            <div className="h-3 w-10 rounded bg-[#f0f1f4]" />
          </div>
        ))}
      </div>
      <div className="h-3 w-full rounded bg-[#f0f1f4]" />
    </div>
  );
}

function ContextUsageDetails({
  context,
  loading,
  error,
  position,
  cardRef,
  onClose,
}: {
  context: AgentSessionContext | null;
  loading: boolean;
  error: string;
  position: FloatingPosition;
  cardRef: RefObject<HTMLDivElement>;
  onClose: () => void;
}) {
  const style: CSSProperties = {
    top: position.top,
    left: position.left,
    width: position.width,
    maxHeight: "min(420px, calc(100vh - 24px))",
  };
  const breakdown = context ? orderedBreakdown(context.breakdown) : [];
  const usedRatio = context ? clampRatio(context.ratio) : 0;
  const triggerRatio = context?.limitTokens
    ? clampRatio(context.triggerTokens / context.limitTokens)
    : 0;

  return (
    <div
      ref={cardRef}
      role="dialog"
      aria-label="上下文用量详情"
      className="fixed z-[140] overflow-y-auto rounded-[18px] border border-[#e8e8ec] bg-white text-[#202124] shadow-[0_14px_40px_rgba(0,0,0,0.16)]"
      style={style}
    >
      <div className="flex items-center justify-between px-5 pb-1 pt-4">
        <h3 className="m-0 text-[14px] font-medium text-[#202124]">上下文用量</h3>
        <button
          type="button"
          aria-label="关闭上下文用量详情"
          className="flex size-7 cursor-pointer items-center justify-center rounded-full text-[#858585] transition-colors hover:bg-[#f3f4f6] hover:text-[#303030]"
          onClick={onClose}
        >
          <X size={16} strokeWidth={1.8} />
        </button>
      </div>

      {loading ? (
        <ContextUsageSkeleton />
      ) : error ? (
        <div role="alert" className="px-5 pb-5 pt-4 text-xs leading-5 text-[#8b8b8b]">
          {error}
        </div>
      ) : context ? (
        <div className="px-5 pb-5 pt-2">
          <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
            <span className="text-[26px] font-semibold leading-8 tracking-[-0.04em] text-[#202124]">
              {formatPercent(usedRatio)}
            </span>
            <span className="text-xs text-[#777d87]">
              已使用 {formatTokenCount(context.usedTokens)} / {formatTokenCount(context.limitTokens)}
            </span>
          </div>

          <div
            className="relative mt-3 flex h-2.5 overflow-hidden rounded-full bg-[#e9eaee]"
            aria-label={"上下文使用率 " + formatPercent(usedRatio)}
          >
            {breakdown.map((item) => (
              <span
                key={item.key}
                className="h-full shrink-0"
                style={{
                  width: (usedRatio * clampRatio(item.ratio) * 100) + "%",
                  backgroundColor: CATEGORY_COLORS[item.key] ?? "#64748b",
                }}
              />
            ))}
            {triggerRatio > 0 ? (
              <span
                aria-hidden="true"
                className="absolute bottom-0 top-0 z-10 w-px bg-[#4b5563]/55"
                style={{ left: (triggerRatio * 100) + "%" }}
              />
            ) : null}
          </div>

          <div className="mt-5 flex flex-col gap-3">
            {breakdown.map((item) => (
              <div key={item.key} className="flex items-center justify-between gap-4">
                <div className="flex min-w-0 items-center gap-2.5">
                  <span
                    aria-hidden="true"
                    className="size-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: CATEGORY_COLORS[item.key] ?? "#64748b" }}
                  />
                  <span className="truncate text-[13px] text-[#555b65]">{item.label}</span>
                </div>
                <span className="shrink-0 text-[13px] font-medium tabular-nums text-[#30343b]">
                  {formatPercent(item.ratio)}
                </span>
              </div>
            ))}
          </div>

          <div className="mt-5 space-y-3 border-t border-[#eceef1] pt-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-[#858b95]">平均缓存命中率</span>
              <span className="text-xs font-semibold tabular-nums text-[#30343b]">
                {formatPercent(context.averageCacheHitRate)}
              </span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-[#858b95]">上下文压缩</span>
              <span className="text-xs font-medium text-[#555b65]">
                {context.compressed ? "已触发" : "未触发"}
              </span>
            </div>
          </div>
        </div>
      ) : (
        <div className="px-5 pb-5 pt-4 text-xs text-[#858b95]">暂无上下文数据</div>
      )}
    </div>
  );
}

export function DataAgentContextUsage({
  context,
  loading,
  error,
  hasSession,
}: DataAgentContextUsageProps) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState(false);
  const [open, setOpen] = useState(false);
  const [popoverPosition, setPopoverPosition] = useState<FloatingPosition | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<CSSProperties | null>(null);

  const close = useCallback(() => setOpen(false), []);

  const toggleDetails = useCallback(() => {
    if (!hasSession) return;
    if (open) {
      setOpen(false);
      return;
    }
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    setPopoverPosition(getPopoverPosition(rect));
    setOpen(true);
  }, [hasSession, open]);

  const showTooltip = useCallback(() => {
    if (open) return;
    const rect = triggerRef.current?.getBoundingClientRect();
    if (rect) setTooltipPosition(getTooltipPosition(rect));
    setHovered(true);
  }, [open]);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (triggerRef.current?.contains(target) || cardRef.current?.contains(target)) return;
      setOpen(false);
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }

    function handleViewportChange() {
      setOpen(false);
    }

    document.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("resize", handleViewportChange);
    window.addEventListener("scroll", handleViewportChange, true);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("resize", handleViewportChange);
      window.removeEventListener("scroll", handleViewportChange, true);
    };
  }, [open]);

  const tooltipText = !hasSession
    ? "发送消息后显示上下文用量"
    : loading
      ? "正在获取上下文用量…"
      : error
        ? error
        : context
          ? formatPercent(context.ratio) + " · " +
            formatTokenCount(context.usedTokens) + " / " +
            formatTokenCount(context.limitTokens) + " 上下文已使用"
          : "暂无上下文用量数据";

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label="查看上下文用量"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-disabled={!hasSession}
        className={
          "flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors hover:bg-[#f3f4f6] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#8b8b8b]/25 " +
          (open ? "bg-[#f3f4f6]" : "")
        }
        onMouseEnter={showTooltip}
        onMouseLeave={() => setHovered(false)}
        onFocus={showTooltip}
        onBlur={() => setHovered(false)}
        onClick={toggleDetails}
      >
        <span
          aria-hidden="true"
          className="relative size-[21px] rounded-full"
          style={{ background: ringBackground(context) }}
        >
          <span className="absolute inset-[3px] rounded-full bg-white" />
        </span>
      </button>

      {typeof document !== "undefined" && hovered && !open && tooltipPosition
        ? createPortal(
            <div
              role="tooltip"
              className="pointer-events-none fixed z-[150] rounded-lg border border-[#e8e8ec] bg-white px-3 py-2 text-[11px] leading-4 text-[#4f5661] shadow-[0_5px_18px_rgba(0,0,0,0.12)]"
              style={tooltipPosition}
            >
              {tooltipText}
            </div>,
            document.body,
          )
        : null}

      {typeof document !== "undefined" && open && popoverPosition
        ? createPortal(
            <ContextUsageDetails
              context={context}
              loading={loading}
              error={error}
              position={popoverPosition}
              cardRef={cardRef}
              onClose={close}
            />,
            document.body,
          )
        : null}
    </>
  );
}

export default DataAgentContextUsage;
