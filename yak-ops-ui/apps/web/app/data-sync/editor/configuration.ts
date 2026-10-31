import type {
  DataSyncDraft,
  DataSyncPipelineOptions,
  DataSyncSolution,
  DataSyncStep,
  DataSyncTableMapping,
} from "@/service/data-sync";
import { offlineIncremental } from "@/service/data-sync/parameters";
import { findStepCombination } from "../solution/presentation";
import { EMPTY_INCREMENTAL } from "./incremental-fields";
import type { EditorForm } from "./types";

export interface EditorConfiguration {
  draft: DataSyncDraft;
  form: EditorForm;
  mappings: DataSyncTableMapping[];
}

export function changeReadStrategy(
  value: EditorConfiguration,
  steps: DataSyncStep[],
  solution?: DataSyncSolution,
): EditorConfiguration {
  const currentSteps: DataSyncStep[] = value.draft.steps.filter((step) => step !== "SCHEMA");
  if (currentSteps.length === steps.length && steps.every((step) => currentSteps.includes(step)))
    return value;
  const nextSteps: DataSyncStep[] =
    value.draft.steps.includes("SCHEMA") && steps.includes("FULL") ? ["SCHEMA", ...steps] : steps;
  const next = findStepCombination(solution, nextSteps);
  if (!next) return value;
  const continuous =
    value.draft.productType === "DATABASE_REPLICATION" && steps.includes("INCREMENTAL");
  return {
    draft: { ...value.draft, steps: nextSteps },
    form: {
      ...value.form,
      startup: undefined,
      source: {
        ...value.form.source,
        incremental: offlineIncremental(value.draft.productType, steps)
          ? value.form.source.incremental || EMPTY_INCREMENTAL
          : undefined,
      },
      sink: {
        ...value.form.sink,
        writeMode: next.writeModes.includes(value.form.sink.writeMode)
          ? value.form.sink.writeMode
          : next.writeModes[0],
        schemaChangesEnabled: continuous && value.form.sink.schemaChangesEnabled,
        allowDropColumns: continuous && value.form.sink.allowDropColumns,
      },
    },
    mappings: value.mappings.map((mapping) => ({
      ...mapping,
      source: steps.includes("FULL")
        ? mapping.source
        : { ...mapping.source, chunkKeyColumn: undefined },
      writeMode: next.writeModes.includes(mapping.writeMode)
        ? mapping.writeMode
        : next.writeModes[0],
    })),
  };
}

export function changeSchemaPreparation(
  value: EditorConfiguration,
  enabled: boolean,
  solution?: DataSyncSolution,
): EditorConfiguration {
  const steps: DataSyncStep[] = value.draft.steps.filter((step) => step !== "SCHEMA");
  if (enabled) steps.unshift("SCHEMA");
  if (!findStepCombination(solution, steps)) return value;
  return {
    ...value,
    draft: { ...value.draft, steps },
    mappings: enabled
      ? value.mappings.map((mapping) => ({
          ...mapping,
          target: {
            ...mapping.target,
            database: mapping.target.database || value.form.sink.database || undefined,
            schema: mapping.target.schema || value.form.sink.schema || undefined,
            table: mapping.target.table || mapping.source.table,
          },
        }))
      : value.mappings,
  };
}

const ENGINE_KEYS = [
  "name",
  "parallelism",
  "local-time-zone",
  "schema.change.behavior",
  "schema.operator.uid",
  "route-mode",
  "transform.decimal.precision.mode",
];

export function editableEngineOptions(value?: DataSyncPipelineOptions): DataSyncPipelineOptions {
  return Object.fromEntries(
    Object.entries(value || {}).filter(([key]) => ENGINE_KEYS.includes(key)),
  );
}

export function parseEngineOptions(
  content: string,
  schemaChangesEnabled: boolean,
): DataSyncPipelineOptions {
  const value = JSON.parse(content) as DataSyncPipelineOptions;
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error("引擎参数必须是 JSON 对象");
  for (const [key, option] of Object.entries(value)) {
    if (!ENGINE_KEYS.includes(key)) throw new Error(`不支持的引擎参数：${key}`);
    if (key === "parallelism") {
      if (
        !/^\d+$/.test(String(option)) ||
        !Number.isSafeInteger(Number(option)) ||
        Number(option) < 1
      )
        throw new Error("并行度必须是正整数");
    } else if (typeof option !== "string") throw new Error(`${key} 必须是字符串`);
  }
  if (
    value["schema.change.behavior"] !== undefined &&
    value["schema.change.behavior"].toLowerCase() !==
      (schemaChangesEnabled ? "evolve" : "exception")
  )
    throw new Error("结构变更行为与 DDL 配置不一致");
  return value;
}
