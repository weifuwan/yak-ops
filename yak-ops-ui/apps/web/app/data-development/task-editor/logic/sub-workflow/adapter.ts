import { getWorkflowDefinition } from "@/service/workflow/definition";

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

export function normalizeSubWorkflowTaskParams(value: unknown): {
  localParams: unknown[];
  workflowDefinitionCode: number;
} {
  const params = asRecord(value);
  return {
    localParams: Array.isArray(params.localParams) ? params.localParams : [],
    workflowDefinitionCode: Number(params.workflowDefinitionCode ?? 0),
  };
}

/**
 * Reject direct self-reference and transitive cycles before applying a
 * SUB_WORKFLOW task. Existing nested sub-workflow references are traversed
 * through the same workflow DAG API used by the editor.
 */
export async function hasSubWorkflowCycle(
  currentWorkflowCode: number,
  selectedWorkflowCode: number,
): Promise<boolean> {
  if (currentWorkflowCode <= 0 || selectedWorkflowCode <= 0) return false;
  if (currentWorkflowCode === selectedWorkflowCode) return true;

  const visited = new Set<number>();
  const visit = async (workflowCode: number, path: Set<number>, depth: number): Promise<boolean> => {
    if (workflowCode === currentWorkflowCode) return true;
    if (path.has(workflowCode)) return true;
    if (visited.has(workflowCode)) return false;
    if (depth > 32) {
      throw new Error("子工作流嵌套超过 32 层，无法完成循环引用校验");
    }

    visited.add(workflowCode);
    path.add(workflowCode);
    const dag = await getWorkflowDefinition(workflowCode);
    const childCodes = (dag.taskDefinitionList ?? [])
      .filter((task) => task.taskType.toUpperCase() === "SUB_WORKFLOW")
      .map((task) => normalizeSubWorkflowTaskParams(task.taskParams).workflowDefinitionCode)
      .filter((code) => Number.isSafeInteger(code) && code > 0);

    for (const childCode of childCodes) {
      if (await visit(childCode, path, depth + 1)) return true;
    }
    path.delete(workflowCode);
    return false;
  };

  return visit(selectedWorkflowCode, new Set<number>(), 0);
}
