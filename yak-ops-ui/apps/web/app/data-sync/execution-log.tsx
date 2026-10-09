import { Badge, SectionCard, Spinner, type BadgeProps } from "@yak-ops/yak-ui";
import { useCallback, useEffect, useState } from "react";

import {
  listDataSyncExecutionEvents,
  type DataSyncExecutionEventLevel,
  type DataSyncExecutionEventRecord,
  type DataSyncInstanceRecord,
} from "@/service/data-sync";

import { isActiveDataSyncInstance } from "./execution-detail";

const POLL_INTERVAL_MILLIS = 2000;

const levelTone = (level: DataSyncExecutionEventLevel): BadgeProps["tone"] => {
  if (level === "ERROR") return "danger";
  if (level === "WARN") return "warning";
  if (level === "INFO") return "info";
  return "neutral";
};

interface DataSyncExecutionLogPanelProps {
  record?: DataSyncInstanceRecord;
  active: boolean;
  sectionCard?: boolean;
  embedded?: boolean;
}

export function DataSyncExecutionLogPanel({
  record,
  active,
  sectionCard = false,
  embedded = false,
}: DataSyncExecutionLogPanelProps) {
  const executionId = record?.id;
  const live = isActiveDataSyncInstance(record);
  const [events, setEvents] = useState<DataSyncExecutionEventRecord[]>([]);
  const [loading, setLoading] = useState(false);

  const loadEvents = useCallback(
    async (silent = false) => {
      if (!active || !executionId) return;
      if (!silent) setLoading(true);
      try {
        setEvents((await listDataSyncExecutionEvents(executionId)) || []);
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [active, executionId],
  );

  useEffect(() => {
    setEvents([]);
  }, [executionId]);

  useEffect(() => {
    if (!active || !executionId) return;
    void loadEvents();
  }, [active, executionId, record?.status, loadEvents]);

  useEffect(() => {
    if (!active || !executionId || !live) return;
    const timer = window.setInterval(() => void loadEvents(true), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [active, executionId, live, loadEvents]);

  const emptyState = (
    <div className="flex min-h-56 items-center justify-center px-6 text-center text-sm text-[#98a2b3]">
      {record ? "暂无执行日志，历史 Execution 可能没有产品事件记录" : "选择左侧执行记录查看日志"}
    </div>
  );

  if (!record) {
    if (embedded) return emptyState;
    return sectionCard ? (
      <SectionCard title="事件时间线">{emptyState}</SectionCard>
    ) : (
      <div className="rounded-lg border border-[#e6e8eb] bg-white">{emptyState}</div>
    );
  }

  const timeline =
    loading && events.length === 0 ? (
      <div className="flex min-h-56 items-center justify-center">
        <Spinner size="large" label="加载执行日志" />
      </div>
    ) : events.length === 0 ? (
      emptyState
    ) : (
      <div className="max-h-[560px] overflow-y-auto">
        {events.map((event) => (
          <div
            key={event.id}
            className="grid grid-cols-[160px_72px_minmax(0,1fr)] gap-3 border-b border-[#f0f1f3] px-4 py-3 last:border-b-0 max-md:grid-cols-1 max-md:gap-1.5"
          >
            <span className="font-mono text-xs text-[#98a2b3]">{event.createTime || "-"}</span>
            <div>
              <Badge tone={levelTone(event.level)}>{event.level}</Badge>
            </div>
            <div className="min-w-0">
              <div className="break-words text-[13px] leading-5 text-[#344054]">
                {event.message}
              </div>
              <div className="mt-1 font-mono text-[11px] text-[#98a2b3]">{event.eventType}</div>
            </div>
          </div>
        ))}
      </div>
    );

  if (embedded) {
    return (
      <div>
        <div className="mb-3 flex items-center justify-between gap-3 text-xs text-[#98a2b3]">
          <span>{events.length} 条事件</span>
          {live ? <span className="text-[#667085]">运行中 · 2s 自动刷新</span> : null}
        </div>
        {timeline}
      </div>
    );
  }

  if (sectionCard) {
    return (
      <SectionCard title="事件时间线">
        <div className="mb-3 flex items-center justify-between gap-3 text-xs text-[#98a2b3]">
          <span>{events.length} 条事件</span>
          {live ? <span className="text-[#667085]">运行中 · 2s 自动刷新</span> : null}
        </div>
        {timeline}
      </SectionCard>
    );
  }

  return (
    <section className="overflow-hidden rounded-lg border border-[#e6e8eb] bg-white">
      <div className="flex items-center justify-between border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5">
        <div>
          <div className="text-sm font-semibold text-[#344054]">事件时间线</div>
          <div className="mt-0.5 text-xs text-[#98a2b3]">{events.length} 条事件</div>
        </div>
        {live ? <span className="text-xs text-[#667085]">运行中 · 2s 自动刷新</span> : null}
      </div>
      {timeline}
    </section>
  );
}
