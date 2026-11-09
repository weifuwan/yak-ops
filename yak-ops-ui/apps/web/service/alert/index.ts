import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

const CHANNEL_API = YAK_OPS_API_PREFIX + "/alert-plugin-instances";
const GROUP_API = YAK_OPS_API_PREFIX + "/alert-groups";
const PLUGIN_API = YAK_OPS_API_PREFIX + "/ui-plugins";

export type AlertPluginFormType = "input" | "input-number" | "radio" | "select";

export interface AlertPluginOption {
  label: string;
  value: string | number | boolean;
  disabled?: boolean;
}

export interface AlertPluginValidate {
  required?: boolean;
  message?: string;
  type?: string;
  min?: number;
  max?: number;
}

export interface AlertPluginParam {
  field: string;
  name?: string;
  type: AlertPluginFormType;
  title: string;
  value?: unknown;
  props?: {
    placeholder?: string;
    type?: string;
    rows?: number;
    [key: string]: unknown;
  };
  options?: AlertPluginOption[];
  validate?: AlertPluginValidate[];
  hidden?: boolean;
  display?: boolean;
  info?: string;
}

export interface AlertPluginDefinition {
  id: number;
  pluginName: string;
  pluginType: string;
  pluginParams: string;
  createTime?: string;
  updateTime?: string;
}

export interface AlertChannelRecord {
  id: number;
  pluginDefineId: number;
  instanceName: string;
  pluginInstanceParams: string;
  alertPluginName?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AlertGroupRecord {
  id: number;
  groupName: string;
  alertInstanceIds?: string | null;
  description?: string | null;
  createTime?: string;
  updateTime?: string;
  createUserId?: number;
}

export function parseAlertPluginParams(value?: string | null): AlertPluginParam[] {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is AlertPluginParam =>
        Boolean(item) &&
        typeof item === "object" &&
        typeof (item as AlertPluginParam).field === "string" &&
        typeof (item as AlertPluginParam).type === "string",
    );
  } catch {
    return [];
  }
}

export function parseAlertInstanceIds(value?: string | null): number[] {
  if (!value) return [];
  return value
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0);
}

export async function listAlertPluginDefinitions(): Promise<AlertPluginDefinition[]> {
  return HttpUtils.getData<AlertPluginDefinition[]>(
    PLUGIN_API + "/query-by-type?pluginType=ALERT",
  );
}

export async function listAlertChannels(): Promise<AlertChannelRecord[]> {
  return HttpUtils.getData<AlertChannelRecord[]>(CHANNEL_API + "/list");
}

export async function createAlertChannel(
  pluginDefineId: number,
  instanceName: string,
  pluginInstanceParams: string,
): Promise<AlertChannelRecord> {
  return HttpUtils.postData<AlertChannelRecord>(CHANNEL_API, undefined, {
    body: new URLSearchParams({
      pluginDefineId: String(pluginDefineId),
      instanceName,
      pluginInstanceParams,
    }),
  });
}

export async function updateAlertChannel(
  id: number,
  instanceName: string,
  pluginInstanceParams: string,
): Promise<AlertChannelRecord> {
  return HttpUtils.putData<AlertChannelRecord>(
    CHANNEL_API + "/" + encodeURIComponent(id),
    undefined,
    {
      body: new URLSearchParams({ instanceName, pluginInstanceParams }),
    },
  );
}

export async function deleteAlertChannel(id: number): Promise<void> {
  await HttpUtils.deleteData<unknown>(CHANNEL_API + "/" + encodeURIComponent(id));
}

export async function testAlertChannel(
  pluginDefineId: number,
  pluginInstanceParams: string,
): Promise<void> {
  await HttpUtils.postData<unknown>(CHANNEL_API + "/test-send", undefined, {
    body: new URLSearchParams({
      pluginDefineId: String(pluginDefineId),
      pluginInstanceParams,
    }),
  });
}

export async function listAlertGroups(): Promise<AlertGroupRecord[]> {
  return HttpUtils.getData<AlertGroupRecord[]>(GROUP_API + "/list");
}

export async function createAlertGroup(
  groupName: string,
  alertInstanceIds: number[],
  description?: string,
): Promise<AlertGroupRecord> {
  return HttpUtils.postData<AlertGroupRecord>(GROUP_API, undefined, {
    body: new URLSearchParams({
      groupName,
      description: description ?? "",
      alertInstanceIds: alertInstanceIds.join(","),
    }),
  });
}

export async function updateAlertGroup(
  id: number,
  groupName: string,
  alertInstanceIds: number[],
  description?: string,
): Promise<AlertGroupRecord> {
  return HttpUtils.putData<AlertGroupRecord>(
    GROUP_API + "/" + encodeURIComponent(id),
    undefined,
    {
      body: new URLSearchParams({
        groupName,
        description: description ?? "",
        alertInstanceIds: alertInstanceIds.join(","),
      }),
    },
  );
}

export async function deleteAlertGroup(id: number): Promise<void> {
  await HttpUtils.deleteData<unknown>(GROUP_API + "/" + encodeURIComponent(id));
}
