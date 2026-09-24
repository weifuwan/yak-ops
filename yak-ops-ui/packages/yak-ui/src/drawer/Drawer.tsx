import { Drawer as BaseDrawer } from "@base-ui/react/drawer";
import type { ReactNode } from "react";

import { cn } from "../cn";

export const Drawer = BaseDrawer.Root;
export const DrawerTrigger = BaseDrawer.Trigger;
export const DrawerClose = BaseDrawer.Close;
export const DrawerTitle = BaseDrawer.Title;
export const DrawerDescription = BaseDrawer.Description;

export type DrawerContentProps = Omit<BaseDrawer.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  side?: "left" | "right";
  width?: number | string;
};

export function DrawerContent({
  children,
  className,
  side = "right",
  width = 620,
  style,
  ...props
}: DrawerContentProps) {
  const direction = side === "right" ? "right" : "left";

  return (
    <BaseDrawer.Portal>
      <BaseDrawer.Backdrop className="fixed inset-0 z-50 bg-[var(--yak-components-overlay)] transition-opacity data-starting-style:opacity-0 data-ending-style:opacity-0 motion-reduce:transition-none" />
      <BaseDrawer.Popup
        {...props}
        swipeDirection={direction}
        style={{ width, ...style }}
        className={cn(
          "fixed inset-y-0 z-50 flex max-w-[calc(100vw-1rem)] flex-col overflow-hidden border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-dialog-shadow)] outline-none",
          side === "right"
            ? "right-0 border-l data-starting-style:translate-x-full data-ending-style:translate-x-full"
            : "left-0 border-r data-starting-style:-translate-x-full data-ending-style:-translate-x-full",
          "transition-transform duration-200 motion-reduce:transition-none",
          className,
        )}
      >
        {children}
      </BaseDrawer.Popup>
    </BaseDrawer.Portal>
  );
}

export type DrawerBodyProps = React.HTMLAttributes<HTMLDivElement>;
export function DrawerBody({ className, ...props }: DrawerBodyProps) {
  return <div className={cn("min-h-0 flex-1 overflow-y-auto p-5", className)} {...props} />;
}
