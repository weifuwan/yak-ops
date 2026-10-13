import { Button, SectionCard } from "@yak-ops/yak-ui";
import { useEffect, useRef, useState } from "react";

import { downloadTaskInstanceLogs, getTaskInstanceLogs } from "@/service/task-execution";

interface Props {
  instanceId: string;
  active: boolean;
}

export function TaskInstanceLogPanel({ instanceId, active }: Props) {
  const [content, setContent] = useState("");
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const bodyRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    let disposed = false;
    let position: string | undefined;
    let loading = false;
    let more = true;

    const load = async () => {
      if (loading || disposed) return;
      loading = true;
      try {
        let pages = 0;
        let hasMore: boolean;
        do {
          const page = await getTaskInstanceLogs(instanceId, position);
          if (disposed) return;
          position = page.nextCursor;
          if (page.content) {
            setContent((previous) => (previous + page.content).slice(-180000));
          }
          hasMore = page.hasMore;
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
      if (active || more) void load();
    }, 2000);
    return () => {
      disposed = true;
      window.clearInterval(refresh);
    };
  }, [instanceId, active, revision]);

  useEffect(() => {
    const element = bodyRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [content]);

  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const file = await downloadTaskInstanceLogs(instanceId);
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

  return (
    <SectionCard title="执行日志">
      <div className="flex justify-end gap-2">
        <Button size="small" onClick={() => setRevision((value) => value + 1)}>
          刷新
        </Button>
        <Button size="small" loading={downloading} onClick={() => void download()}>
          下载
        </Button>
      </div>
      <pre
        ref={bodyRef}
        className="mt-3 max-h-96 min-h-36 overflow-auto rounded-xl bg-[#151b2d] p-4 text-xs leading-5 whitespace-pre-wrap break-words text-[#ecf0f8]"
      >
        {content || (error ? "日志加载失败" : "暂无日志")}
      </pre>
    </SectionCard>
  );
}
