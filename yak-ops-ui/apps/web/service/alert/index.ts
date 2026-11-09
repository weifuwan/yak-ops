import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

const CHANNEL_API = YAK_OPS_API_PREFIX + "/alert-plugin-instances";
const GROUP_API = YAK_OPS_API_PREFIX + "/alert-groups";
const PLUGIN_API = YAK_OPS_API_PREFIX + "/ui-plugins";

const ALERT_CHANNEL_TYPE_LABELS: Record<string, string> = {
  DingTalk: "钉钉",
  Feishu: "飞书",
  Email: "邮件",
  WeChat: "企业微信",
  Http: "Webhook",
  Script: "脚本",
};

const ALERT_PARAM_LABELS: Record<string, string> = {
  receivers: "收件人",
  receiverCcs: "抄送人",
  mailSender: "发件人",
  mailUser: "用户名",
  mailPasswd: "密码",
  isEnableProxy: "启用代理",
  webhook: "Webhook",
  keyword: "关键词",
  secret: "密钥",
  msgType: "消息类型",
  atMobiles: "@手机号",
  atDingtalkIds: "@用户",
  isAtAll: "@所有人",
  proxy: "代理地址",
  port: "端口",
  user: "用户名",
  password: "密码",
  scriptType: "脚本类型",
  scriptPath: "脚本路径",
  userParams: "脚本参数",
  "mail.smtp.host": "SMTP 地址",
  "mail.smtp.port": "SMTP 端口",
  "mail.smtp.auth": "SMTP 认证",
  "mail.smtp.starttls.enable": "STARTTLS",
  "mail.smtp.ssl.enable": "SSL",
  "mail.smtp.ssl.trust": "SSL 信任地址",
  serverHost: "SMTP 地址",
  serverPort: "SMTP 端口",
  sender: "发件人",
  enableSmtpAuth: "SMTP 认证",
  User: "用户名",
  Password: "密码",
  starttlsEnable: "STARTTLS",
  sslEnable: "SSL",
  smtpSslTrust: "SSL 信任地址",
  WebHook: "Webhook",
  IsEnableProxy: "启用代理",
  Proxy: "代理地址",
  Port: "端口",
  Keyword: "关键词",
  Secret: "密钥",
  MsgType: "消息类型",
  AtMobiles: "@手机号",
  AtDingtalkIds: "@用户",
  IsAtAll: "@所有人",
};

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

export function alertChannelTypeLabel(value?: string | null): string {
  if (!value) return "-";
  return ALERT_CHANNEL_TYPE_LABELS[value] ?? value;
}

export function alertParamLabel(value?: string | null, field?: string): string {
  if (value) {
    const match = /^\$t\(['"](.+?)['"]\)$/.exec(value);
    const key = match?.[1] ?? value;
    if (ALERT_PARAM_LABELS[key]) return ALERT_PARAM_LABELS[key];
    if (match) return key;
    return value;
  }
  return (field && ALERT_PARAM_LABELS[field]) || field || "-";
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
