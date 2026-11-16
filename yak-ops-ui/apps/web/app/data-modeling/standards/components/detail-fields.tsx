import type { ReactNode } from "react";

export const STANDARD_DETAIL_CARD_CLASS = "mb-4 rounded-md border border-[#e7e9ee] bg-white p-4";
const TITLE = "flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";

export function SectionTitle({ children }: { children: ReactNode }) {
  return <h3 className={TITLE}><span className="h-4 w-1 rounded-sm bg-[var(--yak-color-primary)]" />{children}</h3>;
}

function display(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "boolean") return value ? "是" : "否";
  return String(value);
}

export function DescriptionItem({ label, value, className = "", multiline = false }: {
  label: string; value: unknown; className?: string; multiline?: boolean;
}) {
  return (
    <div className={"flex min-w-0 items-baseline " + className}>
      <span className="shrink-0 text-xs leading-[22px] text-[rgba(128,128,128,1)]">{label}：</span>
      <span className={"min-w-0 pl-3 text-xs leading-[22px] text-black " + (multiline ? "whitespace-pre-wrap break-words" : "break-words")}>
        {display(value)}
      </span>
    </div>
  );
}
