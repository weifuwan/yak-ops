import { Dialog as BaseDialog } from "@base-ui/react/dialog";
import type { ReactNode } from "react";

import { cn } from "../cn";

export const Dialog = BaseDialog.Root;
export const DialogTrigger = BaseDialog.Trigger;
export const DialogClose = BaseDialog.Close;
export const DialogTitle = BaseDialog.Title;
export const DialogDescription = BaseDialog.Description;

export type DialogContentProps = Omit<BaseDialog.Popup.Props, "children" | "className"> & {
  children: ReactNode;
  className?: string;
};

export function DialogContent({ children, className, ...props }: DialogContentProps) {
  return (
    <BaseDialog.Portal>
      <BaseDialog.Backdrop className="fixed inset-0 z-50 bg-[var(--yak-components-overlay)] transition-opacity data-starting-style:opacity-0 data-ending-style:opacity-0 motion-reduce:transition-none" />
      <BaseDialog.Popup
        {...props}
        className={cn(
          "fixed left-1/2 top-1/2 z-50 max-h-[80dvh] w-[480px] max-w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-6 text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-dialog-shadow)] outline-none",
          "transition-[opacity,transform] data-starting-style:scale-95 data-starting-style:opacity-0 data-ending-style:scale-95 data-ending-style:opacity-0 motion-reduce:transition-none",
          className,
        )}
      >
        {children}
      </BaseDialog.Popup>
    </BaseDialog.Portal>
  );
}
