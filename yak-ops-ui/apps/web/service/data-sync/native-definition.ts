import HttpUtils from "@/service/http/HttpUtils";
import { YAK_OPS_API_PREFIX } from "@/config/api";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";
import type { TaskDefinitionRecord } from "./types";
import type { PipelineDefinition } from "./native-pipeline";
import { createTaskRequest, updateTaskRequest, publishTaskRequest } from "./native-pipeline";

const url = (suffix: string) =>
  YAK_OPS_API_PREFIX + "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) + "/data-sync/tasks" + suffix;

export interface NativeTaskWrite {
  name: string;
  remark?: string;
  definition: PipelineDefinition;
}

export function createNativeTask(value: NativeTaskWrite, signal?: AbortSignal) {
  return HttpUtils.postData<TaskDefinitionRecord>(
    url(""), createTaskRequest(value.name, value.remark || "", value.definition), { signal },
  );
}

export function updateNativeTask(
  taskCode: string,
  value: NativeTaskWrite,
  expectedRevision: number,
  parametersChanged: boolean,
  signal?: AbortSignal,
) {
  return HttpUtils.putData<TaskDefinitionRecord>(
    url("/" + encodeURIComponent(taskCode)),
    updateTaskRequest(value.name, value.remark || "", value.definition, expectedRevision, parametersChanged),
    { signal },
  );
}

export function publishNativeTask(
  taskCode: string,
  version: number,
  expectedRevision: number,
  signal?: AbortSignal,
) {
  return HttpUtils.postData<TaskDefinitionRecord>(
    url("/" + encodeURIComponent(taskCode) + "/publish"),
    publishTaskRequest(version, expectedRevision),
    { signal },
  );
}
