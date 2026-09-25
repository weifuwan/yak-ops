import { Menu as BaseMenu } from "@base-ui/react/menu";
import type { ReactNode } from "react";

import { cn } from "../cn";

export const DropdownMenu = BaseMenu.Root;
export const DropdownMenuTrigger = BaseMenu.Trigger;
export const DropdownMenuGroup = BaseMenu.Group;

export type DropdownMenuContentProps = Omit<BaseMenu.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  side?: BaseMenu.Positioner.Props["side"];
  align?: BaseMenu.Positioner.Props["align"];
  sideOffset?: BaseMenu.Positioner.Props["sideOffset"];
};

export function DropdownMenuContent({
  children,
  className,
  side = "top",
  align = "start",
  sideOffset = 6,
  ...props
}: DropdownMenuContentProps) {
  return (
    <BaseMenu.Portal>
      <BaseMenu.Positioner
        side={side}
        align={align}
        sideOffset={sideOffset}
        className="z-60 outline-none"
      >
        <BaseMenu.Popup
          {...props}
          className={cn(
            "min-w-48 overflow-hidden rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-1 text-sm text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-panel-shadow)] outline-none",
            "transition-[opacity,transform] data-starting-style:scale-95 data-starting-style:opacity-0 data-ending-style:scale-95 data-ending-style:opacity-0 motion-reduce:transition-none",
            className,
          )}
        >
          {children}
        </BaseMenu.Popup>
      </BaseMenu.Positioner>
    </BaseMenu.Portal>
  );
}

export type DropdownMenuItemProps = Omit<BaseMenu.Item.Props, "className"> & {
  className?: string;
  tone?: "default" | "danger";
};

export function DropdownMenuItem({ className, tone = "default", ...props }: DropdownMenuItemProps) {
  return (
    <BaseMenu.Item
      data-tone={tone}
      className={cn(
        "flex min-h-8 cursor-pointer items-center gap-2 rounded-lg px-2.5 py-1.5 text-[13px] outline-none",
        "data-highlighted:bg-[var(--yak-components-menu-item-hover)] data-disabled:cursor-not-allowed data-disabled:opacity-40",
        "data-[tone=danger]:text-[var(--yak-components-danger-text)]",
        className,
      )}
      {...props}
    />
  );
}

export type DropdownMenuLabelProps = Omit<BaseMenu.GroupLabel.Props, "className"> & {
  className?: string;
};
export function DropdownMenuLabel({ className, ...props }: DropdownMenuLabelProps) {
  return (
    <BaseMenu.GroupLabel
      className={cn(
        "px-2.5 py-1.5 text-[11px] font-medium text-[var(--yak-components-muted-text)]",
        className,
      )}
      {...props}
    />
  );
}

export type DropdownMenuSeparatorProps = Omit<BaseMenu.Separator.Props, "className"> & {
  className?: string;
};
export function DropdownMenuSeparator({ className, ...props }: DropdownMenuSeparatorProps) {
  return (
    <BaseMenu.Separator
      className={cn("my-1 h-px bg-[var(--yak-components-control-border)]", className)}
      {...props}
    />
  );
}
