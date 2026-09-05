import { useAccess, useIntl } from '@umijs/max';
import {
  GitBranch,
  LoaderCircle,
  RotateCcw,
  Square,
  Table2,
  X,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useState } from 'react';

import SqlLineagePreviewPanel from '../../editors/sql/lineage/SqlLineagePreviewPanel';
import type { DevelopmentEditorDefinition } from '../../editors/types';
import type {
  DevelopmentDirectory,
  DevelopmentNode,
  DevelopmentSqlLineagePreview,
  DevelopmentTaskRunResult,
} from '../../types';

export type WorkbenchBottomPanelView = 'result' | 'lineage';

interface RunResultPanelProps {
  open: boolean;
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
  result?: DevelopmentTaskRunResult;
  view: WorkbenchBottomPanelView;
  onViewChange: (view: WorkbenchBottomPanelView) => void;
  lineagePreview?: DevelopmentSqlLineagePreview;
  lineageLoading?: boolean;
  onRefreshLineage?: () => void;
  onCancel?: () => void;
  onRetry?: () => void;
  actionLoading?: boolean;
  onClose: () => void;
}

const DEFAULT_HEIGHT = 280;
const MIN_HEIGHT = 160;
const MAX_HEIGHT = 520;
const HEIGHT_STORAGE_KEY = 'yak-data-development.bottom-panel-height';

const clampHeight = (value: number) =>
  Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, value));

