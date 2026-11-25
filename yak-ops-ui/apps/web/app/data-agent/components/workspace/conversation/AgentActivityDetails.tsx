import { useEffect, useId, useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";

import { DataAgentMarkdown } from "../../markdown/DataAgentMarkdown";
import type { AgentActivity } from "../../../model/types";
import { formatToolInput } from "../../../utils/message-formatters";

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
    <section className="min-w-0">
      <button
        type="button"
        aria-expanded={expanded}
        aria-controls={"agent-activity-detail-" + id.replace(/:/g, "")}
        className={[
          "flex min-h-9 w-full cursor-pointer items-center justify-between gap-3",
          "rounded-md px-1.5 py-2 text-left transition-colors hover:bg-[#f7f8f8]",
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
        {expanded ? <div className="min-w-0 px-1 pt-2">{children}</div> : null}
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
          "rounded-lg bg-[#f7f8f8] px-3 py-2.5 font-mono text-[12px] leading-[1.75]",
          "text-[#303936]",
        ].join(" ")}
      >
        {formatToolInput(value)}
      </pre>
    </DetailSection>
  );
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

  const content = activity.content?.trim() ?? "";
  const hasDetails = Boolean(
    content || activity.input?.trim() || activity.output?.trim(),
  );

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections((current) => ({
      ...current,
      [section]: !current[section],
    }));
  };

  return (
    <div className="flex min-w-0 flex-col gap-5 px-4 pb-5 pt-4">
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
            "flex min-h-32 items-center justify-center rounded-lg",
            "bg-[#f7f8f8] px-6 text-center text-xs leading-5 text-[#929b96]",
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
            "rounded-lg bg-[#fff7f7]",
            "px-3 py-2 text-xs leading-5 text-[#a14d4d]",
          ].join(" ")}
        >
          {messageError}
        </div>
      ) : null}
    </div>
  );
}
