import { useCallback, useRef, useState } from "react";

import { fetchAgentTurn, streamAgentTurnEvents, submitAgentTurn } from "@/service/agent";
import { requireActiveProjectCode } from "@/service/workspace/project-scope";

export type ChatItemKind = "user" | "assistant" | "error" | "tool" | "status";

export type ChatItem = {
  id: string;
  kind: ChatItemKind;
  content: string;
  tool?: string;
  running?: boolean;
  durationMs?: number;
};

let sequence = 0;

const nextId = () => `agent-item-${Date.now()}-${sequence++}`;

export function useAgentChat() {
  const [items, setItems] = useState<ChatItem[]>([]);
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<string>();
  const abortRef = useRef<AbortController | null>(null);

  const append = useCallback((item: Omit<ChatItem, "id">) => {
    setItems((current) => [...current, { ...item, id: nextId() }]);
  }, []);

  const dropStatus = useCallback(() => {
    setItems((current) => current.filter((item) => item.kind !== "status"));
  }, []);

  const updateTool = useCallback((tool: string, patch: Partial<ChatItem>) => {
    setItems((current) => {
      for (let index = current.length - 1; index >= 0; index -= 1) {
        const item = current[index];
        if (item.kind === "tool" && item.tool === tool && item.running) {
          const next = [...current];
          next[index] = { ...item, ...patch };
          return next;
        }
      }
      return current;
    });
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setItems([]);
    setSessionId(undefined);
    setSending(false);
  }, []);

  const send = useCallback(
    async (text: string) => {
      const message = text.trim();
      if (!message || sending) {
        return;
      }

      let projectCode: string;
      try {
        projectCode = requireActiveProjectCode();
      } catch (error) {
        append({
          kind: "error",
          content: error instanceof Error ? error.message : "请先选择工作空间",
        });
        return;
      }

      append({ kind: "user", content: message });
      setSending(true);
      const controller = new AbortController();
      abortRef.current = controller;
      let terminal = false;

      try {
        const submission = await submitAgentTurn({ message, projectCode, sessionId });
        setSessionId(submission.sessionId);
        append({ kind: "status", content: "正在分析…", running: true });

        await streamAgentTurnEvents(
          submission.turnId,
          (event) => {
            const data = event.data;
            if (event.type === "TOOL_STARTED") {
              dropStatus();
              append({
                kind: "tool",
                tool: String(data.tool ?? ""),
                content: String(data.tool ?? ""),
                running: true,
              });
            } else if (event.type === "TOOL_FINISHED") {
              updateTool(String(data.tool ?? ""), {
                running: false,
                durationMs: Number(data.durationMs ?? 0),
              });
            } else if (event.type === "TURN_COMPLETED") {
              terminal = true;
              dropStatus();
              append({ kind: "assistant", content: String(data.answer ?? "（无回答内容）") });
            } else if (event.type === "TURN_FAILED") {
              terminal = true;
              dropStatus();
              append({ kind: "error", content: String(data.errorMessage ?? "对话失败") });
            }
          },
          controller.signal,
        );

        if (!terminal) {
          const result = await fetchAgentTurn(submission.turnId);
          dropStatus();
          if (result.status === "COMPLETED") {
            append({ kind: "assistant", content: result.answer ?? "（无回答内容）" });
          } else if (result.status === "FAILED") {
            append({ kind: "error", content: result.errorMessage ?? "对话失败" });
          } else {
            append({ kind: "error", content: "连接已断开，请稍后重试" });
          }
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          dropStatus();
          append({ kind: "error", content: error instanceof Error ? error.message : "请求失败" });
        }
      } finally {
        setSending(false);
      }
    },
    [append, dropStatus, sending, sessionId, updateTool],
  );

  return { items, sending, sessionId, send, reset };
}
