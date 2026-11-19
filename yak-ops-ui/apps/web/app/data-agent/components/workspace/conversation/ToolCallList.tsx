import type { AgentToolCall } from "../../../model/types";
import { ToolCallCard } from "./ToolCallCard";

type ToolCallListProps = {
  toolCalls: AgentToolCall[];
};

export function ToolCallList({ toolCalls }: ToolCallListProps) {
  return (
    <div className="mt-3 flex flex-col gap-2">
      {toolCalls.map((toolCall) => (
        <ToolCallCard key={toolCall.id} toolCall={toolCall} />
      ))}
    </div>
  );
}
