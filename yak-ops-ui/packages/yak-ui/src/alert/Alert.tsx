import type { HTMLAttributes, ReactNode } from "react";

import { cn } from "../cn";

export interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, "children"> {
  children: ReactNode;
}

function AlertWarningIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4">
      <path
        fill="currentColor"
        d="M8.57 3.05a1.65 1.65 0 0 1 2.86 0l6.16 10.68A1.65 1.65 0 0 1 16.16 16H3.84a1.65 1.65 0 0 1-1.43-2.27L8.57 3.05Z"
      />
      <path
        fill="white"
        d="M9.2 7.05a.8.8 0 1 1 1.6 0v4.05a.8.8 0 1 1-1.6 0V7.05Zm.8 6.9a.95.95 0 1 1 0-1.9.95.95 0 0 1 0 1.9Z"
      />
    </svg>
  );
}

export function Alert({ children, className, ...props }: AlertProps) {
  return (
    <div
      {...props}
      role="alert"
      className={cn(
        "flex min-h-9 w-full items-center gap-2 rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-alert-warning-border)] bg-[var(--yak-components-alert-warning-bg)] px-3 py-2 text-xs leading-5 text-[var(--yak-components-alert-warning-text)]",
        className,
      )}
    >
      <span className="shrink-0 text-[var(--yak-components-alert-warning-icon)]">
        <AlertWarningIcon />
      </span>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 [overflow-wrap:anywhere]">
        {children}
      </div>
    </div>
  );
}
