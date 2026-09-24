import { toast, type ToastOptions, type ToastTone } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

export interface NotifyOnceOptions {
  type?: ToastTone;
  title: string;
  description?: ReactNode;
  meta?: ReactNode;
  btnText?: ReactNode;
  onClick?: () => void;
  duration?: number;
  key?: string;
}

const DEFAULT_DEDUPE_MS = 1200;
const recentNotifications = new Map<string, number>();

export const notifyOnce = (
  key: string,
  options: NotifyOnceOptions,
  dedupeMs = DEFAULT_DEDUPE_MS,
): boolean => {
  const now = Date.now();
  const lastShown = recentNotifications.get(key) ?? 0;
  if (now - lastShown < dedupeMs) return false;

  recentNotifications.set(key, now);

  const toastOptions: ToastOptions = {
    id: options.key ?? key,
    description: options.description,
    meta: options.meta,
    timeout: options.duration === undefined ? undefined : options.duration * 1000,
    action:
      options.btnText && options.onClick
        ? { label: options.btnText, onClick: options.onClick }
        : undefined,
  };

  const type = options.type ?? "info";
  toast[type](options.title, toastOptions);

  globalThis.setTimeout(() => {
    if (recentNotifications.get(key) === now) recentNotifications.delete(key);
  }, dedupeMs);

  return true;
};

export const closeNotification = (key?: string) => {
  toast.dismiss(key);
};
