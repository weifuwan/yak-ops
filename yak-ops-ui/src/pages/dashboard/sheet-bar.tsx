import { useIntl } from '@umijs/max';
import { Dropdown, Input, Modal, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  FilePenLine,
  LayoutDashboard,
  MessageSquareText,
  MoreHorizontal,
  Plus,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

export type DashboardEditorSheet = {
  id: string;
  title: string;
};

type DashboardSheetMeta = {
  note?: string;
  hidden?: boolean;
};

type DashboardSheetMetaMap = Record<string, DashboardSheetMeta>;

const SHEET_META_STORAGE_PREFIX = 'yak.dashboard.sheet-meta';

const readSheetMeta = (dashboardKey: string): DashboardSheetMetaMap => {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(`${SHEET_META_STORAGE_PREFIX}:${dashboardKey}`);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

export function DashboardSheetBar({
  dashboardKey,
  sheets,
  activeSheet,
  activeSheetId,
  canAddChart = true,
  onDashboard,
  onChart,
  onReorder,
  onAddChart,
  onRename,
  onDuplicate,
  onDelete,
}: {
  dashboardKey: string;
  sheets: DashboardEditorSheet[];
  activeSheet: 'dashboard' | 'chart';
  activeSheetId?: string;
  canAddChart?: boolean;
  onDashboard: () => void;
  onChart: (sheetId: string) => void;
  onReorder: (sheetIds: string[]) => void;
  onAddChart?: () => void;
  onRename?: (sheetId: string, title: string) => void;
  onDuplicate?: (sheetId: string) => void;
  onDelete?: (sheetId: string) => void;
}) {
  const intl = useIntl();
  const [draggingId, setDraggingId] = useState<string>();
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const [sheetMeta, setSheetMeta] = useState<DashboardSheetMetaMap>({});
  const [loadedMetaKey, setLoadedMetaKey] = useState<string>();
  const [renamingId, setRenamingId] = useState<string>();
  const [renameDraft, setRenameDraft] = useState('');
  const [noteSheet, setNoteSheet] = useState<DashboardEditorSheet>();
  const [noteDraft, setNoteDraft] = useState('');
  const renameCancelledRef = useRef(false);
  const viewportRef = useRef<HTMLDivElement>(null);
  const dashboardRef = useRef<HTMLButtonElement>(null);
  const tabRefs = useRef(new Map<string, HTMLButtonElement>());

  useEffect(() => {
    setLoadedMetaKey(undefined);
    setSheetMeta(readSheetMeta(dashboardKey));
    setLoadedMetaKey(dashboardKey);
  }, [dashboardKey]);

  useEffect(() => {
    if (loadedMetaKey !== dashboardKey || typeof window === 'undefined') return;
    try {
      window.localStorage.setItem(
        `${SHEET_META_STORAGE_PREFIX}:${dashboardKey}`,
        JSON.stringify(sheetMeta),
      );
    } catch {
      // Sheet notes and visibility are editor conveniences. Storage failure must not block editing.
    }
  }, [dashboardKey, loadedMetaKey, sheetMeta]);

  const visibleSheets = useMemo(
    () => sheets.filter((sheet) => !sheetMeta[sheet.id]?.hidden),
    [sheetMeta, sheets],
  );
  const hiddenSheets = useMemo(
    () => sheets.filter((sheet) => sheetMeta[sheet.id]?.hidden),
    [sheetMeta, sheets],
  );

  const patchSheetMeta = useCallback((sheetId: string, patch: DashboardSheetMeta) => {
    setSheetMeta((current) => ({
      ...current,
      [sheetId]: { ...current[sheetId], ...patch },
    }));
  }, []);

  const updateScrollState = useCallback(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    const maximum = Math.max(0, viewport.scrollWidth - viewport.clientWidth);
    setCanScrollLeft(viewport.scrollLeft > 1);
    setCanScrollRight(viewport.scrollLeft < maximum - 1);
  }, []);

  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return undefined;
    updateScrollState();
    const observer = new ResizeObserver(updateScrollState);
    observer.observe(viewport);
    viewport.addEventListener('scroll', updateScrollState, { passive: true });
    return () => {
      observer.disconnect();
      viewport.removeEventListener('scroll', updateScrollState);
    };
  }, [visibleSheets.length, updateScrollState]);

  useEffect(() => {
    if (activeSheet !== 'chart' || !activeSheetId || sheetMeta[activeSheetId]?.hidden) return;
    tabRefs.current.get(activeSheetId)?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'nearest',
    });
    window.requestAnimationFrame(updateScrollState);
  }, [activeSheet, activeSheetId, sheetMeta, updateScrollState]);

  const moveBefore = (targetId: string) => {
    if (!draggingId || draggingId === targetId) return;
    const ids = sheets.map((sheet) => sheet.id);
    const from = ids.indexOf(draggingId);
    const to = ids.indexOf(targetId);
    if (from < 0 || to < 0) return;
    ids.splice(from, 1);
    ids.splice(to, 0, draggingId);
    onReorder(ids);
  };

  const focusSheet = (id: string) => {
    window.requestAnimationFrame(() => {
      if (id === 'dashboard') dashboardRef.current?.focus();
      else tabRefs.current.get(id)?.focus();
    });
  };

  const handleNavigation = (
    event: React.KeyboardEvent<HTMLButtonElement>,
    currentId: string,
  ) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const ids = ['dashboard', ...visibleSheets.map((sheet) => sheet.id)];
    const currentIndex = Math.max(0, ids.indexOf(currentId));
    let nextIndex = currentIndex;
    if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = ids.length - 1;
    else if (event.key === 'ArrowLeft') nextIndex = Math.max(0, currentIndex - 1);
    else if (event.key === 'ArrowRight') nextIndex = Math.min(ids.length - 1, currentIndex + 1);
    const nextId = ids[nextIndex];
    if (!nextId || nextId === currentId) return;
    if (nextId === 'dashboard') onDashboard();
    else onChart(nextId);
    focusSheet(nextId);
  };

  const scrollSheets = (direction: -1 | 1) => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    viewport.scrollBy({
      left: direction * Math.max(180, Math.round(viewport.clientWidth * 0.65)),
      behavior: 'smooth',
    });
  };

  const openRename = (sheet: DashboardEditorSheet) => {
    if (!onRename) return;
    renameCancelledRef.current = false;
    setRenamingId(sheet.id);
    setRenameDraft(sheet.title);
    onChart(sheet.id);
  };

  const commitRename = (sheet: DashboardEditorSheet) => {
    if (renameCancelledRef.current) {
      renameCancelledRef.current = false;
      setRenamingId(undefined);
      setRenameDraft('');
      return;
    }
    const value = renameDraft.trim();
    if (value && value !== sheet.title) onRename?.(sheet.id, value);
    setRenamingId(undefined);
    setRenameDraft('');
  };

  const cancelRename = () => {
    renameCancelledRef.current = true;
    setRenamingId(undefined);
    setRenameDraft('');
  };

  const openNote = (sheet: DashboardEditorSheet) => {
    setNoteSheet(sheet);
    setNoteDraft(sheetMeta[sheet.id]?.note ?? '');
  };

  const hideSheet = (sheet: DashboardEditorSheet) => {
    patchSheetMeta(sheet.id, { hidden: true });
    if (activeSheet === 'chart' && activeSheetId === sheet.id) onDashboard();
  };

  const restoreSheet = (sheetId: string) => {
    patchSheetMeta(sheetId, { hidden: false });
    onChart(sheetId);
  };

  const confirmDelete = (sheet: DashboardEditorSheet) => {
    Modal.confirm({
      title: intl.formatMessage({ id: 'pages.dashboard.editor.sheet.deleteTitle' }),
      content: intl.formatMessage(
        { id: 'pages.dashboard.editor.sheet.deleteContent' },
        { title: sheet.title },
      ),
      okText: intl.formatMessage({ id: 'pages.dashboard.editor.common.delete' }),
      cancelText: intl.formatMessage({ id: 'pages.dashboard.editor.common.cancel' }),
      okButtonProps: { danger: true },
      onOk: () => {
        if (activeSheet === 'chart' && activeSheetId === sheet.id) onDashboard();
        onDelete?.(sheet.id);
      },
    });
  };

  const menuFor = (sheet: DashboardEditorSheet): MenuProps => ({
    items: [
      {
        key: 'rename',
        icon: <FilePenLine size={14} />,
        label: intl.formatMessage({ id: 'pages.dashboard.editor.common.rename' }),
        disabled: !onRename,
      },
      {
        key: 'duplicate',
        icon: <Copy size={14} />,
        label: intl.formatMessage({ id: 'pages.dashboard.editor.common.duplicate' }),
        disabled: !onDuplicate,
      },
      {
        key: 'note',
        icon: <MessageSquareText size={14} />,
        label: intl.formatMessage({ id: 'pages.dashboard.editor.common.note' }),
      },
      { type: 'divider' },
      {
        key: 'hide',
        icon: <EyeOff size={14} />,
        label: intl.formatMessage({ id: 'pages.dashboard.editor.common.hide' }),
      },
      {
        key: 'delete',
        icon: <Trash2 size={14} />,
        label: intl.formatMessage({ id: 'pages.dashboard.editor.common.delete' }),
        danger: true,
        disabled: !onDelete,
      },
    ],
    onClick: ({ key, domEvent }) => {
      domEvent.stopPropagation();
      if (key === 'rename') openRename(sheet);
      else if (key === 'duplicate') onDuplicate?.(sheet.id);
      else if (key === 'note') openNote(sheet);
      else if (key === 'hide') hideSheet(sheet);
      else if (key === 'delete') confirmDelete(sheet);
    },
  });

  const hiddenMenu: MenuProps = {
    items: hiddenSheets.map((sheet) => ({
      key: sheet.id,
      icon: <Eye size={14} />,
      label: (
        <div className="flex min-w-[150px] items-center justify-between gap-4">
          <span className="max-w-[190px] truncate">{sheet.title}</span>
          <span className="text-[10px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dashboard.editor.common.restore' })}
          </span>
        </div>
      ),
    })),
    onClick: ({ key }) => restoreSheet(String(key)),
  };

  const dashboardActive = activeSheet === 'dashboard';

  return (
    <>
      <div
        className="flex h-8 shrink-0 items-stretch border-t border-[#d8dde4] bg-[#eef1f4] shadow-[0_-1px_0_rgba(16,24,40,.025)]"
        aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.aria' })}
      >
        <div className="flex shrink-0 items-end pl-1">
          <button
            ref={dashboardRef}
            type="button"
            role="tab"
            aria-selected={dashboardActive}
            className={[
              'relative mb-0 flex h-7 min-w-[110px] shrink-0 items-center justify-center gap-1.5 rounded-t-[4px] border px-4 text-[11px] outline-none transition-colors',
              'focus-visible:ring-1 focus-visible:ring-inset focus-visible:ring-[var(--yak-brand-color)]',
              dashboardActive
                ? 'z-10 border-[#d7dce3] border-b-white bg-white font-medium text-[#161823] after:absolute after:inset-x-3 after:bottom-0 after:h-[2px] after:rounded-full after:bg-[var(--yak-brand-color)]'
                : 'border-transparent text-[#667085] hover:bg-[#e4e8ed] hover:text-[#344054]',
            ].join(' ')}
            onClick={onDashboard}
            onKeyDown={(event) => handleNavigation(event, 'dashboard')}
          >
            <LayoutDashboard size={13} className={dashboardActive ? 'text-[#344054]' : 'text-[#7d8591]'} />
            <span>{intl.formatMessage({ id: 'pages.dashboard.editor.sheet.dashboard' })}</span>
          </button>
        </div>

        <div
          ref={viewportRef}
          className="flex min-w-0 flex-1 items-end gap-px overflow-x-auto overflow-y-hidden px-0.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          role="tablist"
        >
          {visibleSheets.length ? visibleSheets.map((sheet) => {
            const active = activeSheet === 'chart' && activeSheetId === sheet.id;
            const note = sheetMeta[sheet.id]?.note?.trim();
            const renaming = renamingId === sheet.id;
            const tooltip = note
              ? intl.formatMessage(
                { id: 'pages.dashboard.editor.sheet.noteTooltip' },
                { title: sheet.title, note },
              )
              : intl.formatMessage(
                { id: 'pages.dashboard.editor.sheet.tooltip' },
                { title: sheet.title },
              );
            return (
              <div
                key={sheet.id}
                draggable={!renaming}
                title={renaming ? undefined : tooltip}
                className={[
                  'group relative mb-0 flex h-7 w-[220px] min-w-[220px] max-w-[220px] shrink-0 items-stretch rounded-t-[4px] border transition-colors',
                  active
                    ? 'z-10 border-[#d7dce3] border-b-white bg-white text-[#161823] after:absolute after:inset-x-3 after:bottom-0 after:h-[2px] after:rounded-full after:bg-[var(--yak-brand-color)]'
                    : 'border-transparent bg-transparent text-[#667085] hover:bg-[#e4e8ed] hover:text-[#344054]',
                  draggingId === sheet.id ? 'opacity-45' : '',
                ].join(' ')}
                onDragStart={(event) => {
                  if (renaming) return;
                  setDraggingId(sheet.id);
                  event.dataTransfer.effectAllowed = 'move';
                  event.dataTransfer.setData('text/plain', sheet.id);
                }}
                onDragEnter={() => moveBefore(sheet.id)}
                onDragOver={(event) => {
                  if (renaming) return;
                  event.preventDefault();
                  event.dataTransfer.dropEffect = 'move';
                }}
                onDrop={(event) => event.preventDefault()}
                onDragEnd={() => setDraggingId(undefined)}
              >
                {renaming ? (
                  <div className="flex min-w-0 flex-1 items-center gap-1.5 bg-transparent py-0 pl-3.5 pr-1 text-left text-[11px]">
                    <BarChart3 size={12} className="shrink-0 text-[#344054]" />
                    <input
                      autoFocus
                      value={renameDraft}
                      maxLength={60}
                      aria-label={intl.formatMessage(
                        { id: 'pages.dashboard.editor.sheet.renameAria' },
                        { title: sheet.title },
                      )}
                      className="h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-[11px] font-medium text-[#161823] outline-none shadow-none [appearance:none] focus:border-0 focus:outline-none focus:ring-0"
                      onFocus={(event) => event.currentTarget.select()}
                      onMouseDown={(event) => event.stopPropagation()}
                      onClick={(event) => event.stopPropagation()}
                      onChange={(event) => setRenameDraft(event.target.value)}
                      onBlur={() => commitRename(sheet)}
                      onKeyDown={(event) => {
                        event.stopPropagation();
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          commitRename(sheet);
                        } else if (event.key === 'Escape') {
                          event.preventDefault();
                          cancelRename();
                        }
                      }}
                    />
                  </div>
                ) : (
                  <button
                    ref={(node) => {
                      if (node) tabRefs.current.set(sheet.id, node);
                      else tabRefs.current.delete(sheet.id);
                    }}
                    type="button"
                    role="tab"
                    aria-selected={active}
                    className="flex min-w-0 flex-1 items-center gap-1.5 bg-transparent py-0 pl-3.5 pr-1 text-left text-[11px] outline-none focus-visible:ring-1 focus-visible:ring-inset focus-visible:ring-[var(--yak-brand-color)]"
                    onClick={() => onChart(sheet.id)}
                    onDoubleClick={() => openRename(sheet)}
                    onKeyDown={(event) => handleNavigation(event, sheet.id)}
                  >
                    <BarChart3
                      size={12}
                      className={active ? 'shrink-0 text-[#344054]' : 'shrink-0 text-[#7d8591]'}
                    />
                    <span className={active ? 'truncate font-medium' : 'truncate'}>{sheet.title}</span>
                    {note ? (
                      <span
                        className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#aeb5bf]"
                        aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.hasNote' })}
                      />
                    ) : null}
                  </button>
                )}

                <Dropdown menu={menuFor(sheet)} trigger={['click']} placement="topLeft">
                  <button
                    type="button"
                    aria-label={intl.formatMessage(
                      { id: 'pages.dashboard.editor.sheet.actionsAria' },
                      { title: sheet.title },
                    )}
                    draggable={false}
                    className={[
                      'mr-1 flex w-6 shrink-0 items-center justify-center self-center rounded-[4px] text-[#818995] transition-all',
                      renaming
                        ? 'opacity-100 hover:bg-[#eef1f4] hover:text-[#344054] focus:outline-none'
                        : 'opacity-0 hover:bg-[#eef1f4] hover:text-[#344054] focus:opacity-100 focus:outline-none group-hover:opacity-100',
                    ].join(' ')}
                    onClick={(event) => event.stopPropagation()}
                    onMouseDown={(event) => event.stopPropagation()}
                  >
                    <MoreHorizontal size={14} />
                  </button>
                </Dropdown>
              </div>
            );
          }) : (
            <div className="flex h-7 items-center px-3 text-[10px] text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.sheet.empty' })}
            </div>
          )}
        </div>

        <div className="flex shrink-0 items-center border-l border-[#d8dde4] bg-[#eef1f4] px-0.5">
          <Tooltip title={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.scrollLeft' })}>
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.scrollLeftAria' })}
              disabled={!canScrollLeft}
              className="flex h-7 w-7 items-center justify-center rounded-[4px] bg-transparent text-[#687180] transition-colors hover:bg-[#e1e5ea] hover:text-[#344054] disabled:cursor-default disabled:text-[#c2c8d0] disabled:hover:bg-transparent"
              onClick={() => scrollSheets(-1)}
            >
              <ChevronLeft size={13} />
            </button>
          </Tooltip>
          <Tooltip title={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.scrollRight' })}>
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.scrollRightAria' })}
              disabled={!canScrollRight}
              className="flex h-7 w-7 items-center justify-center rounded-[4px] bg-transparent text-[#687180] transition-colors hover:bg-[#e1e5ea] hover:text-[#344054] disabled:cursor-default disabled:text-[#c2c8d0] disabled:hover:bg-transparent"
              onClick={() => scrollSheets(1)}
            >
              <ChevronRight size={13} />
            </button>
          </Tooltip>

          {hiddenSheets.length ? (
            <Dropdown menu={hiddenMenu} trigger={['click']} placement="topRight">
              <Tooltip
                title={intl.formatMessage(
                  { id: 'pages.dashboard.editor.sheet.hiddenCount' },
                  { count: hiddenSheets.length },
                )}
              >
                <button
                  type="button"
                  aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.showHidden' })}
                  className="relative flex h-7 w-8 items-center justify-center rounded-[4px] bg-transparent text-[#687180] transition-colors hover:bg-[#e1e5ea] hover:text-[#344054]"
                >
                  <EyeOff size={13} />
                  <span className="absolute right-0.5 top-0.5 min-w-[12px] rounded-full bg-[#8d95a1] px-0.5 text-center text-[8px] leading-[12px] text-white">
                    {hiddenSheets.length}
                  </span>
                </button>
              </Tooltip>
            </Dropdown>
          ) : null}

          <div className="mx-0.5 h-4 w-px bg-[#d4d9e0]" />
          <Tooltip title={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.new' })}>
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.new' })}
              disabled={!onAddChart || !canAddChart}
              className="flex h-7 w-8 items-center justify-center rounded-[4px] bg-transparent text-[#596271] transition-colors hover:bg-[#e1e5ea] hover:text-[#161823] disabled:cursor-not-allowed disabled:text-[#c2c8d0] disabled:hover:bg-transparent"
              onClick={onAddChart}
            >
              <Plus size={14} />
            </button>
          </Tooltip>
        </div>
      </div>

      <Modal
        open={Boolean(noteSheet)}
        title={noteSheet
          ? intl.formatMessage(
            { id: 'pages.dashboard.editor.sheet.noteTitle' },
            { title: noteSheet.title },
          )
          : intl.formatMessage({ id: 'pages.dashboard.editor.sheet.noteFallbackTitle' })}
        okText={intl.formatMessage({ id: 'pages.dashboard.editor.common.save' })}
        cancelText={intl.formatMessage({ id: 'pages.dashboard.editor.common.cancel' })}
        width={440}
        onCancel={() => setNoteSheet(undefined)}
        onOk={() => {
          if (!noteSheet) return;
          patchSheetMeta(noteSheet.id, { note: noteDraft.trim() });
          setNoteSheet(undefined);
        }}
      >
        <Input.TextArea
          autoFocus
          value={noteDraft}
          maxLength={300}
          autoSize={{ minRows: 4, maxRows: 8 }}
          placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.sheet.notePlaceholder' })}
          onChange={(event) => setNoteDraft(event.target.value)}
        />
        <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.editor.sheet.noteHint' })}
        </div>
      </Modal>
    </>
  );
}
