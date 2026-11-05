import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";
import {
  createSchedule,
  deleteSchedule,
  getTaskSchedule,
  previewSchedule,
  scheduleErrorMessage,
  setScheduleEnabled,
  updateSchedule,
  type ScheduleRecord,
  type ScheduleTimeRule,
} from "@/service/schedule";
import { EMPTY_SCHEDULE, sameScheduleRule } from "../schedule-presentation";
import { scheduleQueryKeys, useScheduleScope } from "../query-keys";
import type { ScheduleDialogProps } from "./types";

type RuleDraft = { base: ScheduleTimeRule; value: ScheduleTimeRule };
const emptyRule = () => ({ ...EMPTY_SCHEDULE });
const ruleOf = (schedule: ScheduleRecord | null) =>
  schedule ? { cronExpression: schedule.cronExpression, timeZone: schedule.timeZone } : emptyRule();

/** Schedule reads belong to Query; editable rules and unknown-write recovery belong to this session. */
export function useScheduleSession({
  targetId,
  published,
  onClose,
  onChanged,
  historyScheduleId,
}: Omit<ScheduleDialogProps, "open" | "onViewInstance">) {
  const historyOnly = Boolean(historyScheduleId);
  const [tab, setTab] = useState(historyOnly ? "fires" : "config");
  const [draft, setDraft] = useState<RuleDraft>(() => ({
    base: emptyRule(),
    value: emptyRule(),
  }));
  const [ready, setReady] = useState(historyOnly);
  const [pending, setPending] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [error, setError] = useState("");
  const [needsReload, setNeedsReload] = useState(false);
  const [confirmation, setConfirmation] = useState<"delete" | "discard" | "reload">();
  const session = useRef<AbortController>();
  const { userId } = useScheduleScope();
  const queryClient = useQueryClient();
  const key = scheduleQueryKeys.detail(userId, targetId);
  const dirty = !sameScheduleRule(draft.value, draft.base);
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;

  useEffect(() => {
    const controller = new AbortController();
    session.current = controller;
    return () => controller.abort();
  }, []);

  const query = useQuery({
    queryKey: key,
    enabled: Boolean(userId && targetId && !historyOnly && !needsReload),
    queryFn: ({ signal }) => getTaskSchedule(targetId, signal),
    retry: false,
    refetchOnWindowFocus: false,
    refetchIntervalInBackground: false,
    refetchInterval: () => {
      if (!ready || dirtyRef.current || pending || needsReload || reloading) return false;
      return 5000;
    },
  });

  useEffect(() => {
    if (historyOnly || pending || reloading || needsReload) return;
    if (query.isError) {
      setError(scheduleErrorMessage(query.error));
      setNeedsReload(true);
      return;
    }
    if (!query.isSuccess || dirtyRef.current) return;
    const value = ruleOf(query.data);
    setDraft({ base: value, value });
    setReady(true);
    setError("");
  }, [
    query.dataUpdatedAt,
    query.isSuccess,
    query.isError,
    query.error,
    historyOnly,
    pending,
    reloading,
    needsReload,
  ]);

  const schedule = query.data ?? null;
  const form = draft.value;
  const loading = !historyOnly && (reloading || (!ready && query.isPending));
  const blocked = Boolean(schedule && !schedule.enabled && (!published || !schedule.schedulable));

  const notifyChanged = () => {
    void queryClient.invalidateQueries({ queryKey: scheduleQueryKeys.tasks() });
    onChanged();
  };

  const recover = async (failure: unknown, signal: AbortSignal) => {
    if (signal.aborted) return;
    setError(scheduleErrorMessage(failure));
    setNeedsReload(true);
    // A timed-out write may have committed. Observe once; do not retry or discard the draft.
    try {
      const observed = await getTaskSchedule(targetId, signal);
      if (!signal.aborted) queryClient.setQueryData(key, observed);
    } catch {
      // The persisted state is unknown until the user explicitly reloads.
    }
    if (!signal.aborted) notifyChanged();
  };

  const reload = async () => {
    if (pending || reloading) return;
    const signal = session.current?.signal;
    if (!signal || signal.aborted) return;
    setConfirmation(undefined);
    setReloading(true);
    setError("");
    try {
      const result = await query.refetch();
      if (signal.aborted) return;
      if (result.isSuccess) {
        const observed = ruleOf(result.data ?? null);
        setDraft({ base: observed, value: observed });
        setReady(true);
        setNeedsReload(false);
      } else {
        setError(scheduleErrorMessage(result.error));
        setNeedsReload(true);
      }
    } finally {
      if (!signal.aborted) setReloading(false);
    }
  };

  const save = async () => {
    const signal = session.current?.signal;
    if (
      !signal ||
      signal.aborted ||
      pending ||
      needsReload ||
      loading ||
      query.isError ||
      schedule?.enabled ||
      !form.cronExpression.trim() ||
      !dirty
    )
      return;
    setPending(true);
    await queryClient.cancelQueries({ queryKey: key });
    if (signal.aborted) return;
    setError("");
    try {
      try {
        await previewSchedule(form, signal);
      } catch (failure) {
        if (!signal.aborted) setError(scheduleErrorMessage(failure));
        return;
      }
      if (signal.aborted) return;
      const saved = schedule
        ? await updateSchedule(schedule.id, schedule.revision, form, signal)
        : await createSchedule(targetId, form, signal);
      if (signal.aborted) return;
      const rule = ruleOf(saved);
      setDraft({ base: rule, value: rule });
      queryClient.setQueryData(key, saved);
      toast.success("调度已保存，未启用");
      notifyChanged();
    } catch (failure) {
      await recover(failure, signal);
    } finally {
      if (!signal.aborted) setPending(false);
    }
  };

  const toggle = async (enabled: boolean) => {
    const signal = session.current?.signal;
    if (!signal || !schedule || pending || needsReload || dirty || signal.aborted || query.isError)
      return;
    setPending(true);
    await queryClient.cancelQueries({ queryKey: key });
    if (signal.aborted) return;
    setError("");
    try {
      const saved = await setScheduleEnabled(schedule.id, schedule.revision, enabled, signal);
      if (!signal.aborted) {
        queryClient.setQueryData(key, saved);
        toast.success(enabled ? "调度已启用" : "调度已停止");
        notifyChanged();
      }
    } catch (failure) {
      await recover(failure, signal);
    } finally {
      if (!signal.aborted) setPending(false);
    }
  };

  const remove = async () => {
    const signal = session.current?.signal;
    if (!signal || signal.aborted || !schedule || pending || needsReload || query.isError) return;
    setPending(true);
    await queryClient.cancelQueries({ queryKey: key });
    if (signal.aborted) return;
    setConfirmation(undefined);
    setError("");
    try {
      await deleteSchedule(schedule.id, schedule.revision, signal);
      if (!signal.aborted) {
        queryClient.setQueryData(key, null);
        const value = emptyRule();
        setDraft({ base: value, value });
        toast.success("调度已删除");
        notifyChanged();
      }
    } catch (failure) {
      await recover(failure, signal);
    } finally {
      if (!signal.aborted) setPending(false);
    }
  };

  const close = () => {
    if (pending || reloading) return;
    if (dirty && !historyOnly) setConfirmation("discard");
    else onClose();
  };

  return {
    tab,
    setTab,
    schedule,
    form,
    setForm: (value: ScheduleTimeRule) => {
      setDraft((previous) => ({ ...previous, value }));
      setError("");
    },
    dirty,
    loading,
    pending,
    error,
    needsReload,
    historyOnly,
    blocked,
    confirmation,
    setConfirmation,
    save,
    toggle,
    remove,
    reload,
    close,
  };
}
