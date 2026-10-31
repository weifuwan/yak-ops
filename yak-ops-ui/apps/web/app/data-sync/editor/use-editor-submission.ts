import {
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type MutableRefObject,
  type SetStateAction,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";
import { clearTaskDraft } from "@/service/data-sync/draft";
import {
  createDataSyncTask,
  updateDataSyncTask,
  publishDataSyncTask,
  getDataSyncTask,
  listTaskPublications,
  isRejectedTaskWrite,
  type DataSyncTaskRecord,
  type DataSyncTaskSavePayload,
  type PublishTaskRequest,
} from "@/service/data-sync";
import { matchesSavedTask } from "@/service/data-sync/versions";
import {
  createSchedule,
  updateSchedule,
  previewSchedule,
  scheduleErrorMessage,
  type ScheduleRecord,
  type ScheduleTimeRule,
} from "@/service/schedule";
import { EMPTY_SCHEDULE, sameScheduleRule } from "@/app/schedule/schedule-presentation";

interface SaveInput {
  draftKey: string;
  scope: { userId: string; workspaceId: string };
  taskDirty: boolean;
  parametersDirty: boolean;
  supportsScheduling: boolean;
  schedule: ScheduleRecord | null;
  scheduleForm: ScheduleTimeRule;
  savedTask: MutableRefObject<DataSyncTaskRecord | undefined>;
  scheduleDraft: MutableRefObject<ScheduleTimeRule | undefined>;
  payload: () => DataSyncTaskSavePayload;
  onPersisted: (task: DataSyncTaskRecord, request?: DataSyncTaskSavePayload) => void;
  onComplete: (task: DataSyncTaskRecord) => void;
  onScheduleFailure: () => void;
  setSchedule: Dispatch<SetStateAction<ScheduleRecord | null>>;
  setScheduleForm: Dispatch<SetStateAction<ScheduleTimeRule>>;
  setScheduleError: Dispatch<SetStateAction<string>>;
  setScheduleNeedsReload: Dispatch<SetStateAction<boolean>>;
  recovery?: EditorWriteRecovery;
}

type PendingWrite =
  | { kind: "save" | "create" | "conflict"; id?: string; request: DataSyncTaskSavePayload }
  | { kind: "publish"; id: string; publication: PublishTaskRequest };

export interface EditorWriteRecovery {
  error: string;
  pendingWrite?: PendingWrite;
  retryPublication?: PublishTaskRequest;
}

/** One ordered submission owns task, schedule, publication and uncertain-write recovery. */
export function useEditorSubmission(input: SaveInput) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(input.recovery?.error || "");
  const [pendingWrite, setPendingWrite] = useState<PendingWrite | undefined>(
    input.recovery?.pendingWrite,
  );
  const [retryPublication, setRetryPublication] = useState<PublishTaskRequest | undefined>(
    input.recovery?.retryPublication,
  );
  const saveSession = useRef<AbortController>();
  const inFlight = useRef(false);
  const queryClient = useQueryClient();
  useEffect(() => {
    const controller = new AbortController();
    saveSession.current = controller;
    return () => controller.abort();
  }, []);
  const invalidate = () => {
    void queryClient.invalidateQueries({
      queryKey: ["data-sync-list", `${input.scope.userId}:${input.scope.workspaceId}`],
    });
    void queryClient.invalidateQueries({
      queryKey: ["data-sync-versions", input.scope.userId, input.scope.workspaceId],
    });
  };
  const persisted = (task: DataSyncTaskRecord, request?: DataSyncTaskSavePayload) => {
    if (!request && input.savedTask.current && !matchesSavedTask(input.payload(), task)) {
      setPendingWrite({ kind: "conflict", id: task.id, request: input.payload() });
      setError("发布结果已确认，但远端任务已有其他修改。请查看并加载远端配置后继续编辑。");
      invalidate();
      return false;
    }
    input.savedTask.current = task;
    input.onPersisted(task, request);
    clearTaskDraft(input.draftKey);
    invalidate();
    return true;
  };
  const save = async (publishAfterSave = false) => {
    const signal = saveSession.current?.signal;
    if (inFlight.current || pendingWrite || !signal || signal.aborted) return;
    if (retryPublication && (!publishAfterSave || input.taskDirty)) return;
    inFlight.current = true;
    setSaving(true);
    setError("");
    let attempted: PendingWrite | undefined;
    try {
      const ruleChanged =
        input.supportsScheduling &&
        !sameScheduleRule(input.scheduleForm, input.schedule || EMPTY_SCHEDULE);
      if (ruleChanged && input.scheduleForm.cronExpression.trim()) {
        try {
          await previewSchedule(input.scheduleForm, signal);
        } catch (failure) {
          if (!signal.aborted) {
            input.setScheduleError(scheduleErrorMessage(failure));
            input.onScheduleFailure();
          }
          return;
        }
      }
      if (signal.aborted) return;
      const request = input.payload();
      let saved = input.savedTask.current;
      if (!saved || input.taskDirty) {
        attempted = { kind: saved ? "save" : "create", id: saved?.id, request };
        saved = saved
          ? await updateDataSyncTask(
              saved.id,
              input.parametersDirty
                ? request
                : { name: request.name, remark: request.remark, debug: request.debug },
              saved.revision,
              signal,
            )
          : await createDataSyncTask(request, signal);
        if (signal.aborted) return;
        attempted = undefined;
        persisted(saved, request);
      }
      if (ruleChanged && input.scheduleForm.cronExpression.trim()) {
        try {
          const schedule = input.schedule
            ? await updateSchedule(
                input.schedule.id,
                input.schedule.revision,
                input.scheduleForm,
                signal,
              )
            : await createSchedule(saved.id, input.scheduleForm, signal);
          if (signal.aborted) return;
          input.setSchedule(schedule);
          input.setScheduleForm(schedule);
          input.scheduleDraft.current = undefined;
          input.setScheduleError("");
        } catch (failure) {
          if (!signal.aborted) {
            input.setScheduleError("任务已保存，调度未确认保存。" + scheduleErrorMessage(failure));
            input.setScheduleNeedsReload(true);
            input.scheduleDraft.current = input.scheduleForm;
            input.onScheduleFailure();
          }
          return;
        }
      }
      if (signal.aborted) return;
      let confirmedPublicationVersion: number | undefined;
      if (publishAfterSave) {
        const publication = retryPublication || {
          version: saved.definitionVersion,
          expectedRevision: saved.revision,
          requestId: crypto.randomUUID(),
        };
        attempted = { kind: "publish", id: saved.id, publication };
        saved = await publishDataSyncTask(saved.id, publication, signal, true);
        if (signal.aborted) return;
        attempted = undefined;
        confirmedPublicationVersion = publication.version;
        setRetryPublication(undefined);
        persisted(saved);
      }
      toast.success(
        confirmedPublicationVersion
          ? `本次发布 V${confirmedPublicationVersion} 已确认${saved.hasUnpublishedChanges ? "，仍有待发布修改" : ""}`
          : "同步任务已保存",
      );
      input.onComplete(saved);
    } catch (failure) {
      if (signal.aborted) return;
      const reason = failure instanceof Error ? failure.message : "操作失败";
      if (attempted && !isRejectedTaskWrite(failure)) {
        setPendingWrite(attempted);
        setError(
          attempted.kind === "create"
            ? "创建结果未确认，请返回任务列表核对后再操作。"
            : "提交结果未确认，请重新加载状态核对后再操作。",
        );
      } else {
        setRetryPublication(undefined);
        setError(attempted?.kind === "publish" ? `任务已保存，发布失败：${reason}` : reason);
      }
    } finally {
      inFlight.current = false;
      if (!signal.aborted) setSaving(false);
    }
  };

  const reconcile = async () => {
    const signal = saveSession.current?.signal;
    if (
      !pendingWrite ||
      pendingWrite.kind === "create" ||
      !pendingWrite.id ||
      inFlight.current ||
      !signal ||
      signal.aborted
    )
      return;
    inFlight.current = true;
    setSaving(true);
    try {
      const task = await getDataSyncTask(pendingWrite.id, signal);
      if (signal.aborted) return;
      if (pendingWrite.kind === "save" || pendingWrite.kind === "conflict") {
        if (!matchesSavedTask(pendingWrite.request, task)) {
          setError("远端配置与本页不一致。请查看远端配置后再继续，当前输入已保留。");
          return;
        }
        persisted(task, pendingWrite.request);
        setPendingWrite(undefined);
        setError("");
        toast.success("已确认任务保存成功");
      } else if (pendingWrite.kind === "publish") {
        const publication = pendingWrite.publication;
        const history = await listTaskPublications(task.id, 1, 20, signal);
        if (signal.aborted) return;
        if (history.bizData.some((record) => record.requestId === publication.requestId)) {
          const adopted = persisted(task);
          if (adopted) setPendingWrite(undefined);
          setRetryPublication(undefined);
          if (adopted) setError("");
          toast.success(
            `本次发布 V${publication.version} 已确认${task.hasUnpublishedChanges ? "，仍有待发布修改" : ""}`,
          );
        } else {
          setRetryPublication(publication);
          setPendingWrite(undefined);
          setError("未查到本次发布记录，可重试同一次发布；本页参数已保留。");
        }
      }
    } catch (failure) {
      if (!signal.aborted)
        setError(failure instanceof Error ? failure.message : "状态核对失败，请重试");
    } finally {
      inFlight.current = false;
      if (!signal.aborted) setSaving(false);
    }
  };
  return {
    save,
    saving,
    error,
    pendingWrite,
    retryPublication,
    reconcile,
    clearRecovery: () => {
      setPendingWrite(undefined);
      setRetryPublication(undefined);
      setError("");
    },
  };
}
