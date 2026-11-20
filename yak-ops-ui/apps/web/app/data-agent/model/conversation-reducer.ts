import type {
  AgentActivity,
  AgentConversation,
  AgentMessage,
  AgentToolCall,
  AgentUsage,
} from "./types";

export type ConversationState = {
  conversations: AgentConversation[];
  activeConversationId: string | null;
};

export type ConversationAction =
  | { type: "RESET" }
  | { type: "RESTORE_CONVERSATIONS"; conversations: AgentConversation[] }
  | { type: "SELECT_CONVERSATION"; sessionId: string | null }
  | { type: "HYDRATE_CONVERSATION"; conversation: AgentConversation }
  | { type: "REMOVE_CONVERSATION"; sessionId: string }
  | { type: "RENAME_CONVERSATION"; sessionId: string; title: string }
  | {
      type: "MESSAGE_PAIR_ADDED";
      sessionId: string;
      title: string;
      preview?: string;
      updatedAt: string;
      userMessage: AgentMessage;
      assistantMessage: AgentMessage;
    }
  | {
      type: "TEXT_DELTA";
      sessionId: string;
      messageId: string;
      delta: string;
      replyId?: string;
      blockId?: string;
      updatedAt: string;
    }
  | {
      type: "THINKING_STARTED";
      sessionId: string;
      messageId: string;
      replyId?: string;
      blockId: string;
      updatedAt: string;
    }
  | {
      type: "THINKING_COMPLETED";
      sessionId: string;
      messageId: string;
      replyId?: string;
      blockId: string;
      updatedAt: string;
    }
  | {
      type: "TOOL_STARTED";
      sessionId: string;
      messageId: string;
      toolCallId: string;
      toolName: string;
      replyId?: string;
      updatedAt: string;
    }
  | {
      type: "TOOL_INPUT_DELTA";
      sessionId: string;
      messageId: string;
      toolCallId: string;
      delta: string;
      updatedAt: string;
    }
  | {
      type: "TOOL_OUTPUT_DELTA";
      sessionId: string;
      messageId: string;
      toolCallId: string;
      delta: string;
      updatedAt: string;
    }
  | {
      type: "TOOL_COMPLETED";
      sessionId: string;
      messageId: string;
      toolCallId: string;
      state?: string;
      updatedAt: string;
    }
  | {
      type: "PROGRESS_UPDATED";
      sessionId: string;
      messageId: string;
      phase: string;
      title: string;
      status: "running" | "completed" | "error";
      replyId?: string;
      updatedAt: string;
    }
  | {
      type: "USAGE_RECORDED";
      sessionId: string;
      messageId: string;
      usage: AgentUsage;
      updatedAt: string;
    }
  | {
      type: "REPLY_ENDED";
      sessionId: string;
      messageId: string;
      replyId?: string;
      finishedReason: string;
      error?: Record<string, unknown> | null;
      updatedAt: string;
    }
  | {
      type: "CONFIRMATION_REQUIRED";
      sessionId: string;
      messageId: string;
      toolCalls: AgentToolCall[];
      updatedAt: string;
    }
  | {
      type: "CONFIRMATION_RESOLVED";
      sessionId: string;
      messageId: string;
      approved: boolean;
      updatedAt: string;
    }
  | {
      type: "MESSAGE_COMPLETED";
      sessionId: string;
      messageId: string;
      updatedAt: string;
      finishedReason?: string;
    }
  | {
      type: "MESSAGE_FAILED";
      sessionId: string;
      messageId: string;
      error: string;
      updatedAt: string;
    };

export const initialConversationState: ConversationState = {
  conversations: [],
  activeConversationId: null,
};

function timestamp(value: string) {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : Date.now();
}

function updateAssistantMessage(
  state: ConversationState,
  sessionId: string,
  messageId: string,
  updatedAt: string,
  update: (message: AgentMessage) => AgentMessage,
): ConversationState {
  let changed = false;
  const conversations = state.conversations.map((conversation) => {
    if (conversation.id !== sessionId) return conversation;

    let messageChanged = false;
    const messages = conversation.messages.map((message) => {
      if (message.id !== messageId) return message;
      messageChanged = true;
      return update(message);
    });

    if (!messageChanged) return conversation;
    changed = true;
    return { ...conversation, updatedAt, messages };
  });

  return changed ? { ...state, conversations } : state;
}

