import type { ReactNode } from "react";

import { cn } from "../cn";

export interface EmptyProps {
  title?: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function Empty({ action, className, description, icon, title }: EmptyProps) {
  return (
    <div
      className={cn(
        "flex min-h-40 flex-col items-center justify-center px-6 py-8 text-center",
        className,
      )}
    >
      {icon ? <div className="mb-3 text-[var(--yak-components-empty-icon)]">{icon}</div> : null}
      {title ? (
        <div className="text-sm font-medium text-[var(--yak-components-empty-title)]">{title}</div>
      ) : null}
      {description ? (
        <div className="mt-1 max-w-md text-xs leading-5 text-[var(--yak-components-empty-description)]">
          {description}
        </div>
      ) : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
