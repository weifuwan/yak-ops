import { notification } from 'antd';
import { openPrettyNotification } from './PrettyNotification';

type PrettyNotificationOptions = Parameters<typeof openPrettyNotification>[0];

const DEFAULT_DEDUPE_MS = 1200;
const recentNotifications = new Map<string, number>();

/**
 * Unified UI notification gateway.
 *
 * Repeated requests often fail at the same time (for example after a session
 * expires). Deduplicating by a stable key avoids flooding the screen while
 * keeping the original backend error visible.
 */
export const notifyOnce = (
  key: string,
  options: PrettyNotificationOptions,
  dedupeMs = DEFAULT_DEDUPE_MS,
): boolean => {
  const now = Date.now();
  const lastShown = recentNotifications.get(key) ?? 0;
  if (now - lastShown < dedupeMs) return false;

  recentNotifications.set(key, now);
  openPrettyNotification({
    ...options,
    key: options.key ?? key,
  });

  globalThis.setTimeout(() => {
    if (recentNotifications.get(key) === now) {
      recentNotifications.delete(key);
    }
  }, dedupeMs);

  return true;
};

export const closeNotification = (key?: string) => {
  notification.destroy(key);
};
