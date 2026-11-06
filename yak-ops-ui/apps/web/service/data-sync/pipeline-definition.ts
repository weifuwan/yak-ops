import type { DataSyncEditorParameters, DataSyncParameters } from "./types";

/**
 * The editor remains a form. Persist the native Flink CDC YAML mapping instead
 * of a second runtime configuration vocabulary.
 */
export function encodeNativePipelineParameters(value: DataSyncEditorParameters): DataSyncParameters {
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
    source[0]?.read !== undefined ||
    source[0]?.options !== undefined ||
    source[0]?.snapshot !== undefined ||
    source[0]?.dml !== undefined ||
    source[0]?.incremental !== undefined ||
    value.sink.mapping !== undefined ||
    Boolean(value.sink.typeMappings?.length) ||
    sink[0]?.write.primaryKeys !== undefined ||
    sink[0]?.write.transaction !== undefined ||
    sink[0]?.write.preSql !== undefined ||
    sink[0]?.write.postSql !== undefined ||
    sink[0]?.write.batchSize !== undefined ||
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
  const options: DataSyncParameters["definition"]["pipeline"] = {
    ...value.pipeline,
    "execution.runtime-mode": "STREAMING",
    "schema.change.behavior": schemaBehavior,
  };
  return {
    definition: {
      source: {
        ...value.source.pipelineOptions,
        type: "mysql",
        "datasource-id": value.source.datasourceId,
        tables: sourcePattern,
        "scan.startup.mode": "initial",
      },
      sink: {
        ...value.sink.pipelineOptions,
        type: "jdbc",
        "datasource-id": value.sink.datasourceId,
        "exclude.schema.changes": ["drop.column"],
      },
      route: [{
        "source-table": sourcePattern,
        "sink-table": targetPath,
        ...(value.route[0]?.description ? { description: value.route[0].description } : {}),
      }],
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
  const definition = (content as DataSyncParameters).definition;
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
  const options = { ...pipeline };
  const sourcePipelineOptions = Object.fromEntries(
    Object.entries(source).filter(
      ([key]) => !["type", "datasource-id", "tables", "scan.startup.mode"].includes(key),
    ),
  );
  const sinkPipelineOptions = Object.fromEntries(
    Object.entries(sink).filter(
      ([key]) => !["type", "datasource-id", "exclude.schema.changes"].includes(key),
    ),
  );
  return {
    source: {
      type: "mysql",
      datasourceId: String(source["datasource-id"]),
      ...(Object.keys(sourcePipelineOptions).length ? { pipelineOptions: sourcePipelineOptions } : {}),
      startup: { mode: "INITIAL" },
      tables: [sourceTable],
    },
    sink: {
      type: "jdbc",
      datasourceId: String(sink["datasource-id"]),
      ...(Object.keys(sinkPipelineOptions).length ? { pipelineOptions: sinkPipelineOptions } : {}),
      schemaPolicy: {
        initial: "CREATE_OR_ALTER",
        evolution: { enabled: canEvolve, allowDropColumns: false },
      },
      tables: [{ ...sinkTable, write: { mode: "UPSERT" } }],
    },
    route: [{
      "source-table": `${sourceTable.database}.${sourceTable.table}`,
      "sink-table": `${sinkTable.database}.${sinkTable.table}`,
      ...(typeof route.description === "string" ? { description: route.description } : {}),
    }],
    transform: [],
    pipeline: {
      ...options,
      "execution.runtime-mode": "STREAMING",
    } as DataSyncEditorParameters["pipeline"],
  };
}
