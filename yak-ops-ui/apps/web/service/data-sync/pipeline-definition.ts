import type { DataSyncEditorParameters, DataSyncPipelineTaskParameters } from "./types";

/**
 * The editor remains a form. Persist the native Flink CDC YAML mapping instead
 * of a second runtime configuration vocabulary.
 */
export function encodeNativePipelineParameters(value: DataSyncEditorParameters): DataSyncPipelineTaskParameters {
  const source = value.source.tables;
  const sink = value.sink.tables;
  if (
    value.source.type !== "mysql" ||
    value.sink.type !== "jdbc" ||
    source.length !== 1 ||
    sink.length !== 1 ||
    value.source.startup?.mode !== "INITIAL" ||
    value.pipeline["execution.runtime-mode"] !== "STREAMING" ||
    sink[0]?.write.mode !== "UPSERT" ||
    value.transform.length !== 0 ||
    value.source.selection !== undefined ||
    source[0]?.snapshot !== undefined ||
    source[0]?.dml !== undefined ||
    value.sink.schemaPolicy.initial !== "CREATE_OR_ALTER" ||
    value.sink.schemaPolicy.evolution.allowDropColumns
  ) {
    throw new Error("当前 CDC Runtime 只支持 MySQL 单表 INITIAL → JDBC UPSERT，禁止删除字段和自定义转换");
  }
  const path = (entry: { database?: string; schema?: string; table: string }) =>
    [entry.database, entry.schema, entry.table].filter(Boolean).join(".");
  const sourcePath = path(source[0]);
  const targetPath = path(sink[0]);
  if (!/^[A-Za-z0-9_$-]+\.[A-Za-z0-9_$-]+$/.test(sourcePath) ||
      !/^[A-Za-z0-9_$-]+\.[A-Za-z0-9_$-]+$/.test(targetPath)) {
    throw new Error("当前 CDC Runtime 的表名格式为 database.table");
  }
  const sourcePattern = `\\Q${sourcePath}\\E`;
  const schemaBehavior = value.sink.schemaPolicy.evolution.enabled ? "evolve" : "exception";
  const options: Record<string, unknown> = { ...value.pipeline };
  options["execution.runtime-mode"] = "STREAMING";
  options["schema.change.behavior"] = schemaBehavior;
  return {
    definition: {
      source: {
        type: "mysql",
        "datasource-id": value.source.datasourceId,
        tables: sourcePattern,
        "scan.startup.mode": "initial",
      },
      sink: {
        type: "jdbc",
        "datasource-id": value.sink.datasourceId,
        "exclude.schema.changes": ["drop.column"],
      },
      route: [{ "source-table": sourcePattern, "sink-table": targetPath }],
      pipeline: options,
    },
  };
}

/** Rebuild form state from the single persisted Pipeline definition. */
export function decodeNativePipelineParameters(content: unknown): DataSyncEditorParameters {
  if (!content || typeof content !== "object" || Array.isArray(content) ||
      !("definition" in content)) {
    throw new Error("无效的 CDC Pipeline 定义");
  }
  const definition = (content as DataSyncPipelineTaskParameters).definition;
  if (!definition || !Array.isArray(definition.route) || definition.route.length !== 1) {
    throw new Error("Pipeline 需要一条表映射");
  }
  const source = definition.source;
  const sink = definition.sink;
  const pipeline = definition.pipeline;
  const route = definition.route[0];
  const pattern = source?.tables;
  if (source?.type !== "mysql" || sink?.type !== "jdbc" || typeof pattern !== "string" ||
      !pattern.startsWith("\\Q") || !pattern.endsWith("\\E") ||
      route?.["source-table"] !== pattern || typeof route?.["sink-table"] !== "string") {
    throw new Error("尚不支持此 Pipeline Connector 或表映射");
  }
  const fromPath = (fullPath: string) => {
    const parts = fullPath.split(".");
    if (parts.length !== 2 || !parts[0] || !parts[1]) throw new Error("无效的表路径");
    return { database: parts[0], table: parts[1] };
  };
  const sourceTable = fromPath(pattern.slice(2, -2));
  const sinkTable = fromPath(route["sink-table"] as string);
  const disabledDrop = Array.isArray(sink["exclude.schema.changes"]) &&
    (sink["exclude.schema.changes"] as string[]).includes("drop.column");
  if (!disabledDrop) throw new Error("当前 Runtime 不允许删除目标字段");
  const canEvolve = pipeline["schema.change.behavior"] === "evolve";
  const options = Object.fromEntries(
    Object.entries(pipeline).filter(([key]) => key !== "name" || typeof pipeline[key] === "string"),
  );
  return {
    source: {
      type: "mysql",
      datasourceId: String(source["datasource-id"]),
      startup: { mode: "INITIAL" },
      tables: [sourceTable],
    },
    sink: {
      type: "jdbc",
      datasourceId: String(sink["datasource-id"]),
      schemaPolicy: {
        initial: "CREATE_OR_ALTER",
        evolution: { enabled: canEvolve, allowDropColumns: false },
      },
      tables: [{ ...sinkTable, write: { mode: "UPSERT" } }],
    },
    route: [{
      "source-table": `${sourceTable.database}.${sourceTable.table}`,
      "sink-table": `${sinkTable.database}.${sinkTable.table}`,
    }],
    transform: [],
    pipeline: {
      ...options,
      "execution.runtime-mode": "STREAMING",
    } as DataSyncEditorParameters["pipeline"],
  };
}
