import {
  Dialog,
  DialogContent,
  DialogTitle,
  SectionCard,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@yak-ops/yak-ui";
import { Download, Maximize2, Minimize2, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { downloadDataSyncInstanceLogs } from "@/service/data-sync";

import { TaskInstanceLogLine } from "./task-instance-log-line";
import { useInstanceLogs } from "./use-instance-logs";

import "./task-instance-log.css";

interface Props {
  instanceId: string;
  active: boolean;
  embedded?: boolean;
}

export function TaskInstanceLogPanel({ instanceId, active, embedded = false }: Props) {
  const { content, error, logLines, setRevision } = useInstanceLogs(instanceId, active);
  const [downloading, setDownloading] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const bodyRef = useRef<HTMLPreElement>(null);
  const fullscreenBodyRef = useRef<HTMLPreElement>(null);
  const followRef = useRef(true);
  const fullscreenFollowRef = useRef(true);

  useEffect(() => {
    if (bodyRef.current && followRef.current) {
      bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
    }
    if (fullscreenBodyRef.current && fullscreenFollowRef.current) {
      fullscreenBodyRef.current.scrollTop = fullscreenBodyRef.current.scrollHeight;
    }
  }, [content, fullscreen]);

  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const file = await downloadDataSyncInstanceLogs(instanceId);
      const url = URL.createObjectURL(file);
      const link = document.createElement("a");
      link.href = url;
      link.download = `task-${instanceId}.log`;
      link.click();
      URL.revokeObjectURL(url);
    } finally {
      setDownloading(false);
    }
  };

  const actionClassName =
    "flex h-8 w-8 cursor-pointer items-center justify-center rounded-md border-0 bg-white/10 text-[var(--yak-log-toolbar-text)] transition-colors hover:bg-white/20 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/50 disabled:cursor-not-allowed disabled:opacity-45";

  const tooltipStyle = {
    border: "none",
    backgroundColor: "var(--yak-log-tooltip-bg)",
    color: "var(--yak-log-tooltip-text)",
  };

  const logViewer = (expanded: boolean) => (
    <div
      className={
        expanded
          ? "relative flex h-full min-h-0 flex-col rounded-xl bg-[var(--yak-log-panel-bg)]"
          : "relative rounded-xl bg-[var(--yak-log-panel-bg)]"
      }
    >
      <div className="absolute top-3 right-3 z-10 flex gap-1">
        <Tooltip>
          <TooltipTrigger
            render={
              <button
                type="button"
                className={actionClassName}
                aria-label="刷新日志"
                onClick={() => setRevision((value) => value + 1)}
              >
                <RefreshCw size={14} aria-hidden="true" />
              </button>
            }
          />
          <TooltipContent side="top" style={tooltipStyle}>
            刷新日志
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger
            render={
              <button
                type="button"
                className={actionClassName}
                aria-label={expanded ? "退出全屏" : "全屏查看日志"}
                onClick={() => {
                  if (!expanded) fullscreenFollowRef.current = true;
                  setFullscreen(!expanded);
                }}
              >
                {expanded ? (
                  <Minimize2 size={14} aria-hidden="true" />
                ) : (
                  <Maximize2 size={14} aria-hidden="true" />
                )}
              </button>
            }
          />
          <TooltipContent side="top" style={tooltipStyle}>
            {expanded ? "退出全屏" : "全屏查看"}
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger
            render={
              <button
                type="button"
                className={actionClassName}
                aria-label="下载日志"
                disabled={downloading}
                onClick={() => void download()}
              >
                <Download size={14} aria-hidden="true" />
              </button>
            }
          />
          <TooltipContent side="top" style={tooltipStyle}>
            下载日志
          </TooltipContent>
        </Tooltip>
      </div>
      <pre
        ref={expanded ? fullscreenBodyRef : bodyRef}
        onScroll={(event) => {
          const element = event.currentTarget;
          const atBottom = element.scrollHeight - element.scrollTop - element.clientHeight <= 32;
          (expanded ? fullscreenFollowRef : followRef).current = atBottom;
        }}
        className={
          expanded
            ? "min-h-0 flex-1 overflow-auto rounded-xl p-4 pr-28 text-xs leading-5 yak-log-viewer whitespace-pre-wrap break-words"
            : "h-[clamp(420px,55vh,640px)] overflow-auto rounded-xl p-4 pr-28 text-xs leading-5 yak-log-viewer whitespace-pre-wrap break-words"
        }
      >
        {content
          ? logLines.map((line, index) => <TaskInstanceLogLine key={index} line={line} />)
          : error
            ? "日志加载失败"
            : active ? "等待任务日志..." : "暂无日志"}
      </pre>
    </div>
  );

  return (
    <TooltipProvider>
      {embedded ? logViewer(false) : <SectionCard title="执行日志">{logViewer(false)}</SectionCard>}
      <Dialog open={fullscreen} onOpenChange={setFullscreen}>
        <DialogContent
          className="rounded-xl"
          style={{
            inset: 12,
            width: "calc(100vw - 24px)",
            height: "calc(100dvh - 24px)",
            maxWidth: "none",
            maxHeight: "none",
            translate: "none",
            transform: "none",
            padding: 0,
            border: "none",
            overflow: "hidden",
            backgroundColor: "var(--yak-log-panel-bg)",
          }}
        >
          <DialogTitle className="sr-only">运行日志</DialogTitle>
          {fullscreen ? logViewer(true) : null}
        </DialogContent>
      </Dialog>
    </TooltipProvider>
  );
}
