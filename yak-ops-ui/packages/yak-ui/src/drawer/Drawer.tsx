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
  animated?: boolean;
};

export function DrawerContent({
  children,
  className,
  width = 620,
  animated = true,
  style,
  ...props
}: DrawerContentProps) {
  return (
    <BaseDrawer.Portal>
      <BaseDrawer.Backdrop
        className={cn(
          "fixed inset-0 z-50 bg-[var(--yak-components-overlay)]",
          animated &&
            "transition-opacity duration-[280ms] ease-out data-starting-style:opacity-0 data-ending-style:opacity-0 data-ending-style:duration-[260ms] motion-reduce:transition-none",
        )}
      />
      <BaseDrawer.Viewport className="fixed inset-0 z-50 overflow-hidden outline-none">
        <BaseDrawer.Popup
          {...props}
          style={{ width, ...style }}
          className={cn(
            "fixed inset-y-0 flex max-w-[calc(100vw-1rem)] flex-col overflow-hidden border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-dialog-shadow)] outline-none",
            "data-[swipe-direction=right]:right-0 data-[swipe-direction=right]:border-l",
            "data-[swipe-direction=left]:left-0 data-[swipe-direction=left]:border-r",
            "[transform:translate3d(var(--drawer-swipe-movement-x,0px),0,0)]",
            animated &&
              "will-change-transform transition-transform duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] data-swiping:transition-none data-ending-style:duration-[280ms] data-ending-style:ease-[cubic-bezier(0.4,0,0.2,1)] data-starting-style:data-[swipe-direction=right]:[transform:translate3d(100%,0,0)] data-ending-style:data-[swipe-direction=right]:[transform:translate3d(100%,0,0)] data-starting-style:data-[swipe-direction=left]:[transform:translate3d(-100%,0,0)] data-ending-style:data-[swipe-direction=left]:[transform:translate3d(-100%,0,0)] motion-reduce:transition-none",
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
  return (
    <BaseDrawer.Content
      className={cn("min-h-0 flex-1 overflow-y-auto p-5", className)}
      {...props}
    />
  );
}
