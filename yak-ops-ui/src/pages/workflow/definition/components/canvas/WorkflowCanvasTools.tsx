import { useIntl } from '@umijs/max';
import { Popover, Tooltip } from 'antd';
import {
  CirclePlus,
  Hand,
  History,
  Maximize2,
  MousePointer2,
  Redo2,
  StickyNote,
  Undo2,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNodesInitialized, useReactFlow, useViewport } from 'reactflow';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';
import { resolveWorkflowTaskOption } from './taskOptions';
import WorkflowTaskPicker from './WorkflowTaskPicker';
import type { WorkflowCanvasTaskOption } from './types';
import type { WorkflowCanvasHistoryEntry } from './useWorkflowCanvasHistory';

export type WorkflowCanvasMode = 'pointer' | 'hand';

interface WorkflowCanvasToolsProps<T> {
  mode: WorkflowCanvasMode;
  locked: boolean;
  taskOptions: WorkflowCanvasTaskOption[];
  historyEntries: Array<WorkflowCanvasHistoryEntry<T>>;
  currentHistoryIndex: number;
  canUndo: boolean;
  canRedo: boolean;
  onModeChange: (mode: WorkflowCanvasMode) => void;
  onAddTask: (taskId: string) => void;
  onAddNote: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onJumpToHistory: (index: number) => void;
  onClearHistory: () => void;
}

interface CandidatePointer {
  x: number;
  y: number;
}

const iconButtonClass = (active = false) => [
  'flex h-8 w-8 items-center justify-center rounded-md border-0 transition-colors',
  active
    ? 'bg-[rgba(97,114,243,.10)] text-[#6172f3]'
    : 'bg-transparent text-[#667085] hover:bg-[#f2f4f7] hover:text-[#344054]',
].join(' ');
const disabledButtonClass =
  'disabled:cursor-not-allowed disabled:text-[#c6c9cf] disabled:hover:bg-transparent';

const isEditableTarget = (target: EventTarget | null) => {
  const element = target as HTMLElement | null;
  if (!element) return false;
  const tagName = element.tagName?.toLowerCase();
  return (
    tagName === 'input' ||
    tagName === 'textarea' ||
    tagName === 'select' ||
    element.isContentEditable
  );
};

