import { Toast as BaseToast } from "@base-ui/react/toast";
import type { ReactNode } from "react";

import { cn } from "../cn";

export type ToastTone = "success" | "error" | "warning" | "info";

type ToastData = {
  meta?: ReactNode;
};

export const toastManager = BaseToast.createToastManager<ToastData>();

export type ToastOptions = {
  description?: ReactNode;
  meta?: ReactNode;
  timeout?: number;
  id?: string;
  action?: {
    label: ReactNode;
    onClick: () => void;
  };
};

const addToast = (type: ToastTone, title: ReactNode, options?: ToastOptions) =>
  toastManager.add({
    id: options?.id,
    title,
    description: options?.description,
    timeout: options?.timeout,
    type,
    data: options?.meta ? { meta: options.meta } : undefined,
    actionProps: options?.action
      ? {
          children: options.action.label,
          onClick: options.action.onClick,
        }
      : undefined,
  });

export const toast = {
  success: (title: ReactNode, options?: ToastOptions) => addToast("success", title, options),
  error: (title: ReactNode, options?: ToastOptions) => addToast("error", title, options),
  warning: (title: ReactNode, options?: ToastOptions) => addToast("warning", title, options),
  info: (title: ReactNode, options?: ToastOptions) => addToast("info", title, options),
  dismiss: (id?: string) => toastManager.close(id),
};

const toneClass: Record<ToastTone, string> = {
  success: "text-[var(--yak-components-toast-success)]",
  error: "text-[var(--yak-components-toast-error)]",
  warning: "text-[var(--yak-components-toast-warning)]",
  info: "text-[var(--yak-components-toast-info)]",
};

function ToastHost() {
  const { toasts } = BaseToast.useToastManager<ToastData>();

  return (
    <BaseToast.Portal>
      <BaseToast.Viewport className="pointer-events-none fixed right-5 top-5 z-[100] w-[360px] max-w-[calc(100vw-2rem)]">
        {toasts.map((item) => {
          const tone = (item.type as ToastTone | undefined) ?? "info";
          return (
            <BaseToast.Root
              key={item.id}
              toast={item}
              swipeDirection={["up", "right"]}
              className={cn(
                "pointer-events-auto mb-2 rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-3 shadow-[var(--yak-components-panel-shadow)] outline-none",
                "transition-[opacity,transform] data-starting-style:-translate-y-2 data-starting-style:opacity-0 data-ending-style:translate-x-8 data-ending-style:opacity-0 motion-reduce:transition-none",
              )}
            >
              <BaseToast.Content className="flex items-start gap-3">
                <span
                  aria-hidden="true"
                  className={cn("mt-1 size-2 shrink-0 rounded-full bg-current", toneClass[tone])}
                />
                <div className="min-w-0 flex-1">
                  <BaseToast.Title className="text-[13px] font-semibold text-[var(--yak-components-panel-text)]" />
                  {item.description ? (
                    <BaseToast.Description className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]" />
                  ) : null}
                  {item.data?.meta ? (
                    <div className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">
                      {item.data.meta}
                    </div>
                  ) : null}
                  {item.actionProps ? (
                    <BaseToast.Action
                      {...item.actionProps}
                      className="mt-2 cursor-pointer rounded-lg bg-[var(--yak-components-button-secondary-bg)] px-2.5 py-1.5 text-xs font-medium text-[var(--yak-components-button-secondary-text)] outline-none hover:bg-[var(--yak-components-button-secondary-bg-hover)]"
                    />
                  ) : null}
                </div>
                <BaseToast.Close
                  aria-label="Close notification"
                  className="flex size-7 cursor-pointer items-center justify-center rounded-lg text-[var(--yak-components-muted-text)] outline-none hover:bg-[var(--yak-components-menu-item-hover)]"
                >
                  ×
                </BaseToast.Close>
              </BaseToast.Content>
            </BaseToast.Root>
          );
        })}
      </BaseToast.Viewport>
    </BaseToast.Portal>
  );
}

export interface ToastProviderProps extends Omit<BaseToast.Provider.Props, "toastManager"> {
  children: ReactNode;
}

export function ToastProvider({ children, ...props }: ToastProviderProps) {
  return (
    <BaseToast.Provider {...props} toastManager={toastManager}>
      {children}
      <ToastHost />
    </BaseToast.Provider>
  );
}
