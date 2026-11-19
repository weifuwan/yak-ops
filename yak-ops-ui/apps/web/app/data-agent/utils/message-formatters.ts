import type { ToolCallStatus } from "../model/types";

export function conversationTitle(content: string) {
  const normalized = content.replace(/\s+/g, " ").trim();
  return normalized.length > 26 ? normalized.slice(0, 26) + "…" : normalized;
}

export function formatConversationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function formatSessionTime(value: number) {
  const date = new Date(value * 1000);
  return Number.isNaN(date.getTime())
    ? new Date().toISOString()
    : date.toISOString();
}

export function formatToolInput(value: string) {
  if (!value) return "";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export function toolStatusLabel(status: ToolCallStatus) {
  switch (status) {
    case "running":
      return "执行中";
    case "completed":
      return "已完成";
    case "awaiting-confirmation":
      return "等待确认";
    case "approved":
      return "已确认";
    case "rejected":
      return "已拒绝";
  }
}

export function agentErrorMessage(error: unknown) {
  if (error instanceof Error) {
    if (error.name === "DataAgentApiError") {
      const status = "status" in error ? error.status : undefined;
      if (status === 401) return "Yak-OPS 登录状态已失效，请重新登录后再试。";
      if (status === 403) {
        return "当前用户没有访问 Agent 配置项目的权限，请检查项目授权和 YAK_OPS_PROJECT_CODE。";
      }
      if (status === 404) return "Agent 会话不存在或已失效，请新建对话。";
    }
    return error.message;
  }
  return "Data Agent 暂时无法响应，请稍后重试。";
}
