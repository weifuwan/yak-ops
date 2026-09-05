import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  markMessageRead,
  notifyMessageCountChanged,
  pageMessages,
  safeMessageActionPath,
  type SecurityMessage,
} from '@/services/security/messages';
import { history, useIntl } from '@umijs/max';
import { Bell, ChevronRight } from 'lucide-react';
import { useEffect, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';

interface NotificationState {
  items: SecurityMessage[];
  unreadTotal: number;
  loading: boolean;
  failed: boolean;
}

const formatMessageDate = (value?: string | number) => {
  if (value === undefined || value === null || value === '') return '--';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return String(value);
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
};

function NotificationRow({
  item,
  onOpen,
}: {
  item: SecurityMessage;
  onOpen: (item: SecurityMessage) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(item)}
      className="group flex w-full items-start gap-2 border-0 bg-transparent py-3 text-left"
    >
      <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-[#ff3657]" />
      <span className="min-w-0 flex-1">
        <strong className="line-clamp-2 block text-[12px] font-semibold leading-5 text-[#353943] transition-colors group-hover:text-[#20232b]">
          {item.title}
        </strong>
        {item.summary ? (
          <span className="mt-0.5 block truncate text-[10px] leading-4 text-[#9a9ea6]">
            {item.summary}
          </span>
        ) : null}
      </span>
      <span className="shrink-0 pt-0.5 text-[10px] leading-5 text-[#a0a4ac]">
        {formatMessageDate(item.createTime)}
      </span>
    </button>
  );
}

export default function NotificationCenter() {
  const intl = useIntl();
  const { projects, currentProject } = useSecurityProject();
  const [state, setState] = useState<NotificationState>({
    items: [],
    unreadTotal: 0,
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;

    if (projects.length > 0 && !currentProject) {
      setState({ items: [], unreadTotal: 0, loading: true, failed: false });
      return () => {
        active = false;
      };
    }

    setState({ items: [], unreadTotal: 0, loading: true, failed: false });

    pageMessages({
      pageNum: 1,
      pageSize: 3,
      status: 'UNREAD',
      projectId: currentProject?.id,
    })
      .then((result) => {
        if (!active) return;
        setState({
          items: result.records || [],
          unreadTotal: result.total || 0,
          loading: false,
          failed: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setState({ items: [], unreadTotal: 0, loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, [currentProject?.id, projects.length]);

  const openNotification = async (item: SecurityMessage) => {
    const actionPath = safeMessageActionPath(item.actionPath) || '/system/messages';

    try {
      await markMessageRead(item.id);
      notifyMessageCountChanged();
    } catch {
      // Reading a notification must never block the user from opening its target.
    } finally {
      history.push(actionPath);
    }
  };

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-4 pt-5">
      <header className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-2">
          <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
            {intl.formatMessage({ id: 'pages.home.notification.title' })}
          </h2>
          {state.unreadTotal > 0 ? (
            <span className="rounded-full bg-[#fff0f2] px-2 py-0.5 text-[10px] font-medium text-[#e33f5c]">
              {state.unreadTotal}
            </span>
          ) : null}
        </div>

        <button
          type="button"
          onClick={() => history.push('/system/messages')}
          className="flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
        >
          {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      </header>

      <div className="mt-3 min-h-[126px]">
        {state.items.length > 0 ? (
          <div className="divide-y divide-[#f0f1f3]">
            {state.items.map((item) => (
              <NotificationRow key={item.id} item={item} onOpen={openNotification} />
            ))}
          </div>
        ) : state.loading || state.failed ? (
          <div className="flex min-h-[126px] items-center justify-center text-[11px] text-[#9da1a8]">
            {intl.formatMessage({
              id: state.loading
                ? 'pages.home.notification.loading'
                : 'pages.home.notification.failed',
            })}
          </div>
        ) : (
          <HomeEmptyState
            icon={Bell}
            title={intl.formatMessage({ id: 'pages.home.notification.empty' })}
            size="small"
            className="min-h-[126px]"
          />
        )}
      </div>
    </section>
  );
}
