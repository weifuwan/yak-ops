import { useRef, useState, type Dispatch, type SetStateAction } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  getTaskSchedule,
  scheduleErrorMessage,
  type ScheduleRecord,
  type ScheduleTimeRule,
} from "@/service/schedule";
import { EMPTY_SCHEDULE } from "@/app/schedule/schedule-presentation";

interface PendingSchedule {
  scheduleDraft?: ScheduleTimeRule;
  scheduleSaveError?: string;
}

/** Existing schedule identity is independent of the currently edited task strategy. */
export function useEditorSchedule(id: string | undefined, pendingSchedule: PendingSchedule | null) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const queryClient = useQueryClient();
  const queryKey = ["data-sync-editor-schedule", currentUser?.id, currentWorkspace?.id, id];
  const query = useQuery({
    queryKey,
    enabled: Boolean(id && currentUser && currentWorkspace),
    queryFn: ({ signal }) => getTaskSchedule(id!, signal),
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const [authored, setAuthored] = useState<ScheduleTimeRule | undefined>(
    pendingSchedule?.scheduleDraft,
  );
  const [writeError, setScheduleError] = useState(pendingSchedule?.scheduleSaveError || "");
  const [needsReload, setScheduleNeedsReload] = useState(
    Boolean(pendingSchedule?.scheduleSaveError),
  );
  const scheduleDraft = useRef(pendingSchedule?.scheduleDraft);
  const schedule = query.data || null;
  const scheduleForm = authored || schedule || { ...EMPTY_SCHEDULE };
  const setSchedule: Dispatch<SetStateAction<ScheduleRecord | null>> = (value) =>
    queryClient.setQueryData<ScheduleRecord | null>(queryKey, (current) =>
      typeof value === "function" ? value(current || null) : value,
    );
  const setScheduleForm: Dispatch<SetStateAction<ScheduleTimeRule>> = (value) =>
    setAuthored((current) =>
      typeof value === "function" ? value(current || schedule || EMPTY_SCHEDULE) : value,
    );
  const reloadSchedule = async () => {
    scheduleDraft.current = scheduleForm;
    const result = await query.refetch();
    if (!result.isError) {
      setScheduleError("");
      setScheduleNeedsReload(false);
    }
  };
  const changeSchedule = (value: ScheduleTimeRule) => {
    setAuthored(value);
    scheduleDraft.current = value;
    setScheduleError("");
  };
  return {
    schedule,
    scheduleForm,
    scheduleLoading: Boolean(id) && query.isPending,
    scheduleError: writeError || (query.error ? scheduleErrorMessage(query.error) : ""),
    scheduleNeedsReload: needsReload || query.isError,
    scheduleDraft,
    setSchedule,
    setScheduleForm,
    setScheduleError,
    setScheduleNeedsReload,
    reloadSchedule,
    changeSchedule,
  };
}
