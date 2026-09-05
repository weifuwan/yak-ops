import { useIntl } from '@umijs/max';
import { Dropdown, Tooltip } from 'antd';
import { Check, MoreHorizontal, X } from 'lucide-react';
import { useEffect, useMemo, useRef } from 'react';

import { getEditorAppearance } from '../../editors/registry';
import {
  getEditorSession,
  useEditorSessionVersion,
} from '../../editors/session/editorSessionStore';
import type {
  DevelopmentId,
  DevelopmentResourceNode,
} from '../../types';

export type EditorTabAction =
  | 'close-current'
  | 'close-others'
  | 'close-left'
  | 'close-right'
  | 'close-all';

interface EditorTabsProps {
  nodeMap: Map<DevelopmentId, DevelopmentResourceNode>;
  openNodeIds: DevelopmentId[];
  activeNodeId?: DevelopmentId;
  dirtyNodeIds?: DevelopmentId[];
  onFocus: (nodeId: DevelopmentId) => void;
  onClose: (nodeId: DevelopmentId) => void;
  onAction: (action: EditorTabAction) => void;
}

const EditorTabs = ({
  nodeMap,
  openNodeIds,
  activeNodeId,
  dirtyNodeIds = [],
  onFocus,
  onClose,
  onAction,
}: EditorTabsProps) => {
  const intl = useIntl();
  const tabRefs = useRef(new Map<DevelopmentId, HTMLDivElement>());
  const sessionVersion = useEditorSessionVersion();
  const dirtySet = useMemo(() => new Set(dirtyNodeIds), [dirtyNodeIds]);

  useEffect(() => {
    if (!activeNodeId) return;
    const frame = window.requestAnimationFrame(() => {
      tabRefs.current.get(activeNodeId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'nearest',
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeNodeId]);

  const isDirty = (nodeId: DevelopmentId) =>
    Boolean(getEditorSession(nodeId)?.dirty || dirtySet.has(nodeId));

  const menuItems = useMemo(
    () => [
      {
        key: 'opened-editors',
        label: intl.formatMessage(
          { id: 'pages.dataDevelopment.tabs.opened' },
          { count: openNodeIds.length },
        ),
        children: openNodeIds.map((nodeId) => {
          const node = nodeMap.get(nodeId);
          const active = nodeId === activeNodeId;
          const appearance = node ? getEditorAppearance(node.type) : undefined;
          const Icon = appearance?.icon;
          return {
            key: `focus:${nodeId}`,
            icon: Icon ? (
              <span className={appearance?.iconClassName}>
                <Icon size={13} strokeWidth={1.8} />
              </span>
            ) : undefined,
            label: (
              <div className="flex min-w-[190px] items-center justify-between gap-3">
                <span className="flex min-w-0 items-center gap-2">
                  <span className="max-w-[200px] truncate">{node?.name || nodeId}</span>
                  {isDirty(nodeId) ? (
                    <span
                      className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#667085]"
                      title={intl.formatMessage({ id: 'pages.dataDevelopment.common.unsaved' })}
                    />
                  ) : null}
                </span>
                {active ? <Check size={13} className="shrink-0 text-[#667085]" /> : null}
              </div>
            ),
          };
        }),
      },
      { type: 'divider' as const },
      {
        key: 'close-current',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.tabs.closeCurrent' }),
      },
      {
        key: 'close-others',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.tabs.closeOthers' }),
        disabled: openNodeIds.length <= 1,
      },
      {
        key: 'close-left',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.tabs.closeLeft' }),
        disabled: !activeNodeId || openNodeIds.indexOf(activeNodeId) <= 0,
      },
      {
        key: 'close-right',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.tabs.closeRight' }),
        disabled:
          !activeNodeId || openNodeIds.indexOf(activeNodeId) >= openNodeIds.length - 1,
      },
      {
        key: 'close-all',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.tabs.closeAll' }),
      },
    ],
    [activeNodeId, dirtySet, intl, nodeMap, openNodeIds, sessionVersion],
  );

  return (
    <div className="flex h-9 shrink-0 bg-[#f7f7f8]">
      <div className="min-w-0 flex-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <div className="flex h-9 min-w-full w-max items-stretch">
          {openNodeIds.map((nodeId) => {
            const node = nodeMap.get(nodeId);
            if (!node) return null;
            const active = nodeId === activeNodeId;
            const appearance = getEditorAppearance(node.type);
            const Icon = appearance.icon;

            return (
              <div
                key={nodeId}
                ref={(element) => {
                  if (element) tabRefs.current.set(nodeId, element);
                  else tabRefs.current.delete(nodeId);
                }}
                onAuxClick={(event) => {
                  if (event.button === 1) onClose(nodeId);
                }}
                className={[
                  'group relative flex h-9 min-w-[120px] max-w-[220px] flex-none items-center border-b border-r border-r-[#e5e7eb] border-t-2 transition-colors',
                  active
                    ? 'z-10 border-b-white border-t-[rgba(254,44,85,1)] bg-white text-[#344054]'
                    : 'border-b-[#e8e9ec] border-t-transparent bg-[#f7f7f8] text-[#667085] hover:bg-[#f0f1f2] hover:text-[#344054]',
                ].join(' ')}
              >
                <button
                  type="button"
                  title={`${appearance.label} · ${node.name}`}
                  aria-current={active ? 'page' : undefined}
                  onClick={() => onFocus(nodeId)}
                  className="flex h-full min-w-0 flex-1 items-center gap-2 bg-transparent pl-3 pr-1 text-left outline-none"
                >
                  <span className={['flex h-5 w-4 shrink-0 items-center justify-center', appearance.iconClassName].join(' ')}>
                    <Icon size={13} strokeWidth={1.8} />
                  </span>
                  <span className={[
                    'min-w-0 flex-1 truncate text-[12px] leading-5',
                    active ? 'font-medium text-[#344054]' : 'font-normal',
                  ].join(' ')}>
                    {node.name}
                  </span>
                  {isDirty(nodeId) ? (
                    <span
                      className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#667085]"
                      title={intl.formatMessage({ id: 'pages.dataDevelopment.common.unsaved' })}
                    />
                  ) : null}
                </button>

                <button
                  type="button"
                  aria-label={intl.formatMessage(
                    { id: 'pages.dataDevelopment.tabs.closeNamed' },
                    { name: node.name },
                  )}
                  title={intl.formatMessage({ id: 'pages.dataDevelopment.tabs.close' })}
                  onClick={() => onClose(nodeId)}
                  className={[
                    'mr-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-[3px] text-[#98a2b3] transition-all',
                    active
                      ? 'opacity-100 hover:bg-[#f2f4f7] hover:text-[#475467]'
                      : 'opacity-0 group-hover:opacity-100 hover:bg-[#e4e7ec] hover:text-[#475467]',
                  ].join(' ')}
                >
                  <X size={13} strokeWidth={1.8} />
                </button>
              </div>
            );
          })}
          <div className="min-w-0 flex-1 border-b border-b-[#e8e9ec]" aria-hidden="true" />
        </div>
      </div>

      <div className="flex h-9 w-10 shrink-0 items-center justify-center border-b border-b-[#e8e9ec] border-l border-[#e5e7eb] bg-[#f7f7f8]">
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: menuItems,
            onClick: ({ key }) => {
              if (key.startsWith('focus:')) {
                onFocus(key.substring('focus:'.length));
                return;
              }
              onAction(key as EditorTabAction);
            },
          }}
        >
          <Tooltip
            title={intl.formatMessage({ id: 'pages.dataDevelopment.tabs.actions' })}
            placement="bottomRight"
          >
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.dataDevelopment.tabs.actions' })}
              className="flex h-7 w-7 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-white hover:text-[#344054]"
            >
              <MoreHorizontal size={17} strokeWidth={1.8} />
            </button>
          </Tooltip>
        </Dropdown>
      </div>
    </div>
  );
};

export default EditorTabs;
