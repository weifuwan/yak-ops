import { Popover as BasePopover } from "@base-ui/react/popover";
import type { ReactNode } from "react";

import { cn } from "../cn";

export const Popover = BasePopover.Root;
export const PopoverTrigger = BasePopover.Trigger;
export const PopoverClose = BasePopover.Close;
export const PopoverTitle = BasePopover.Title;
export const PopoverDescription = BasePopover.Description;

export type PopoverContentProps = Omit<BasePopover.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  side?: BasePopover.Positioner.Props["side"];
  align?: BasePopover.Positioner.Props["align"];
  sideOffset?: BasePopover.Positioner.Props["sideOffset"];
};

export function PopoverContent({
  children,
  className,
  side = "bottom",
  align = "center",
  sideOffset = 8,
  ...props
}: PopoverContentProps) {
  return (
    <BasePopover.Portal>
      <BasePopover.Positioner side={side} align={align} sideOffset={sideOffset} className="z-60 outline-none">
        <BasePopover.Popup
          {...props}
          className={cn(
            "rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-3 text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-panel-shadow)] outline-none",
            "transition-[opacity,transform] data-starting-style:scale-95 data-starting-style:opacity-0 data-ending-style:scale-95 data-ending-style:opacity-0 motion-reduce:transition-none",
            className,
          )}
        >
          {children}
        </BasePopover.Popup>
      </BasePopover.Positioner>
    </BasePopover.Portal>
  );
}