function upsertActivity(
  activities: AgentActivity[],
  activity: AgentActivity,
): AgentActivity[] {
  const index = activities.findIndex((item) => item.id === activity.id);
  if (index < 0) return [...activities, activity];
  return activities.map((item, itemIndex) =>
    itemIndex === index ? { ...item, ...activity } : item,
  );
}

function updateActivity(
  activities: AgentActivity[],
  id: string,
  update: (activity: AgentActivity) => AgentActivity,
) {
  return activities.map((activity) =>
    activity.id === id ? update(activity) : activity,
  );
}

function toolStatusFromResult(state?: string): AgentToolCall["status"] {
  switch (state) {
    case "error":
      return "error";
    case "denied":
      return "denied";
    case "interrupted":
      return "interrupted";
    case "success":
    default:
      return "completed";
  }
}

function updateToolCall(
  toolCalls: AgentToolCall[],
  toolCallId: string,
  update: (toolCall: AgentToolCall) => AgentToolCall,
) {
  return toolCalls.map((toolCall) =>
    toolCall.id === toolCallId ? update(toolCall) : toolCall,
  );
}

export function conversationReducer(
  state: ConversationState,
  action: ConversationAction,
): ConversationState {
  switch (action.type) {
    case "RESET":
      return initialConversationState;

    case "RESTORE_CONVERSATIONS": {
      const incoming = new Map(action.conversations.map((item) => [item.id, item]));
      const existingIds = new Set(state.conversations.map((item) => item.id));
      const updated = state.conversations.map((item) => {
        const restored = incoming.get(item.id);
        return restored
          ? {
              ...item,
              title: restored.title || item.title,
              preview: restored.preview || item.preview,
              updatedAt: restored.updatedAt || item.updatedAt,
            }
          : item;
      });
      const added = action.conversations.filter((item) => !existingIds.has(item.id));
      if (added.length === 0 && updated.every((item, index) => item === state.conversations[index])) {
        return state;
      }
      return {
        ...state,
        conversations: [...updated, ...added].sort(
          (left, right) =>
            new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
        ),
      };
    }

    case "SELECT_CONVERSATION":
      return { ...state, activeConversationId: action.sessionId };

    case "HYDRATE_CONVERSATION": {
      const exists = state.conversations.some((item) => item.id === action.conversation.id);
      const conversations = exists
        ? state.conversations.map((item) =>
            item.id === action.conversation.id ? action.conversation : item,
          )
        : [action.conversation, ...state.conversations];
      return {
        activeConversationId: action.conversation.id,
        conversations: conversations.sort(
          (left, right) =>
            new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
        ),
      };
    }

    case "REMOVE_CONVERSATION":
      return {
        activeConversationId:
          state.activeConversationId === action.sessionId ? null : state.activeConversationId,
        conversations: state.conversations.filter((item) => item.id !== action.sessionId),
      };

    case "RENAME_CONVERSATION":
      return {
        ...state,
        conversations: state.conversations.map((item) =>
          item.id === action.sessionId ? { ...item, title: action.title } : item,
        ),
      };

    case "MESSAGE_PAIR_ADDED": {
      const existing = state.conversations.find((item) => item.id === action.sessionId);
      if (!existing) {
        return {
          activeConversationId: action.sessionId,
          conversations: [
            {
              id: action.sessionId,
              title: action.title,
              preview: action.preview ?? "",
              updatedAt: action.updatedAt,
              messages: [action.userMessage, action.assistantMessage],
            },
            ...state.conversations,
          ],
        };
      }

      return {
        activeConversationId: action.sessionId,
        conversations: state.conversations.map((conversation) =>
          conversation.id === action.sessionId
            ? {
                ...conversation,
                title:
                  conversation.messages.length === 0 ? action.title : conversation.title,
                preview: action.preview ?? conversation.preview,
                updatedAt: action.updatedAt,
                restored: false,
                messages: [
                  ...conversation.messages,
                  action.userMessage,
                  action.assistantMessage,
                ],
              }
            : conversation,
        ),
      };
    }

    case "TEXT_DELTA":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const activityId =
            "text:" + (action.replyId ?? "reply") + ":" + (action.blockId ?? "default");
          const activities = message.activities ?? [];
          const existing = activities.find((activity) => activity.id === activityId);
          const nextActivity: AgentActivity = {
            id: activityId,
            kind: "text",
            title: "回复内容",
            status: "running",
            replyId: action.replyId,
            blockId: action.blockId,
            content: (existing?.content ?? "") + action.delta,
            startedAt: existing?.startedAt ?? timestamp(action.updatedAt),
          };
          return {
            ...message,
            content: message.content + action.delta,
            status: "streaming",
            activities: upsertActivity(activities, nextActivity),
          };
        },
      );

    case "THINKING_STARTED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const id = "thinking:" + (action.replyId ?? "reply") + ":" + action.blockId;
          return {
            ...message,
            activities: upsertActivity(message.activities ?? [], {
              id,
              kind: "thinking",
              title: "思考过程",
              status: "running",
              replyId: action.replyId,
              blockId: action.blockId,
              startedAt: timestamp(action.updatedAt),
            }),
          };
        },
      );

    case "THINKING_COMPLETED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const id = "thinking:" + (action.replyId ?? "reply") + ":" + action.blockId;
          return {
            ...message,
            activities: updateActivity(message.activities ?? [], id, (activity) => ({
              ...activity,
              status: "completed",
              completedAt: timestamp(action.updatedAt),
            })),
          };
        },
      );

    case "TOOL_STARTED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const calls = message.toolCalls ?? [];
          const exists = calls.some((call) => call.id === action.toolCallId);
          const toolCalls = exists
            ? calls.map((call) =>
                call.id === action.toolCallId
                  ? { ...call, name: action.toolName, status: "running" as const }
                  : call,
              )
            : [
                ...calls,
                {
                  id: action.toolCallId,
                  name: action.toolName,
                  input: "",
                  status: "running" as const,
                },
              ];
          const activityId = "tool:" + action.toolCallId;
          const existing = (message.activities ?? []).find(
            (activity) => activity.id === activityId,
          );
          return {
            ...message,
            toolCalls,
            activities: upsertActivity(message.activities ?? [], {
              id: activityId,
              kind: "tool",
              title: action.toolName,
              status: "running",
              replyId: action.replyId,
              toolCallId: action.toolCallId,
              toolName: action.toolName,
              input: existing?.input ?? "",
              output: existing?.output ?? "",
              startedAt: existing?.startedAt ?? timestamp(action.updatedAt),
            }),
          };
        },
      );

    case "TOOL_INPUT_DELTA":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const activityId = "tool:" + action.toolCallId;
          const activity = (message.activities ?? []).find(
            (item) => item.id === activityId,
          );
          return {
            ...message,
            toolCalls: updateToolCall(message.toolCalls ?? [], action.toolCallId, (call) => ({
              ...call,
              input: call.input + action.delta,
            })),
            activities: upsertActivity(message.activities ?? [], {
              id: activityId,
              kind: "tool",
              title: activity?.title ?? "工具调用",
              status: activity?.status ?? "running",
              toolCallId: action.toolCallId,
              toolName: activity?.toolName,
              input: (activity?.input ?? "") + action.delta,
              output: activity?.output ?? "",
              startedAt: activity?.startedAt ?? timestamp(action.updatedAt),
            }),
          };
        },
      );

    case "TOOL_OUTPUT_DELTA":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const activityId = "tool:" + action.toolCallId;
          const activity = (message.activities ?? []).find(
            (item) => item.id === activityId,
          );
          return {
            ...message,
            activities: upsertActivity(message.activities ?? [], {
              id: activityId,
              kind: "tool",
              title: activity?.title ?? "工具调用",
              status: activity?.status ?? "running",
              toolCallId: action.toolCallId,
              toolName: activity?.toolName,
              input: activity?.input ?? "",
              output: (activity?.output ?? "") + action.delta,
              startedAt: activity?.startedAt ?? timestamp(action.updatedAt),
            }),
          };
        },
      );

    case "TOOL_COMPLETED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const status = toolStatusFromResult(action.state);
          const activityId = "tool:" + action.toolCallId;
          return {
            ...message,
            toolCalls: updateToolCall(message.toolCalls ?? [], action.toolCallId, (call) => ({
              ...call,
              status,
            })),
            activities: updateActivity(message.activities ?? [], activityId, (activity) => ({
              ...activity,
              status,
              completedAt: timestamp(action.updatedAt),
            })),
          };
        },
      );

    case "PROGRESS_UPDATED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const id = "progress:" + action.phase;
          const existing = (message.activities ?? []).find(
            (activity) => activity.id === id,
          );
          return {
            ...message,
            activities: upsertActivity(message.activities ?? [], {
              id,
              kind: "progress",
              title: action.title,
              phase: action.phase,
              status: action.status,
              replyId: action.replyId,
              startedAt: existing?.startedAt ?? timestamp(action.updatedAt),
              ...(action.status === "running"
                ? {}
                : { completedAt: timestamp(action.updatedAt) }),
            }),
          };
        },
      );

    case "USAGE_RECORDED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          usage: [...(message.usage ?? []), action.usage],
        }),
      );

    case "REPLY_ENDED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          replyEnds: [
            ...(message.replyEnds ?? []),
            {
              replyId: action.replyId,
              finishedReason: action.finishedReason,
              error: action.error,
            },
          ],
        }),
      );

    case "CONFIRMATION_REQUIRED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => {
          const existingCalls = message.toolCalls ?? [];
          const toolCalls = action.toolCalls.reduce<AgentToolCall[]>((calls, next) => {
            const index = calls.findIndex((call) => call.id === next.id);
            const pending = { ...next, status: "awaiting-confirmation" as const };
            if (index < 0) return [...calls, pending];
            return calls.map((call, callIndex) =>
              callIndex === index ? { ...call, ...pending } : call,
            );
          }, existingCalls);
          const activities = action.toolCalls.reduce<AgentActivity[]>(
            (items, toolCall) => {
              const id = "tool:" + toolCall.id;
              const existing = items.find((item) => item.id === id);
              return upsertActivity(items, {
                id,
                kind: "tool",
                title: toolCall.name,
                status: "awaiting-confirmation",
                toolCallId: toolCall.id,
                toolName: toolCall.name,
                input: toolCall.input || existing?.input || "",
                output: existing?.output ?? "",
                startedAt: existing?.startedAt ?? timestamp(action.updatedAt),
              });
            },
            message.activities ?? [],
          );
          return {
            ...message,
            status: "awaiting-confirmation",
            toolCalls,
            activities,
          };
        },
      );

    case "CONFIRMATION_RESOLVED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          status: "streaming",
          toolCalls: (message.toolCalls ?? []).map((call) =>
            call.status === "awaiting-confirmation"
              ? {
                  ...call,
                  status: action.approved ? ("approved" as const) : ("rejected" as const),
                }
              : call,
          ),
          activities: (message.activities ?? []).map((activity) =>
            activity.status === "awaiting-confirmation"
              ? {
                  ...activity,
                  status: action.approved ? ("approved" as const) : ("rejected" as const),
                  completedAt: timestamp(action.updatedAt),
                }
              : activity,
          ),
        }),
      );

    case "MESSAGE_COMPLETED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          status: action.finishedReason === "interrupted" ? "interrupted" : "complete",
          toolCalls: (message.toolCalls ?? []).map((call) =>
            call.status === "running" ? { ...call, status: "interrupted" as const } : call,
          ),
          activities: (message.activities ?? []).map((activity) =>
            activity.status === "running"
              ? {
                  ...activity,
                  status: activity.kind === "tool" ? "interrupted" : "completed",
                  completedAt: timestamp(action.updatedAt),
                }
              : activity,
          ),
        }),
      );

    case "MESSAGE_FAILED":
      return updateAssistantMessage(
        state,
        action.sessionId,
        action.messageId,
        action.updatedAt,
        (message) => ({
          ...message,
          status: "error",
          error: action.error,
          toolCalls: (message.toolCalls ?? []).map((call) =>
            call.status === "running" ? { ...call, status: "error" as const } : call,
          ),
          activities: (message.activities ?? []).map((activity) =>
            activity.status === "running"
              ? { ...activity, status: "error", completedAt: timestamp(action.updatedAt) }
              : activity,
          ),
        }),
      );

    default:
      return state;
  }
}
