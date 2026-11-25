import { useEffect, useId, useState, type ReactNode } from "react";
import {
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  LoaderCircle,
  ShieldCheck,
  Wrench,
  XCircle,
} from "lucide-react";

import { DataAgentMarkdown } from "../../markdown/DataAgentMarkdown";
import type { AgentActivity } from "../../../model/types";
import { TOOL_LABELS } from "../../../model/constants";
import { formatToolInput, toolStatusLabel } from "../../../utils/message-formatters";

type AgentActivityDetailsProps = {
  activity: AgentActivity;
  expandAll: boolean;
  messageError?: string;
};

type DetailSectionProps = {
  label: string;
  expanded: boolean;
  onToggle: () => void;
  children: ReactNode;
};

function DetailSection({
  label,
  expanded,
  onToggle,
  children,
}: DetailSectionProps) {
  const id = useId();

  return (
    <section className="min-w-0 overflow-hidden rounded-xl border border-[#e7e9e8] bg-white">
      <button
        type="button"
        aria-expanded={expanded}
        aria-controls={"agent-activity-detail-" + id.replace(/:/g, "")}
        className={[
          "flex min-h-10 w-full cursor-pointer items-center justify-between gap-3",
          "px-3 py-2.5 text-left transition-colors hover:bg-[#f7f8f8]",
          "focus-visible:outline-none focus-visible:ring-2",
          "focus-visible:ring-inset focus-visible:ring-[#0033ff]/20",
        ].join(" ")}
        onClick={onToggle}
      >
        <span className="text-xs font-semibold text-[#46514c]">{label}</span>
        <ChevronDown
          size={14}
          className={
            "shrink-0 text-[#89938e] transition-transform duration-200 " +
            (expanded ? "rotate-180" : "")
          }
        />
      </button>
      <div
        id={"agent-activity-detail-" + id.replace(/:/g, "")}
        hidden={!expanded}
      >
        {expanded ? (
          <div className="min-w-0 border-t border-[#edf0ee] p-3">{children}</div>
        ) : null}
      </div>
    </section>
  );
}

function CodeSection({
  label,
  value,
  expanded,
  onToggle,
}: {
  label: string;
  value: string;
  expanded: boolean;
  onToggle: () => void;
}) {
  if (!value.trim()) return null;

  return (
    <DetailSection label={label} expanded={expanded} onToggle={onToggle}>
      <pre
        className={[
          "m-0 max-h-[420px] min-w-0 overflow-auto whitespace-pre-wrap break-words",
          "rounded-lg bg-[#f7f8f8] p-3 font-mono text-[12px] leading-6",
          "text-[#303936]",
        ].join(" ")}
      >
        {formatToolInput(value)}
      </pre>
    </DetailSection>
  );
}

function statusIcon(status: AgentActivity["status"]) {
  if (status === "running") return LoaderCircle;
  if (status === "error" || status === "interrupted") return CircleAlert;
  if (status === "denied" || status === "rejected") return XCircle;
  if (status === "awaiting-confirmation") return ShieldCheck;
  return CheckCircle2;
}

function statusClassName(status: AgentActivity["status"]) {
  if (
    status === "error" ||
    status === "denied" ||
    status === "rejected" ||
    status === "interrupted"
  ) {
    return "border-[#f0d8d8] bg-[#fff7f7] text-[#b45c5c]";
  }
  if (status === "running" || status === "awaiting-confirmation") {
    return "border-[#e9dfc8] bg-[#fffbf2] text-[#9a7b42]";
  }
  return "border-[#dce8e1] bg-[#f5faf7] text-[#668574]";
}

export function AgentActivityDetails({
  activity,
  expandAll,
  messageError,
}: AgentActivityDetailsProps) {
  const [expandedSections, setExpandedSections] = useState({
    content: true,
    input: true,
    output: true,
  });

  useEffect(() => {
    setExpandedSections({
      content: expandAll,
      input: expandAll,
      output: expandAll,
    });
  }, [activity.id, expandAll]);

  const title =
    activity.kind === "tool"
      ? TOOL_LABELS[activity.toolName ?? ""] ??
        activity.toolName ??
        activity.title
      : activity.title;
  const StatusIcon = statusIcon(activity.status);
  const content = activity.content?.trim() ?? "";
  const hasInput = Boolean(activity.input?.trim());
  const hasOutput = Boolean(activity.output?.trim());
  const hasDetails = Boolean(content || hasInput || hasOutput);

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections((current) => ({
      ...current,
      [section]: !current[section],
    }));
  };

  return (
    <div className="flex min-w-0 flex-col gap-4 px-4 pb-5 pt-4">
      <div className="flex min-w-0 items-center gap-3 border-b border-[#edf0ee] pb-4">
        <span
          className={[
            "flex size-9 shrink-0 items-center justify-center rounded-lg",
            "bg-[#f0f3f2] text-[#52635c]",
          ].join(" ")}
        >
          <Wrench size={16} strokeWidth={1.6} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="m-0 truncate text-sm font-semibold text-[#29332e]">
            {title}
          </h2>
          <p className="m-0 mt-0.5 truncate text-[11px] text-[#929b96]">
            {activity.kind === "tool" ? "工具调用" : "执行步骤"}
          </p>
        </div>
        <span
          className={[
            "flex shrink-0 items-center gap-1.5 rounded-full border px-2 py-1",
            "text-[11px] font-medium",
            statusClassName(activity.status),
          ].join(" ")}
        >
          <StatusIcon
            size={12}
            className={activity.status === "running" ? "animate-spin" : ""}
          />
          {toolStatusLabel(activity.status)}
        </span>
      </div>

      {content ? (
        <DetailSection
          label="执行说明"
          expanded={expandedSections.content}
          onToggle={() => toggleSection("content")}
        >
          <div className="min-w-0 text-[13px] leading-6 text-[#303936]">
            <DataAgentMarkdown
              content={content}
              isStreaming={activity.status === "running"}
            />
          </div>
        </DetailSection>
      ) : null}

      <CodeSection
        label="输入参数"
        value={activity.input ?? ""}
        expanded={expandedSections.input}
        onToggle={() => toggleSection("input")}
      />

      <CodeSection
        label="执行结果"
        value={activity.output ?? ""}
        expanded={expandedSections.output}
        onToggle={() => toggleSection("output")}
      />

      {!hasDetails ? (
        <div
          className={[
            "flex min-h-32 items-center justify-center rounded-xl",
            "border border-dashed border-[#e5e9e7] px-6 text-center",
            "text-xs leading-5 text-[#929b96]",
          ].join(" ")}
        >
          {activity.status === "running"
            ? "工具正在执行，结果返回后会显示在这里"
            : "该工具暂无可展示的输入参数或执行结果"}
        </div>
      ) : null}

      {messageError ? (
        <div
          role="alert"
          className={[
            "rounded-lg border border-[#f0d6d6] bg-[#fff7f7]",
            "px-3 py-2 text-xs leading-5 text-[#a14d4d]",
          ].join(" ")}
        >
          {messageError}
        </div>
      ) : null}
    </div>
  );
}
