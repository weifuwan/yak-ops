import type {
  DataSyncSolution,
  DataSyncSolutionType,
  DataSyncStep,
  DataSyncWriteMode,
} from "./types";

/** Capabilities discovered from the installed source and sink Factory SPIs. */
export interface PipelineConnectorPair {
  sourceConnector: string;
  sinkConnector: string;
  producesChangelog: boolean;
  supportsIncrementalBatch: boolean;
  supportsSchemaChanges: boolean;
  supportsRetract: boolean;
  boundedness: "BOUNDED" | "CONTINUOUS_UNBOUNDED";
}

/** Product presets retain the selected Connector identifiers independently of labels and table scope. */
export function connectorChoices(pairs: PipelineConnectorPair[]): DataSyncSolution[] {
  const choices: DataSyncSolution[] = [];
  for (const pair of pairs) {
    for (const multiple of [false, true]) {
      const modes: Array<"BATCH" | "STREAMING"> =
        pair.boundedness === "BOUNDED"
          ? ["BATCH"]
          : pair.producesChangelog
            ? ["STREAMING", "BATCH"]
            : ["STREAMING"];
      for (const runtimeMode of modes) {
        const streaming = runtimeMode === "STREAMING";
        const solutionType: DataSyncSolutionType = multiple
          ? streaming
            ? "DATABASE_REALTIME"
            : "DATABASE_OFFLINE"
          : streaming
            ? "SINGLE_TABLE_REALTIME"
            : "SINGLE_TABLE_OFFLINE";
        const snapshot = pair.producesChangelog && !streaming;
        const writes: DataSyncWriteMode[] =
          pair.producesChangelog && streaming
            ? streaming && !multiple && pair.supportsRetract
              ? ["UPSERT", "RETRACT"]
              : ["UPSERT"]
            : ["APPEND", "UPSERT"];
        const defaultSteps: DataSyncStep[] = streaming ? ["FULL", "INCREMENTAL"] : ["FULL"];
        const dataSteps: DataSyncStep[][] = streaming
          ? [["FULL", "INCREMENTAL"], ["INCREMENTAL"]]
          : !pair.producesChangelog && pair.supportsIncrementalBatch
            ? [["FULL"], ["INCREMENTAL"], ["FULL", "INCREMENTAL"]]
            : [["FULL"]];
        const allowedCombinations: DataSyncSolution["allowedCombinations"] = dataSteps.map(
          (steps) => ({
            steps,
            supportsScheduling: !streaming,
            writeModes:
              !pair.producesChangelog && steps.includes("INCREMENTAL") ? ["UPSERT"] : writes,
          }),
        );
        if (pair.supportsSchemaChanges) {
          const initialCombinations = allowedCombinations.filter((combination) =>
            combination.steps.includes("FULL"),
          );
          for (const combination of initialCombinations) {
            allowedCombinations.push({ ...combination, steps: ["SCHEMA", ...combination.steps] });
          }
        }
        const steps: DataSyncStep[] = [
          ...(pair.supportsSchemaChanges ? ["SCHEMA" as const] : []),
          "FULL",
          ...(dataSteps.some((value) => value.includes("INCREMENTAL"))
            ? ["INCREMENTAL" as const]
            : []),
        ];
        choices.push({
          id: `${pair.sourceConnector}:${pair.sinkConnector}:${solutionType}`,
          sourceConnector: pair.sourceConnector,
          sinkConnector: pair.sinkConnector,
          producesChangelog: pair.producesChangelog,
          runtimeMode,
          solutionType,
          displayName: `${multiple ? "整库" : "单表"}${streaming ? "实时" : "离线"}${snapshot ? "（CDC 快照）" : ""}`,
          steps,
          defaultSteps,
          allowedCombinations,
        });
      }
    }
  }
  return choices;
}
