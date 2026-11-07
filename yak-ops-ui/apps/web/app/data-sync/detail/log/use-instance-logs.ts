import { useEffect, useMemo, useRef, useState } from "react";
import { getDataSyncInstanceLogs } from "@/service/data-sync";
import { parseLogContent, trimLogContent, type ParsedLogLine } from "./task-instance-log-parser";

/** Cursor and accumulated lines belong to the selected Instance, never to the viewer DOM. */
export function useInstanceLogs(instanceId: string, active: boolean) {
  const activeRef = useRef(active);
  const finalPollsRemainingRef = useRef(0);
  useEffect(() => {
    if (activeRef.current && !active) finalPollsRemainingRef.current = 2;
    activeRef.current = active;
  }, [active]);
  const [content, setContent] = useState("");
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const parsedCacheRef = useRef<{ text: string; lines: ParsedLogLine[] }>({ text: "", lines: [] });

  // Reuse unchanged parsed lines while new pages arrive; reparse after older text is trimmed.
  const logLines = useMemo(() => {
    const previous = parsedCacheRef.current;
    let lines: ParsedLogLine[];
    if (previous.text && content.startsWith(previous.text)) {
      const lastLine = previous.lines[previous.lines.length - 1]?.raw ?? "";
      lines = [
        ...previous.lines.slice(0, -1),
        ...parseLogContent(lastLine + content.slice(previous.text.length)),
      ];
    } else {
      lines = parseLogContent(content);
    }
    parsedCacheRef.current = { text: content, lines };
    return lines;
  }, [content]);

  useEffect(() => {
    let disposed = false;
    let position = 0;
    let loading = false;
    let more = true;
    let headerRendered = false;

    const load = async () => {
      if (loading || disposed) return;
      loading = true;
      try {
        let pages = 0;
        let hasMore: boolean;
        do {
          const page = await getDataSyncInstanceLogs(instanceId, position);
          if (disposed) return;
          let message = page.message;
          if (position === 0 && message.startsWith("[LOG-PATH]: ")) {
            if (headerRendered) {
              message = message.replace(/^[^\n]*\n/, "");
            } else {
              headerRendered = true;
            }
          }
          if (message) {
            setContent((previous) => trimLogContent(previous + message));
          }
          position += page.lineNum;
          // The log transport can cap response bytes before reaching the requested line limit.
          hasMore = page.lineNum > 0;
          pages += 1;
        } while (hasMore && pages < 5);
        more = hasMore;
        setError(false);
      } catch {
        if (!disposed) setError(true);
      } finally {
        loading = false;
      }
    };

    setContent("");
    setError(false);
    void load();
    const refresh = window.setInterval(() => {
      if (!activeRef.current && !more && finalPollsRemainingRef.current === 0) return;
      // Read a final short tail window: terminal status can arrive before the last log write.
      if (!activeRef.current && !more) finalPollsRemainingRef.current -= 1;
      void load();
    }, 2000);
    return () => {
      disposed = true;
      window.clearInterval(refresh);
    };
  }, [instanceId, revision]);

  return { content, error, logLines, setRevision };
}
