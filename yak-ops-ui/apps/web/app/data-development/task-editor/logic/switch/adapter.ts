import type { WorkflowCanvasEdge } from "../../../workflow-editor/types";

import type { SwitchCase, SwitchTaskParams } from "./types";

type JsonRecord = Record<string, unknown>;

function asRecord(value: unknown): JsonRecord {
  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
        ? parsed as JsonRecord
        : {};
    } catch {
      return {};
    }
  }
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as JsonRecord
    : {};
}

export function normalizeSwitchTaskParams(value: unknown): SwitchTaskParams {
  const params = asRecord(value);
  const result = asRecord(params.switchResult);
  const cases = Array.isArray(result.dependTaskList) ? result.dependTaskList : [];

  return {
    ...params,
    localParams: Array.isArray(params.localParams) ? params.localParams : [],
    switchResult: {
      ...result,
      dependTaskList: cases.map((item) => {
        const current = asRecord(item);
        return {
          condition: String(current.condition ?? ""),
          nextNode: current.nextNode === null || current.nextNode === undefined
            ? null
            : Number(current.nextNode),
        } as SwitchCase;
      }),
      nextNode: result.nextNode === null || result.nextNode === undefined
        ? null
        : Number(result.nextNode),
    },
  };
}

export function validateSwitchTaskParams(value: unknown): string | null {
  const normalized = normalizeSwitchTaskParams(value);
  const cases = normalized.switchResult.dependTaskList;
  if (cases.length === 0) return "SWITCH 至少需要配置一个 Case";
  if (cases.some((item) => !item.condition.trim())) {
    return "请为每个 Case 配置判断表达式";
  }
  const expressions = cases.map((item) => item.condition.trim());
  if (new Set(expressions).size !== expressions.length) {
    return "Case 判断表达式不能重复，请检查分支顺序";
  }
  return null;
}

/**
 * Keep an existing Case connection attached to the same expression when the
 * editor reorders or removes cases. Unmatched deleted cases lose their edge.
 */
export function remapSwitchCaseHandles(
  nodeId: string,
  previousParams: unknown,
  nextParams: unknown,
  edges: WorkflowCanvasEdge[],
): WorkflowCanvasEdge[] {
  const previousCases = normalizeSwitchTaskParams(previousParams).switchResult.dependTaskList;
  const nextCases = normalizeSwitchTaskParams(nextParams).switchResult.dependTaskList;
  const claimed = new Set<number>();
  const indexMap = new Map<number, number>();

  previousCases.forEach((item, previousIndex) => {
    const expression = item.condition.trim();
    if (!expression) return;
    const nextIndex = nextCases.findIndex(
      (candidate, index) => !claimed.has(index) && candidate.condition.trim() === expression,
    );
    if (nextIndex >= 0) {
      indexMap.set(previousIndex, nextIndex);
      claimed.add(nextIndex);
    }
  });

  previousCases.forEach((_item, previousIndex) => {
    if (indexMap.has(previousIndex)) return;
    if (previousIndex < nextCases.length && !claimed.has(previousIndex)) {
      indexMap.set(previousIndex, previousIndex);
      claimed.add(previousIndex);
    }
  });

  return edges.flatMap((edge) => {
    if (edge.source !== nodeId || !edge.sourceHandle?.startsWith("case-")) {
      return [edge];
    }
    const previousIndex = Number(edge.sourceHandle.slice("case-".length));
    if (!Number.isInteger(previousIndex)) return [edge];
    const nextIndex = indexMap.get(previousIndex);
    if (nextIndex === undefined) return [];
    return [{ ...edge, sourceHandle: "case-" + nextIndex }];
  });
}