const WorkflowCanvasTools = <T,>({
  mode,
  locked,
  taskOptions,
  historyEntries,
  currentHistoryIndex,
  canUndo,
  canRedo,
  onModeChange,
  onAddTask,
  onAddNote,
  onUndo,
  onRedo,
  onJumpToHistory,
  onClearHistory,
}: WorkflowCanvasToolsProps<T>) => {
  const intl = useIntl();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [candidateTask, setCandidateTask] = useState<WorkflowCanvasTaskOption>();
  const [candidatePointer, setCandidatePointer] = useState<CandidatePointer>();
  const lastPointerRef = useRef<CandidatePointer>({ x: 0, y: 0 });
  const reactFlow = useReactFlow();
  const { zoom } = useViewport();
  const nodesInitialized = useNodesInitialized();
  const initialFitDoneRef = useRef(false);

  const cancelCandidate = useCallback(() => {
    setCandidateTask(undefined);
    setCandidatePointer(undefined);
  }, []);

  const beginCandidate = useCallback(
    (taskId: string) => {
      if (locked) return;
      void resolveWorkflowTaskOption(taskId, taskOptions).then((task) => {
        if (!task) return;
        setHistoryOpen(false);
        onModeChange('pointer');
        setCandidateTask(task);
        setCandidatePointer(
          lastPointerRef.current.x || lastPointerRef.current.y
            ? { ...lastPointerRef.current }
            : undefined,
        );
      });
    },
    [locked, onModeChange, taskOptions],
  );

  useEffect(() => {
    if (!nodesInitialized || initialFitDoneRef.current) return;
    initialFitDoneRef.current = true;
    const frame = window.requestAnimationFrame(() => {
      void reactFlow.fitView({ padding: 0.18, maxZoom: 0.9, duration: 0 });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [nodesInitialized, reactFlow]);

  useEffect(() => {
    const rememberPointer = (event: PointerEvent) => {
      lastPointerRef.current = { x: event.clientX, y: event.clientY };
    };
    window.addEventListener('pointermove', rememberPointer, { passive: true });
    return () => window.removeEventListener('pointermove', rememberPointer);
  }, []);

  useEffect(() => {
    if (!candidateTask) return;
    const previousCursor = document.body.style.cursor;
    document.body.style.cursor = 'crosshair';
    const handlePointerMove = (event: PointerEvent) => {
      const next = { x: event.clientX, y: event.clientY };
      lastPointerRef.current = next;
      setCandidatePointer(next);
    };
    const handleClick = (event: MouseEvent) => {
      if (event.button !== 0) return;
      const target =
        event.target instanceof Element
          ? event.target
          : document.elementFromPoint(event.clientX, event.clientY);
      const flowRoot = target?.closest('.react-flow');
      if (!flowRoot) return;
      event.preventDefault();
      event.stopPropagation();
      try {
        const dataTransfer = new DataTransfer();
        dataTransfer.setData(
          'application/yak-workflow-task',
          JSON.stringify({
            id: candidateTask.id,
            name: candidateTask.label,
            type: candidateTask.taskType || 'SYNC',
            taskAssetId: candidateTask.taskAssetId,
            taskRevisionId: candidateTask.taskRevisionId,
            taskRevisionNo: candidateTask.taskRevisionNo,
          }),
        );
        dataTransfer.effectAllowed = 'move';
        flowRoot.dispatchEvent(
          new DragEvent('drop', {
            bubbles: true,
            cancelable: true,
            clientX: event.clientX,
            clientY: event.clientY,
            dataTransfer,
          }),
        );
      } catch {
        onAddTask(candidateTask.id);
      }
      cancelCandidate();
    };
    const handleContextMenu = (event: MouseEvent) => {
      event.preventDefault();
      cancelCandidate();
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      cancelCandidate();
    };
    window.addEventListener('pointermove', handlePointerMove, { passive: true });
    window.addEventListener('click', handleClick, true);
    window.addEventListener('contextmenu', handleContextMenu, true);
    window.addEventListener('keydown', handleKeyDown, true);
    return () => {
      document.body.style.cursor = previousCursor;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('click', handleClick, true);
      window.removeEventListener('contextmenu', handleContextMenu, true);
      window.removeEventListener('keydown', handleKeyDown, true);
    };
  }, [candidateTask, cancelCandidate, onAddTask]);

  useEffect(() => {
    if (locked) cancelCandidate();
  }, [cancelCandidate, locked]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (locked || candidateTask || isEditableTarget(event.target)) return;
      const modifier = event.metaKey || event.ctrlKey;
      if (modifier && event.key.toLowerCase() === 'z') {
        event.preventDefault();
        if (event.shiftKey) onRedo();
        else onUndo();
        return;
      }
      if (modifier && event.key.toLowerCase() === 'y') {
        event.preventDefault();
        onRedo();
        return;
      }
      if (!modifier && !event.altKey && !event.shiftKey) {
        if (event.key.toLowerCase() === 'v') onModeChange('pointer');
        if (event.key.toLowerCase() === 'h') onModeChange('hand');
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [candidateTask, locked, onModeChange, onRedo, onUndo]);

  const historyContent = (
    <div className="w-[320px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_36px_rgba(22,24,35,.14)]">
      <div className="flex h-11 items-center justify-between px-3.5">
        <div className="text-[14px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.workflow.editor.canvas.history' })}
        </div>
        <button
          type="button"
          aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.closeHistory' })}
          className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] hover:bg-[#f2f4f7]"
          onClick={() => setHistoryOpen(false)}
        >
          <X size={15} />
        </button>
      </div>
      <div className="max-h-[360px] overflow-y-auto px-2 pb-2">
        {historyEntries.length <= 1 ? (
          <div className="py-10 text-center text-[12px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.workflow.editor.canvas.noHistory' })}
          </div>
        ) : (
          [...historyEntries]
            .map((entry, index) => ({ entry, index }))
            .reverse()
            .map(({ entry, index }) => {
              const diff = index - currentHistoryIndex;
              const stepText =
                diff === 0
                  ? intl.formatMessage({ id: 'pages.workflow.editor.canvas.currentState' })
                  : diff < 0
                    ? intl.formatMessage(
                        { id: 'pages.workflow.editor.canvas.stepsBack' },
                        { count: Math.abs(diff) },
                      )
                    : intl.formatMessage(
                        { id: 'pages.workflow.editor.canvas.stepsForward' },
                        { count: diff },
                      );
              return (
                <button
                  key={entry.id}
                  type="button"
                  className={[
                    'mb-0.5 flex w-full items-center rounded-lg border-0 px-2.5 py-2 text-left transition-colors',
                    diff === 0 ? 'bg-[#f2f4f7]' : 'bg-transparent hover:bg-[#f7f8fa]',
                  ].join(' ')}
                  onClick={() => {
                    onJumpToHistory(index);
                    setHistoryOpen(false);
                  }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[12px] font-medium text-[#475467]">
                      {entry.label}
                    </div>
                    <div className="mt-0.5 text-[10px] text-[#98a2b3]">{stepText}</div>
                  </div>
                </button>
              );
            })
        )}
      </div>
      {historyEntries.length > 1 ? (
        <div className="border-t border-[#f0f1f3] px-2 py-1.5">
          <button
            type="button"
            className="flex w-full rounded-lg border-0 bg-transparent px-2.5 py-2 text-left text-[12px] font-medium text-[#475467] hover:bg-[#f7f8fa]"
            onClick={() => {
              onClearHistory();
              setHistoryOpen(false);
            }}
          >
            {intl.formatMessage({ id: 'pages.workflow.editor.canvas.clearHistory' })}
          </button>
        </div>
      ) : null}
      <div className="border-t border-[#f0f1f3] px-3.5 py-3 text-[10px] leading-[18px] text-[#98a2b3]">
        <div className="mb-1 font-medium text-[#667085]">
          {intl.formatMessage({ id: 'pages.workflow.editor.canvas.tip' })}
        </div>
        {intl.formatMessage({ id: 'pages.workflow.editor.canvas.historyHint' })}
      </div>
    </div>
  );

  return (
    <>
      <style>{`
        .react-flow__controls { display: none !important; }
        .react-flow__minimap { right: 12px !important; bottom: 12px !important; transition: right 180ms ease; }
      `}</style>

      {candidateTask && candidatePointer ? (
        <div
          className="pointer-events-none fixed z-[1000] w-60"
          style={{
            left: candidatePointer.x,
            top: candidatePointer.y,
            transform: `scale(${zoom})`,
            transformOrigin: '0 0',
          }}
        >
          <div className="rounded-[15px] border border-[#d7d9de] bg-white px-3 py-3 shadow-[0_8px_24px_rgba(22,24,35,.14)] opacity-95">
            <div className="flex min-h-9 items-center gap-2.5">
              <WorkflowNodeIcon taskType={candidateTask.taskType} />
              <div className="min-w-0 flex-1 truncate text-[14px] font-semibold leading-5 text-[#161823]">
                {candidateTask.label}
              </div>
            </div>
          </div>
        </div>
      ) : null}

      <div className="pointer-events-none absolute inset-y-0 left-0 z-10 flex w-12 items-center justify-center p-1 pl-2">
        <div className="pointer-events-auto flex flex-col items-center rounded-lg border border-[#e4e7ec] bg-white p-0.5 shadow-[0_4px_14px_rgba(22,24,35,.08)]">
          <WorkflowTaskPicker options={taskOptions} disabled={locked} placement="rightTop" onSelect={beginCandidate}>
            <span>
              <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.addNode' })} placement="right">
                <button
                  type="button"
                  aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.addNode' })}
                  disabled={locked}
                  className={`${iconButtonClass(Boolean(candidateTask))} ${disabledButtonClass}`}
                >
                  <CirclePlus size={16} strokeWidth={1.9} />
                </button>
              </Tooltip>
            </span>
          </WorkflowTaskPicker>

          <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.addNote' })} placement="right">
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.addNote' })}
              disabled={locked}
              className={`${iconButtonClass()} ${disabledButtonClass}`}
              onClick={onAddNote}
            >
              <StickyNote size={16} strokeWidth={1.9} />
            </button>
          </Tooltip>
          <div className="my-1 h-px w-5 bg-[#eceef1]" />
          <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.pointerMode' })} placement="right">
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.pointerModeAria' })}
              disabled={locked}
              className={`${iconButtonClass(mode === 'pointer')} ${disabledButtonClass}`}
              onClick={() => onModeChange('pointer')}
            >
              <MousePointer2 size={16} strokeWidth={1.9} />
            </button>
          </Tooltip>
          <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.handMode' })} placement="right">
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.handModeAria' })}
              disabled={locked}
              className={`${iconButtonClass(mode === 'hand')} ${disabledButtonClass}`}
              onClick={() => onModeChange('hand')}
            >
              <Hand size={16} strokeWidth={1.9} />
            </button>
          </Tooltip>
          <div className="my-1 h-px w-5 bg-[#eceef1]" />
          <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.fitView' })} placement="right">
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.fitView' })}
              className={iconButtonClass()}
              onClick={() =>
                void reactFlow.fitView({ padding: 0.18, maxZoom: 1, duration: 250 })
              }
            >
              <Maximize2 size={15} strokeWidth={1.9} />
            </button>
          </Tooltip>
        </div>
      </div>

      <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10 px-1">
        <div className="flex justify-between px-1 pb-2">
          <div className="pointer-events-auto flex items-center rounded-lg border border-[#e4e7ec] bg-white p-0.5 shadow-[0_4px_14px_rgba(22,24,35,.08)]">
            <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.undoHint' })}>
              <button
                type="button"
                aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.undo' })}
                disabled={locked || !canUndo}
                className={`${iconButtonClass()} ${disabledButtonClass}`}
                onClick={onUndo}
              >
                <Undo2 size={16} strokeWidth={1.9} />
              </button>
            </Tooltip>
            <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.redoHint' })}>
              <button
                type="button"
                aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.redo' })}
                disabled={locked || !canRedo}
                className={`${iconButtonClass()} ${disabledButtonClass}`}
                onClick={onRedo}
              >
                <Redo2 size={16} strokeWidth={1.9} />
              </button>
            </Tooltip>
            <div className="mx-1 h-4 w-px bg-[#e4e7ec]" />
            <Popover
              open={historyOpen}
              onOpenChange={(open) => !locked && setHistoryOpen(open)}
              trigger="click"
              placement="top"
              arrow={false}
              content={historyContent}
              overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
            >
              <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.canvas.history' })}>
                <button
                  type="button"
                  aria-label={intl.formatMessage({ id: 'pages.workflow.editor.canvas.historyButton' })}
                  disabled={locked}
                  className={`${iconButtonClass(historyOpen)} ${disabledButtonClass}`}
                >
                  <History size={16} strokeWidth={1.9} />
                </button>
              </Tooltip>
            </Popover>
          </div>
        </div>
      </div>
    </>
  );
};

export default WorkflowCanvasTools;
