import { Fragment, type ReactNode } from "react";
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { Ellipsis } from "lucide-react";

export interface OperationsTableAction {
  key: string;
  label: string;
  onClick: () => void;
  tone?: "primary" | "danger" | "muted";
  disabled?: boolean;
  loading?: boolean;
  separatorBefore?: boolean;
}

interface OperationsTableActionsProps {
  actions: OperationsTableAction[];
  maxVisible?: number;
  after?: ReactNode;
  busy?: boolean;
}

const ACTION_TONE_CLASS: Record<NonNullable<OperationsTableAction["tone"]>, string> = {
  primary: "!text-[var(--yak-color-primary)] hover:!text-[var(--yak-color-primary)]",
  danger: "!text-[var(--yak-components-danger-text)] hover:!text-[var(--yak-components-danger-text)]",
  muted: "!text-[var(--yak-components-collapse-section-icon)] hover:!text-[var(--yak-components-collapse-section-icon)]",
};

const ACTION_DIVIDER_CLASS =
  "mx-0.5 h-3 w-px bg-[var(--yak-components-table-border-strong)]";

export function OperationsTableActions({
  actions,
  maxVisible = 2,
  after,
  busy = false,
}: OperationsTableActionsProps) {
  const visibleActions = actions.slice(0, maxVisible);
  const moreActions = actions.slice(maxVisible);
  const hasLoadingAction = actions.some((action) => action.loading);

  return (
    <div className="flex items-center justify-center gap-0 whitespace-nowrap">
      {visibleActions.map((action, index) => (
        <Fragment key={action.key}>
          {index > 0 ? <span className={ACTION_DIVIDER_CLASS} /> : null}
          <Button
            variant="ghost"
            size="sm"
            isPending={action.loading}
            isDisabled={action.disabled}
            className={`px-1 text-xs font-normal ${ACTION_TONE_CLASS[action.tone ?? "primary"]}`}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        </Fragment>
      ))}

      {moreActions.length > 0 ? (
        <>
          {visibleActions.length > 0 ? <span className={ACTION_DIVIDER_CLASS} /> : null}
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label="更多操作"
              disabled={busy || hasLoadingAction}
              className="inline-flex h-7 cursor-pointer items-center gap-1 rounded-[var(--yak-radius-control-small)] border-0 bg-transparent px-1.5 text-xs text-[var(--yak-components-collapse-section-icon)] outline-none hover:bg-[var(--yak-components-menu-item-hover)] hover:text-[var(--yak-color-primary)] disabled:cursor-not-allowed disabled:opacity-45"
            >
              更多
              <Ellipsis className="h-3.5 w-3.5" />
            </DropdownMenuTrigger>
            <DropdownMenuContent side="bottom" align="end" className="w-36">
              {moreActions.map((action, index) => (
                <Fragment key={action.key}>
                  {index > 0 || action.separatorBefore ? <DropdownMenuSeparator /> : null}
                  <DropdownMenuItem
                    tone={action.tone === "danger" ? "danger" : "default"}
                    disabled={busy || action.disabled || action.loading}
                    onClick={action.onClick}
                  >
                    {action.label}
                  </DropdownMenuItem>
                </Fragment>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      ) : null}

      {after}
    </div>
  );
}
