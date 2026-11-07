import HttpUtils from "@/service/http/HttpUtils";
import { YAK_OPS_API_PREFIX } from "@/config/api";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";
import type { TaskDefinitionRecord } from "./types";
import type { PipelineDefinition } from "./native-pipeline";
import { pipelineParameters } from "./native-pipeline";

const url = (suffix: string) =>
  YAK_OPS_API_PREFIX + "/projects/" +
  encodeURIComponent(requireActiveProjectCode()) + "/data-sync/tasks" + suffix;

export interface NativeTaskWrite {
  name: string;
  remark?: string;
  definition: PipelineDefinition;
}

export function createNativeTask(value: NativeTaskWrite, signal?: AbortSignal) {
  return HttpUtils.postData<TaskDefinitionRecord>(url(""), {
    name: value.name,
    remark: value.remark,
    parameters: pipelineParameters(value.definition),
  }, { signal });
}

export function updateNativeTask(
  taskCode: string,
  value: NativeTaskWrite,
  expectedRevision: number,
  parametersChanged: boolean,
  signal?: AbortSignal,
) {
  return HttpUtils.putData<TaskDefinitionRecord>(url("/" + encodeURIComponent(taskCode)), {
    name: value.name,
    remark: value.remark,
    expectedRevision,
    ...(parametersChanged ? { parameters: pipelineParameters(value.definition) } : {}),
  }, { signal });
}

export function publishNativeTask(
  taskCode: string,
  version: number,
  expectedRevision: number,
  signal?: AbortSignal,
) {
  return HttpUtils.postData<TaskDefinitionRecord>(
    url("/" + encodeURIComponent(taskCode) + "/publish"),
    { version, expectedRevision },
    { signal },
  );
}
