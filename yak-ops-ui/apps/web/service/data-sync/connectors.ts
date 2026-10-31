import type { DataSyncSolution, DataSyncStep, DataSyncWriteMode } from "./types";

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

/** One workflow per installed channel; snapshot and continuous reads are strategies. */
export function connectorChoices(pairs: PipelineConnectorPair[]): DataSyncSolution[] {
  return pairs.flatMap((pair): DataSyncSolution[] => {
    if (!pair.producesChangelog && pair.boundedness !== "BOUNDED") return [];
    const replication = pair.producesChangelog;
    const productType = replication ? "DATABASE_REPLICATION" : "SINGLE_TABLE_BATCH";
    const dataSteps: DataSyncStep[][] = replication
      ? [["FULL", "INCREMENTAL"], ["INCREMENTAL"], ["FULL"]]
      : pair.supportsIncrementalBatch
        ? [["FULL"], ["INCREMENTAL"], ["FULL", "INCREMENTAL"]]
        : [["FULL"]];
    const allowedCombinations: DataSyncSolution["allowedCombinations"] = dataSteps.map((steps) => {
      const continuous = replication && steps.includes("INCREMENTAL");
      const writeModes: DataSyncWriteMode[] = continuous
        ? pair.supportsRetract
          ? ["UPSERT", "RETRACT"]
          : ["UPSERT"]
        : steps.includes("INCREMENTAL")
          ? ["UPSERT"]
          : ["APPEND", "UPSERT"];
      return { steps, supportsScheduling: !continuous, writeModes };
    });
    if (pair.supportsSchemaChanges) {
      const schemaCombinations = allowedCombinations
        .filter((combination) => combination.steps.includes("FULL"))
        .map((combination) => ({
          ...combination,
          steps: ["SCHEMA" as const, ...combination.steps],
        }));
      allowedCombinations.push(...schemaCombinations);
    }
    return [
      {
        id: `${pair.sourceConnector}:${pair.sinkConnector}:${productType}`,
        sourceConnector: pair.sourceConnector,
        sinkConnector: pair.sinkConnector,
        producesChangelog: pair.producesChangelog,
        productType,
        displayName: replication ? "整库实时" : "单表离线",
        steps: [
          ...(pair.supportsSchemaChanges ? ["SCHEMA" as const] : []),
          "FULL",
          ...(dataSteps.some((steps) => steps.includes("INCREMENTAL"))
            ? ["INCREMENTAL" as const]
            : []),
        ],
        defaultSteps: replication ? ["FULL", "INCREMENTAL"] : ["FULL"],
        allowedCombinations,
      },
    ];
  });
}
