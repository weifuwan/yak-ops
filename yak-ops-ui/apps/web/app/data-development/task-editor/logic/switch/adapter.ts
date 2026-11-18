import type { WorkflowCanvasEdge } from "../../../workflow-editor/types";

import type { SwitchCase, SwitchLocalParam, SwitchTaskParams } from "./types";

export type SwitchVariableOption = {
  value: string;
  label: string;
  dataType?: string;
  source?: string;
};

export type SwitchWorkflowTaskLike = {
  code: number;
  name?: string;
  taskParams?: unknown;
};

export type SwitchWorkflowRelationLike = {
  preTaskCode: number;
  postTaskCode: number;
};

export type SwitchCaseWarning = {
  caseIndex: number;
  previousCaseIndex: number;
  message: string;
};

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
    localParams: Array.isArray(params.localParams)
      ? params.localParams.map((item) => {
          const current = asRecord(item);
          return {
            ...current,
            prop: String(current.prop ?? ""),
          } as SwitchLocalParam;
        })
      : [],
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
  if (cases.some((item) => !item.condition.trim())) {
    return "请为每个 Case 配置判断表达式";
  }
  if (cases.some((item) => /(?:===|!==|==|!=|>=|<=|>|<|&&|\|\|)\s*$/.test(item.condition.trim()))) {
    return "存在未填写完整的判断表达式，请补充右侧操作数";
  }
  const expressions = cases.map((item) => item.condition.trim());
  if (new Set(expressions).size !== expressions.length) {
    return "Case 判断表达式不能重复，请检查分支顺序";
  }
  return null;
}

function parseList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (typeof value !== "string" || !value.trim()) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

const SYSTEM_VARIABLES: Array<{ prop: string; dataType?: string }> = [
  { prop: "system.biz.curdate", dataType: "DATE" },
  { prop: "system.biz.date", dataType: "DATE" },
  { prop: "system.datetime", dataType: "DATETIME" },
  { prop: "system.task.execute.path", dataType: "STRING" },
  { prop: "system.task.instance.id", dataType: "LONG" },
  { prop: "system.task.definition.name", dataType: "STRING" },
  { prop: "system.task.definition.code", dataType: "LONG" },
  { prop: "system.workflow.instance.id", dataType: "LONG" },
  { prop: "system.workflow.definition.name", dataType: "STRING" },
  { prop: "system.workflow.definition.code", dataType: "LONG" },
  { prop: "system.project.name", dataType: "STRING" },
  { prop: "system.project.code", dataType: "LONG" },
];

function getUpstreamTaskCodes(
  currentTaskCode: number,
  relations: SwitchWorkflowRelationLike[],
): Set<number> {
  const predecessors = new Map<number, number[]>();
  for (const relation of relations) {
    const source = Number(relation.preTaskCode);
    const target = Number(relation.postTaskCode);
    if (!Number.isSafeInteger(source) || source <= 0 || !Number.isSafeInteger(target) || target <= 0) {
      continue;
    }
    const items = predecessors.get(target) ?? [];
    items.push(source);
    predecessors.set(target, items);
  }

  const visited = new Set<number>();
  const pending = [...(predecessors.get(currentTaskCode) ?? [])];
  while (pending.length > 0) {
    const code = pending.pop();
    if (!code || visited.has(code)) continue;
    visited.add(code);
    pending.push(...(predecessors.get(code) ?? []));
  }
  return visited;
}

function getDataType(value: unknown): string | undefined {
  const dataType = String(value ?? "").trim();
  return dataType ? dataType.toUpperCase() : undefined;
}

export function getSwitchVariableOptions(
  globalParams: unknown,
  taskParams: unknown,
  projectParams: unknown = [],
  workflowTasks: SwitchWorkflowTaskLike[] = [],
  workflowRelations: SwitchWorkflowRelationLike[] = [],
  currentTaskCode = 0,
): SwitchVariableOption[] {
  const entries: Array<{ prop: unknown; source: string; dataType?: unknown }> = [
    ...SYSTEM_VARIABLES.map((item) => ({
      prop: item.prop,
      source: "系统",
      dataType: item.dataType,
    })),
    ...parseList(projectParams).map((item) => {
      const property = asRecord(item);
      return {
        prop: property.paramName ?? property.prop,
        source: "项目",
        dataType: property.paramDataType ?? property.type,
      };
    }),
    ...parseList(globalParams).map((item) => {
      const property = asRecord(item);
      return {
        prop: property.prop,
        source: "全局",
        dataType: property.type ?? property.paramDataType,
      };
    }),
  ];

  const upstreamCodes = getUpstreamTaskCodes(currentTaskCode, workflowRelations);
  for (const task of workflowTasks) {
    if (!upstreamCodes.has(Number(task.code))) continue;
    const taskParamsRecord = asRecord(task.taskParams);
    for (const item of parseList(taskParamsRecord.localParams)) {
      const property = asRecord(item);
      if (String(property.direct ?? "").toUpperCase() !== "OUT") continue;
      entries.push({
        prop: property.prop,
        source: "上游" + (task.name ? " " + task.name : ""),
        dataType: property.type,
      });
    }
  }

  const currentParams = normalizeSwitchTaskParams(taskParams);
  for (const property of currentParams.localParams ?? []) {
    entries.push({
      prop: property.prop,
      source: "节点",
      dataType: property.type,
    });
  }

  const options = new Map<string, SwitchVariableOption>();
  for (const entry of entries) {
    const prop = String(entry.prop ?? "").trim();
    if (!prop) continue;
    const dataType = getDataType(entry.dataType);
    const source = entry.source;
    options.set(prop, {
      value: "${" + prop + "}",
      label: prop + "（" + source + (dataType ? " · " + dataType : "") + "）",
      dataType,
      source,
    });
  }
  return [...options.values()];
}

