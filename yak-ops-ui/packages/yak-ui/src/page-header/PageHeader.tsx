import type { ReactNode } from "react";

import { cn } from "../cn";

export interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  extra?: ReactNode;
  bordered?: boolean;
  className?: string;
}

export function PageHeader({
  title,
  description,
  extra,
  bordered = false,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex w-full flex-wrap items-start justify-between gap-x-6 gap-y-3 py-4",
        bordered && "border-b border-[var(--yak-components-page-header-border)]",
        className,
      )}
    >
      <div className="min-w-0 basis-full sm:basis-auto sm:flex-1">
        <h1 className="text-lg leading-7 font-semibold text-[var(--yak-components-page-header-title)]">
          {title}
        </h1>
        {description != null ? (
          <div className="mt-1 text-sm leading-5 text-[var(--yak-components-page-header-description)]">
            {description}
          </div>
        ) : null}
      </div>
      {extra != null ? <div className="flex shrink-0 items-center gap-2 sm:ml-auto">{extra}</div> : null}
    </header>
  );
}
