import type { ReactNode } from "react";

import { cn } from "../cn";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "../dropdown-menu";

export interface PanelHeaderProps {
  title: ReactNode;
  actions?: ReactNode;
  moreActions?: ReactNode;
  ariaLabel?: string;
  moreActionsLabel?: string;
  className?: string;
  titleClassName?: string;
}

export function PanelHeader({
  title,
  actions,
  moreActions,
  ariaLabel = "面板操作",
  moreActionsLabel = "视图和更多操作…",
  className,
  titleClassName,
}: PanelHeaderProps) {
  const hasActions = actions != null || moreActions != null;

  return (
    <header
      className={cn(
        "flex h-[35px] w-full shrink-0 items-center gap-2 border-b border-[var(--yak-components-border)] bg-[var(--yak-components-panel-bg)] px-2",
        className,
      )}
    >
      <div className="min-w-0 overflow-hidden pl-3 leading-[35px]">
        <h2
          className={cn(
            "m-0 truncate text-[11px] font-normal text-[#26282c]",
            titleClassName,
          )}
        >
          {title}
        </h2>
      </div>

      {hasActions ? (
        <div
          role="toolbar"
          aria-label={ariaLabel}
          className="flex h-[35px] min-w-0 flex-1 items-center justify-end gap-1 pl-[5px]"
        >
          {actions}
          {moreActions != null ? (
            <DropdownMenu>
              <DropdownMenuTrigger
                aria-label={moreActionsLabel}
                title={moreActionsLabel}
                className="inline-flex size-[22px] shrink-0 cursor-pointer items-center justify-center rounded-[5px] border-0 bg-transparent p-[3px] text-[var(--yak-components-muted-text)] outline-none transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)] focus-visible:ring-1 focus-visible:ring-[var(--yak-color-primary)]"
              >
                <span
                  aria-hidden="true"
                  className="flex h-4 w-4 items-center justify-center gap-[2px]"
                >
                  <span className="size-[3px] rounded-full bg-current" />
                  <span className="size-[3px] rounded-full bg-current" />
                  <span className="size-[3px] rounded-full bg-current" />
                </span>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                side="bottom"
                align="end"
                sideOffset={2}
                className="min-w-40"
              >
                {moreActions}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      ) : null}
    </header>
  );
}
