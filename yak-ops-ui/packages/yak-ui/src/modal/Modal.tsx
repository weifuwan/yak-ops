import { Dialog as BaseDialog } from "@base-ui/react/dialog";
import type { CSSProperties, ReactNode } from "react";

import { cn } from "../cn";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  width?: number | string;
  closeLabel?: string;
  className?: string;
  headerClassName?: string;
  bodyClassName?: string;
  footerClassName?: string;
  style?: CSSProperties;
}

export function Modal({
  open,
  onClose,
  title,
  children,
  footer,
  width = 640,
  closeLabel = "Close",
  className,
  headerClassName,
  bodyClassName,
  footerClassName,
  style,
}: ModalProps) {
  return (
    <BaseDialog.Root
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose();
      }}
    >
      <BaseDialog.Portal>
        <BaseDialog.Backdrop className="fixed inset-0 z-50 bg-[var(--yak-components-overlay)] transition-opacity duration-200 ease-out data-starting-style:opacity-0 data-ending-style:opacity-0 motion-reduce:transition-none" />
        <BaseDialog.Popup
          style={{ width, ...style }}
          className={cn(
            "fixed left-1/2 top-4 z-50 flex max-h-[calc(100dvh-2rem)] max-w-[calc(100vw-2rem)] -translate-x-1/2 flex-col overflow-hidden rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)] shadow-[var(--yak-components-dialog-shadow)] outline-none",
            "transition-[opacity,transform] duration-[140ms] ease-[cubic-bezier(0.22,1,0.36,1)] data-starting-style:translate-y-3 data-starting-style:opacity-0 data-ending-style:-translate-y-3 data-ending-style:opacity-0 data-ending-style:duration-[120ms] data-ending-style:ease-in motion-reduce:transition-none",
            className,
          )}
        >
          <header className={cn("flex shrink-0 items-center gap-4 px-5 py-4", headerClassName)}>
            <BaseDialog.Title className="min-w-0 flex-1 text-sm font-medium leading-5">
              {title}
            </BaseDialog.Title>
            <BaseDialog.Close
              aria-label={closeLabel}
              className="inline-flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-[var(--yak-radius-control-small)] border-0 bg-transparent text-[var(--yak-components-panel-text)] opacity-55 outline-none transition-[background-color,opacity] hover:bg-[var(--yak-components-menu-item-hover)] hover:opacity-100 focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)]"
            >
              <svg
                aria-hidden="true"
                viewBox="0 0 20 20"
                className="size-4"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
              >
                <path d="M6 6l8 8M14 6l-8 8" />
              </svg>
            </BaseDialog.Close>
          </header>

          <div className={cn("min-h-0 flex-1 overflow-y-auto px-5 py-4", bodyClassName)}>
            {children}
          </div>

          {footer != null ? (
            <footer
              className={cn(
                "flex shrink-0 items-center justify-end gap-2 border-t border-[var(--yak-components-panel-border)] px-5 py-3",
                footerClassName,
              )}
            >
              {footer}
            </footer>
          ) : null}
        </BaseDialog.Popup>
      </BaseDialog.Portal>
    </BaseDialog.Root>
  );
}