const initialHeight = () => {
  if (typeof window === 'undefined') return DEFAULT_HEIGHT;
  const stored = Number(window.localStorage.getItem(HEIGHT_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampHeight(stored)
    : DEFAULT_HEIGHT;
};

const tabClassName = (active: boolean) => [
  'relative flex h-full items-center gap-1.5 px-2 text-[12px] transition-colors',
  active
    ? 'font-medium text-[#344054] after:absolute after:inset-x-1 after:bottom-0 after:h-[2px] after:bg-[rgba(254,44,85,.9)]'
    : 'text-[#8a8f99] hover:text-[#475467]',
].join(' ');

const actionClassName =
  'inline-flex h-7 items-center gap-1 rounded-[3px] px-2 text-[11px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054] disabled:cursor-not-allowed disabled:opacity-45';

const RunResultPanel = ({
  open,
  node,
  directory,
  definition,
  result,
  view,
  onViewChange,
  lineagePreview,
  lineageLoading = false,
  onRefreshLineage,
  onCancel,
  onRetry,
  actionLoading = false,
  onClose,
}: RunResultPanelProps) => {
  const access = useAccess();
  const intl = useIntl();
  const canExecute = access.hasPermission('data-development:execute');
  const [height, setHeight] = useState(initialHeight);
  const [resizing, setResizing] = useState(false);
  const lineageAvailable = node.type === 'SQL' && Boolean(onRefreshLineage);
  const actualView = view === 'lineage' && lineageAvailable ? 'lineage' : 'result';

  const statusText = (value?: DevelopmentTaskRunResult) => {
    if (!value) return undefined;
    if (value.status === 'PENDING') {
      return intl.formatMessage({ id: 'pages.dataDevelopment.result.pending' });
    }
    if (value.status === 'RUNNING') {
      return intl.formatMessage({ id: 'pages.dataDevelopment.result.running' });
    }
    if (value.status === 'SUCCESS') {
      return intl.formatMessage(
        { id: 'pages.dataDevelopment.result.success' },
        { duration: value.durationMs },
      );
    }
    if (value.status === 'CANCELLED') {
      return intl.formatMessage({ id: 'pages.dataDevelopment.result.cancelled' });
    }
    if (value.status === 'TIMEOUT') {
      return intl.formatMessage(
        { id: 'pages.dataDevelopment.result.timeout' },
        { duration: value.durationMs },
      );
    }
    if (value.status === 'FAILED') {
      return intl.formatMessage(
        { id: 'pages.dataDevelopment.result.failed' },
        { duration: value.durationMs },
      );
    }
    return value.status;
  };

  const handleResizeStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!open) return;
    event.preventDefault();
    const startY = event.clientY;
    const startHeight = height;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;
    setResizing(true);
    document.body.style.cursor = 'row-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setHeight(clampHeight(startHeight + startY - moveEvent.clientY));
    };
    const finish = (upEvent: PointerEvent) => {
      const nextHeight = clampHeight(startHeight + startY - upEvent.clientY);
      setHeight(nextHeight);
      setResizing(false);
      window.localStorage.setItem(HEIGHT_STORAGE_KEY, String(nextHeight));
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', resize);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };
    window.addEventListener('pointermove', resize);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  };

  const Result = definition.RunResult;
  const executionLabel = result?.executionId ? `Execution #${result.executionId}` : undefined;
  const renderedResult = result?.status === 'PENDING'
    ? { ...result, status: 'RUNNING' as const }
    : result;

  return (
    <div
      className={[
        'relative shrink-0 bg-white',
        resizing ? 'transition-none' : 'transition-[height] duration-200 ease-out',
      ].join(' ')}
      style={{ height: open ? height : 0 }}
    >
      {open ? (
        <>
          <div
            role="separator"
            aria-label={intl.formatMessage({ id: 'pages.dataDevelopment.result.resize' })}
            aria-orientation="horizontal"
            onPointerDown={handleResizeStart}
            className="group absolute inset-x-0 top-0 z-40 h-3 -translate-y-1/2 cursor-row-resize touch-none"
          >
            <div className="pointer-events-none absolute inset-x-0 top-1/2 h-px -translate-y-1/2 bg-[#e5e7eb] transition-[height,background-color] duration-150 group-hover:h-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:h-[2px] group-active:bg-[rgba(254,44,85,1)]" />
          </div>

          <div className="flex h-full flex-col overflow-hidden bg-white">
            <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-2">
              <div className="flex h-full min-w-0 items-center gap-1">
                <button
                  type="button"
                  className={tabClassName(actualView === 'result')}
                  onClick={() => onViewChange('result')}
                >
                  <Table2 size={13} strokeWidth={1.8} />
                  {intl.formatMessage({ id: 'pages.dataDevelopment.result.result' })}
                </button>
                {lineageAvailable ? (
                  <button
                    type="button"
                    className={tabClassName(actualView === 'lineage')}
                    onClick={() => onViewChange('lineage')}
                  >
                    <GitBranch size={13} strokeWidth={1.8} />
                    {intl.formatMessage({ id: 'pages.dataDevelopment.result.lineage' })}
                  </button>
                ) : null}
                <span className="mx-1 h-4 w-px bg-[#e5e7eb]" />
                <span className="max-w-[240px] truncate text-[11px] text-[#98a2b3]">
                  {intl.formatMessage(
                    { id: 'pages.dataDevelopment.result.currentNode' },
                    { name: node.name },
                  )}
                </span>
                {actualView === 'result' && executionLabel ? (
                  <span className="shrink-0 font-mono text-[10px] text-[#98a2b3]">
                    {executionLabel}
                  </span>
                ) : null}
                {actualView === 'result'
                  && (result?.status === 'RUNNING' || result?.status === 'PENDING') ? (
                  <span className="inline-flex shrink-0 items-center gap-1 text-[11px] text-[#667085]">
                    <LoaderCircle size={12} className="animate-spin" />
                    {statusText(result)}
                  </span>
                ) : actualView === 'result' && statusText(result) ? (
                  <span className="shrink-0 text-[11px] text-[#667085]">
                    {statusText(result)}
                  </span>
                ) : actualView === 'lineage' ? (
                  <span className="shrink-0 text-[11px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.dataDevelopment.result.sqlLineage' })}
                  </span>
                ) : null}
              </div>
              <div className="flex shrink-0 items-center gap-1">
                {canExecute && actualView === 'result' && result?.executionId && onCancel ? (
                  <button type="button" disabled={actionLoading} className={actionClassName} onClick={onCancel}>
                    {actionLoading ? <LoaderCircle size={12} className="animate-spin" /> : <Square size={11} strokeWidth={1.8} />}
                    {intl.formatMessage({ id: 'pages.dataDevelopment.result.stop' })}
                  </button>
                ) : null}
                {canExecute && actualView === 'result' && onRetry ? (
                  <button type="button" disabled={actionLoading} className={actionClassName} onClick={onRetry}>
                    {actionLoading ? <LoaderCircle size={12} className="animate-spin" /> : <RotateCcw size={12} strokeWidth={1.8} />}
                    {intl.formatMessage({ id: 'pages.dataDevelopment.result.retry' })}
                  </button>
                ) : null}
                <button
                  type="button"
                  title={intl.formatMessage({ id: 'pages.dataDevelopment.tabs.close' })}
                  aria-label={intl.formatMessage({ id: 'pages.dataDevelopment.result.close' })}
                  onClick={onClose}
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
                >
                  <X size={14} strokeWidth={1.8} />
                </button>
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-hidden bg-white">
              {actualView === 'lineage' && onRefreshLineage ? (
                <SqlLineagePreviewPanel
                  nodeId={node.id}
                  preview={lineagePreview}
                  loading={lineageLoading}
                  onRefresh={onRefreshLineage}
                />
              ) : Result ? (
                <Result node={node} directory={directory} result={renderedResult} />
              ) : (
                <div className="flex h-full items-center justify-center text-center">
                  <div>
                    <div className="text-[13px] font-medium text-[#475467]">
                      {intl.formatMessage({ id: 'pages.dataDevelopment.result.area' })}
                    </div>
                    <div className="mt-1 text-[11px] text-[#98a2b3]">
                      {intl.formatMessage({ id: 'pages.dataDevelopment.result.unsupported' })}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
};

export default RunResultPanel;
