import type { WorkflowBranchOutput } from "../types";

export type WorkflowBranchEdgeLike = {
  source: string;
  target: string;
  sourceHandle?: string | null;
};

type JsonRecord = Record<string, unknown>;

function asRecord(value: unknown): JsonRecord {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as JsonRecord
    : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function parseTaskParams(value: unknown): JsonRecord {
  if (typeof value === "string") {
    if (!value.trim()) return {};
    const parsed: unknown = JSON.parse(value);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("逻辑节点参数必须是 JSON 对象");
    }
    return parsed as JsonRecord;
  }
  return asRecord(value);
}

function targetCodes(value: unknown): number[] {
  return asArray(value)
    .map((item) => Number(item))
    .filter((item) => Number.isSafeInteger(item) && item > 0);
}

export function getWorkflowBranchOutputs(
  taskType: string,
  taskParams: unknown,
  includeUnassigned = false,
): WorkflowBranchOutput[] {
  const type = taskType.toUpperCase();
  if (type === "CONDITIONS") {
    const outputs: WorkflowBranchOutput[] = [
      { id: "success", label: "成功", kind: "success" },
      { id: "failed", label: "失败", kind: "failed" },
    ];
    if (includeUnassigned) {
      outputs.push({ id: "unassigned", label: "未配置", kind: "unassigned" });
    }
    return outputs;
  }

  if (type === "SWITCH") {
    const params = parseTaskParams(taskParams);
    const switchResult = asRecord(params.switchResult);
    const cases = asArray(switchResult.dependTaskList);
    const outputs: WorkflowBranchOutput[] = cases.map((_item, index) => ({
      id: "case-" + index,
      label: "Case " + (index + 1),
      kind: "switch-case",
      caseIndex: index,
    }));
    outputs.push({ id: "default", label: "Default", kind: "default" });
    if (includeUnassigned) {
      outputs.push({ id: "unassigned", label: "未配置", kind: "unassigned" });
    }
    return outputs;
  }

  return [];
}

export function resolveWorkflowSourceHandle(
  taskType: string,
  taskParams: unknown,
  targetTaskCode: number,
): string {
  const type = taskType.toUpperCase();
  const params = parseTaskParams(taskParams);

  if (type === "CONDITIONS") {
    const result = asRecord(params.conditionResult);
    const success = targetCodes(result.successNode).includes(targetTaskCode);
    const failed = targetCodes(result.failedNode).includes(targetTaskCode);
    if (success === failed) return "unassigned";
    return success ? "success" : "failed";
  }

  if (type === "SWITCH") {
    const result = asRecord(params.switchResult);
    const cases = asArray(result.dependTaskList);
    const matches = cases.flatMap((item, index) => {
      const nextNode = Number(asRecord(item).nextNode);
      return nextNode === targetTaskCode ? ["case-" + index] : [];
    });
    const defaultMatches = Number(result.nextNode) === targetTaskCode;
    if (matches.length + Number(defaultMatches) !== 1) return "unassigned";
    return defaultMatches ? "default" : matches[0];
  }

  return "source";
}

export function applyWorkflowBranchTargets(
  taskType: string,
  taskParams: unknown,
  nodeId: string,
  edges: WorkflowBranchEdgeLike[],
): unknown {
  const type = taskType.toUpperCase();
  if (type !== "CONDITIONS" && type !== "SWITCH") {
    return taskParams;
  }
  const params = parseTaskParams(taskParams);

  const outgoing = edges.filter((edge) => edge.source === nodeId);
  const targetsFor = (handle: string) => outgoing
    .filter((edge) => (edge.sourceHandle ?? "source") === handle)
    .map((edge) => Number(edge.target))
    .filter((code) => Number.isSafeInteger(code) && code > 0);

  if (outgoing.some((edge) => edge.sourceHandle === "unassigned")) {
    throw new Error("逻辑节点存在未识别的分支连线，请重新连接后再保存");
  }

  if (type === "CONDITIONS") {
    const successNode = targetsFor("success");
    const failedNode = targetsFor("failed");
    if (successNode.length === 0) {
      throw new Error("CONDITIONS 必须至少连接一个成功分支");
    }
    if (failedNode.length === 0) {
      throw new Error("CONDITIONS 必须至少连接一个失败分支");
    }
    const conditionResult = asRecord(params.conditionResult);
    return {
      ...params,
      conditionResult: {
        ...conditionResult,
        conditionSuccess: false,
        successNode,
        failedNode,
      },
    };
  }

  const switchResult = asRecord(params.switchResult);
  const cases = asArray(switchResult.dependTaskList);
  if (cases.length === 0) {
    throw new Error("SWITCH 至少需要配置一个 Case");
  }

  const dependTaskList = cases.map((item, index) => {
    const condition = String(asRecord(item).condition ?? "").trim();
    if (!condition) {
      throw new Error("SWITCH 的 Case " + (index + 1) + " 尚未配置判断表达式");
    }
    const targets = targetsFor("case-" + index);
    if (targets.length !== 1) {
      throw new Error("SWITCH 的 Case " + (index + 1) + " 必须且只能连接一个下游节点");
    }
    return { ...asRecord(item), condition, nextNode: targets[0] };
  });

  const defaultTargets = targetsFor("default");
  if (defaultTargets.length !== 1) {
    throw new Error("SWITCH 的 Default 分支必须且只能连接一个下游节点");
  }

  return {
    ...params,
    switchResult: {
      ...switchResult,
      dependTaskList,
      nextNode: defaultTargets[0],
    },
  };
}