function parseSimpleNumericCondition(condition: string) {
  const match = condition.match(/^\s*(\$\{[^}]+\})\s*(>=|>|<=|<)\s*(-?\d+(?:\.\d+)?)\s*$/);
  if (!match) return null;
  return {
    variable: match[1],
    operator: match[2],
    value: Number(match[3]),
  };
}

/**
 * Warn about obvious numeric ranges shadowed by an earlier Case.
 * This is advisory only; complex JavaScript expressions remain runtime-evaluated.
 */
export function getSwitchCaseWarnings(value: unknown): SwitchCaseWarning[] {
  const cases = normalizeSwitchTaskParams(value).switchResult.dependTaskList;
  const warnings: SwitchCaseWarning[] = [];
  for (let caseIndex = 0; caseIndex < cases.length; caseIndex += 1) {
    const current = parseSimpleNumericCondition(cases[caseIndex].condition.trim());
    if (!current) continue;
    for (let previousCaseIndex = 0; previousCaseIndex < caseIndex; previousCaseIndex += 1) {
      const previous = parseSimpleNumericCondition(cases[previousCaseIndex].condition.trim());
      if (!previous || previous.variable !== current.variable || previous.operator !== current.operator) {
        continue;
      }
      const coversCurrent =
        (current.operator === ">=" || current.operator === ">")
          ? previous.value <= current.value
          : previous.value >= current.value;
      if (coversCurrent) {
        warnings.push({
          caseIndex,
          previousCaseIndex,
          message: "当前条件可能被 Case " + (previousCaseIndex + 1) + " 覆盖，建议调整顺序或表达式",
        });
        break;
      }
    }
  }
  return warnings;
}

/**
 * Keep a Case edge attached to its downstream task first, then fall back to
 * its expression. This means editing an expression does not detach a saved
 * branch, while deleting a connected Case removes only that Case edge.
 */
export function remapSwitchCaseHandles(
  nodeId: string,
  previousParams: unknown,
  nextParams: unknown,
  edges: WorkflowCanvasEdge[],
): WorkflowCanvasEdge[] {
  const previousCases = normalizeSwitchTaskParams(previousParams).switchResult.dependTaskList;
  const nextCases = normalizeSwitchTaskParams(nextParams).switchResult.dependTaskList;
  const outgoingCaseEdges = new Map<number, WorkflowCanvasEdge[]>();
  for (const edge of edges) {
    if (edge.source !== nodeId || !edge.sourceHandle?.startsWith("case-")) continue;
    const previousIndex = Number(edge.sourceHandle.slice("case-".length));
    if (!Number.isInteger(previousIndex) || previousIndex < 0) continue;
    const current = outgoingCaseEdges.get(previousIndex) ?? [];
    current.push(edge);
    outgoingCaseEdges.set(previousIndex, current);
  }

  const positiveCode = (value: unknown): number | null => {
    const code = Number(value);
    return Number.isSafeInteger(code) && code > 0 ? code : null;
  };
  const previousTargets = previousCases.map((item, index) => {
    const connectedEdges = outgoingCaseEdges.get(index) ?? [];
    return connectedEdges.length === 1
      ? positiveCode(connectedEdges[0].target) ?? positiveCode(item.nextNode)
      : positiveCode(item.nextNode);
  });
  const claimed = new Set<number>();
  const indexMap = new Map<number, number>();

  previousTargets.forEach((target, previousIndex) => {
    if (target === null) return;
    const candidates = nextCases.flatMap((item, index) =>
      !claimed.has(index) && positiveCode(item.nextNode) === target ? [index] : [],
    );
    if (candidates.length === 1) {
      indexMap.set(previousIndex, candidates[0]);
      claimed.add(candidates[0]);
    }
  });

  previousCases.forEach((item, previousIndex) => {
    if (indexMap.has(previousIndex)) return;
    const expression = item.condition.trim();
    if (!expression) return;
    const candidates = nextCases.flatMap((candidate, index) =>
      !claimed.has(index) && candidate.condition.trim() === expression ? [index] : [],
    );
    if (candidates.length === 1) {
      indexMap.set(previousIndex, candidates[0]);
      claimed.add(candidates[0]);
    }
  });

  previousCases.forEach((_item, previousIndex) => {
    if (indexMap.has(previousIndex) || previousTargets[previousIndex] !== null) return;
    const candidate = nextCases[previousIndex];
    if (!candidate || claimed.has(previousIndex) || positiveCode(candidate.nextNode) !== null) return;
    indexMap.set(previousIndex, previousIndex);
    claimed.add(previousIndex);
  });

  return edges.flatMap((edge) => {
    if (edge.source !== nodeId || !edge.sourceHandle?.startsWith("case-")) {
      return [edge];
    }
    const previousIndex = Number(edge.sourceHandle.slice("case-".length));
    if (!Number.isInteger(previousIndex) || previousIndex < 0) return [edge];
    const nextIndex = indexMap.get(previousIndex);
    if (nextIndex === undefined) return [];
    return [{ ...edge, sourceHandle: "case-" + nextIndex }];
  });
}
