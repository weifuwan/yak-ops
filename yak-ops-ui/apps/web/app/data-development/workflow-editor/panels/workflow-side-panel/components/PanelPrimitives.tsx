import type { ReactNode } from "react";
import { AlertCircle } from "lucide-react";

import { MUTED_CLASS } from "../shared/constants";

export function PanelNotice({
  children,
  tone = "info",
}: {
  children: ReactNode;
  tone?: "info" | "warning";
}) {
  return (
    <div
      className={[
        "flex items-start gap-2 rounded-md border px-3 py-2.5 text-xs leading-5",
        tone === "warning"
          ? "border-[#f2dfbd] bg-[#fffaf0] text-[#8a6424]"
          : "border-[#e3eaf7] bg-[#f6f9ff] text-[#63738e]",
      ].join(" ")}
    >
      <AlertCircle size={14} className="mt-0.5 shrink-0" />
      <div>{children}</div>
    </div>
  );
}

export function PanelButton({
  children,
  onClick,
  disabled = false,
  variant = "primary",
  title,
}: {
  children: ReactNode;
  onClick: () => void;
  disabled?: boolean;
  variant?: "primary" | "secondary" | "danger";
  title?: string;
}) {
  const style =
    variant === "primary"
      ? "border-[#315efb] bg-[#315efb] text-white hover:bg-[#244fe0]"
      : variant === "danger"
        ? "border-[#f0d5d3] bg-white text-[#c64a43] hover:bg-[#fff5f4]"
        : "border-[#dfe3e8] bg-white text-[#465064] hover:bg-[#f7f8fa]";
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={[
        "inline-flex h-8 items-center justify-center gap-1.5 rounded-md border px-3 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-45",
        style,
      ].join(" ")}
    >
      {children}
    </button>
  );
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex min-h-[150px] flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed border-[#e3e7ed] bg-[#fbfcfd] px-4 text-center">
      <span className="text-xs font-medium text-[#606b7a]">{title}</span>
      {description ? <span className={MUTED_CLASS}>{description}</span> : null}
    </div>
  );
}

