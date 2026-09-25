import { Tooltip as BaseTooltip } from "@base-ui/react/tooltip";
import type { ReactNode } from "react";

import { cn } from "../cn";

export const TooltipProvider = BaseTooltip.Provider;
export const Tooltip = BaseTooltip.Root;
export const TooltipTrigger = BaseTooltip.Trigger;

export type TooltipContentProps = Omit<BaseTooltip.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  side?: BaseTooltip.Positioner.Props["side"];
  align?: BaseTooltip.Positioner.Props["align"];
  sideOffset?: BaseTooltip.Positioner.Props["sideOffset"];
};

export function TooltipContent({
  children,
  className,
  side = "top",
  align = "center",
  sideOffset = 6,
  ...props
}: TooltipContentProps) {
  return (
    <BaseTooltip.Portal>
      <BaseTooltip.Positioner
        side={side}
        align={align}
        sideOffset={sideOffset}
        className="z-60 outline-none"
      >
        <BaseTooltip.Popup
          {...props}
          className={cn(
            "max-w-72 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-tooltip-bg)] px-2.5 py-1.5 text-xs leading-5 text-[var(--yak-components-tooltip-text)] shadow-[var(--yak-components-panel-shadow)] outline-none",
            "transition-[opacity,transform] data-starting-style:scale-95 data-starting-style:opacity-0 data-ending-style:scale-95 data-ending-style:opacity-0 motion-reduce:transition-none",
            className,
          )}
        >
          {children}
        </BaseTooltip.Popup>
      </BaseTooltip.Positioner>
    </BaseTooltip.Portal>
  );
}
