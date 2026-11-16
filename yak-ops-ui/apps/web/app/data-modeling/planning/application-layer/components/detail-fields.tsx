import type { ReactNode } from "react";

const TITLE = "flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";

export const DETAIL_CARD_CLASS = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";

const display = (value?: string | null) => value && value.trim() ? value : "-";

export function SectionTitle({ children }: { children: ReactNode }) {
  return <h3 className={TITLE}><span className="h-4 w-1 rounded-sm bg-[#5664ff]" />{children}</h3>;
}

export function DescriptionItem({ label, value, className = "", multiline = false }: {
  label: string; value?: string | null; className?: string; multiline?: boolean;
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
