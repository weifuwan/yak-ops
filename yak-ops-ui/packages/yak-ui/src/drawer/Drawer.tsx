import { Drawer as BaseDrawer } from "@base-ui/react/drawer";
import type { HTMLAttributes, ReactNode } from "react";

import { cn } from "../cn";

export type DrawerProps<Payload = unknown> = Omit<
  BaseDrawer.Root.Props<Payload>,
  "swipeDirection"
> & {
  side?: "left" | "right";
};

export function Drawer<Payload = unknown>({ side = "right", ...props }: DrawerProps<Payload>) {
  return <BaseDrawer.Root swipeDirection={side} {...props} />;
}

export const DrawerTrigger = BaseDrawer.Trigger;
export const DrawerClose = BaseDrawer.Close;
export const DrawerTitle = BaseDrawer.Title;
export const DrawerDescription = BaseDrawer.Description;

export type DrawerContentProps = Omit<BaseDrawer.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
  width?: number | string;
};

export function DrawerContent({
  children,
  className,
  width = 620,
  style,
  ...props
}: DrawerContentProps) {
  return (
    <BaseDrawer.Portal>
      <BaseDrawer.Backdrop className="fixed inset-0 z-50 bg-[var(--yak-components-overlay)] transition-opacity data-starting-style:opacity-0 data-ending-style:opacity-0 motion-reduce:transition-none" />
      <BaseDrawer.Viewport className="fixed inset-0 z-50 overflow-hidden outline-none">
        <BaseDrawer.Popup
          {...props}
          style={{ width, ...style }}
          className={cn(
            "fixed inset-y-0 flex max-w-[calc(100vw-1rem)] flex-col overflow-hidden border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-dialog-shadow)] outline-none",
            "transition-transform duration-200 motion-reduce:transition-none",
            "data-[swipe-direction=right]:right-0 data-[swipe-direction=right]:border-l data-starting-style:data-[swipe-direction=right]:translate-x-full data-ending-style:data-[swipe-direction=right]:translate-x-full",
            "data-[swipe-direction=left]:left-0 data-[swipe-direction=left]:border-r data-starting-style:data-[swipe-direction=left]:-translate-x-full data-ending-style:data-[swipe-direction=left]:-translate-x-full",
            className,
          )}
        >
          {children}
        </BaseDrawer.Popup>
      </BaseDrawer.Viewport>
    </BaseDrawer.Portal>
  );
}

export type DrawerBodyProps = HTMLAttributes<HTMLDivElement>;

export function DrawerBody({ className, ...props }: DrawerBodyProps) {
  return <div className={cn("min-h-0 flex-1 overflow-y-auto p-5", className)} {...props} />;
}
