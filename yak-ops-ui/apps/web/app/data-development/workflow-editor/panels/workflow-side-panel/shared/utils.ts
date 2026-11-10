import { GLOBAL_PARAMETER_TYPES } from "./constants";
import type {
  GlobalParameter,
  GlobalParameterType,
  WorkflowScheduleForm,
} from "./types";
import type {
  WorkflowScheduleParam,
  WorkflowScheduleRecord,
} from "@/service/workflow/management";

export function parseGlobalParameters(value?: string | null): GlobalParameter[] {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item) => item && typeof item === "object")
      .map((item) => {
        const record = item as Partial<GlobalParameter>;
        return {
          prop: String(record.prop ?? ""),
          direct: record.direct === "OUT" ? "OUT" : "IN",
          type: GLOBAL_PARAMETER_TYPES.includes(record.type as GlobalParameterType)
            ? (record.type as GlobalParameterType)
            : "VARCHAR",
          value: String(record.value ?? ""),
          sensitive: Boolean(record.sensitive),
        };
      });
  } catch {
    return [];
  }
}

export function formatDateTimeInput(value?: string | null): string {
  if (!value) return "";
  const matched = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(value);
  if (matched) return matched[1] + "T" + matched[2];

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    "T" +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
}

export function toApiDateTime(value: string): string {
  return value.replace("T", " ") + (value.length === 16 ? ":00" : "");
}

export function defaultDateTimeInput(): string {
  const date = new Date();
  const pad = (part: number) => String(part).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    "T" +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
}

export function defaultScheduleForm(): WorkflowScheduleForm {
  return {
    startTime: defaultDateTimeInput(),
    endTime: "2099-12-31T23:59",
    timezoneId: "Asia/Shanghai",
    crontab: "",
    missedFirePolicy: "FIRE_ALL_MISSED",
    failureStrategy: "CONTINUE",
    warningType: "NONE",
    warningGroupId: 1,
    workflowInstancePriority: "MEDIUM",
    workerGroup: "default",
    tenantCode: "default",
  };
}

export function scheduleRecordToForm(record: WorkflowScheduleRecord): WorkflowScheduleForm {
  return {
    startTime: formatDateTimeInput(record.startTime),
    endTime: formatDateTimeInput(record.endTime),
    timezoneId: record.timezoneId || "Asia/Shanghai",
    crontab: record.crontab || "",
    missedFirePolicy: record.missedFirePolicy || "FIRE_ALL_MISSED",
    failureStrategy: record.failureStrategy || "END",
    warningType: record.warningType || "NONE",
    warningGroupId: record.warningGroupId || 1,
    workflowInstancePriority: record.workflowInstancePriority || "MEDIUM",
    workerGroup: record.workerGroup || "default",
    tenantCode: record.tenantCode || "default",
  };
}

export function toScheduleParam(form: WorkflowScheduleForm): WorkflowScheduleParam {
  return {
    startTime: toApiDateTime(form.startTime),
    endTime: toApiDateTime(form.endTime),
    timezoneId: form.timezoneId,
    crontab: form.crontab.trim(),
    missedFirePolicy: form.missedFirePolicy,
  };
}

export function formatTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { hour12: false });
}

export function statusLabel(state?: string | null): string {
  if (!state) return "未知";
  return WORKFLOW_STATES[state] ?? TASK_STATES[state] ?? state;
}

export function statusClass(state?: string | null): string {
  if (state === "SUCCESS") return "bg-[#edf8f1] text-[#25834b]";
  if (state === "FAILURE" || state === "KILL") return "bg-[#fff0ef] text-[#cf4a43]";
  if (
    state === "RUNNING_EXECUTION" ||
    state === "SUBMITTED_SUCCESS" ||
    state === "SERIAL_WAIT" ||
    state === "READY_PAUSE" ||
    state === "READY_STOP"
  ) {
    return "bg-[#edf3ff] text-[#3568c8]";
  }
  return "bg-[#f1f3f5] text-[#687282]";
}

