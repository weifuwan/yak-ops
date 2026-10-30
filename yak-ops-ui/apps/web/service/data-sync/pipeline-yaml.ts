import { parse, stringify } from "yaml";
import type { DataSyncParameters, DataSyncTablePath, DataSyncStartupOptions } from "./types";

/**
 * Flink CDC 3.7-style YAML public definition. The editor model is transient:
 * only { pipelineYaml } crosses the Task Definition API / persistence boundary.
 */
export interface PipelineYamlParameters {
  pipelineYaml: string;
}

type ScalarMap = Record<string, string>;
type PipelineDocument = {
  source: ScalarMap;
  sink: ScalarMap;
  route: Array<{ "source-table": string; "sink-table": string }>;
  pipeline: ScalarMap;
};

function path(table: DataSyncTablePath) {
  return [table.database, table.schema, table.table].filter(Boolean).join(".");
}

function tablePath(value: string, namespaceKind: string): DataSyncTablePath {
  const segments = value.split(".");
  if (segments.length < 1 || segments.length > 3 || segments.some((segment) => !segment))
    throw new Error("Invalid V6 table path");
  const table = segments.pop()!;
  if (segments.length === 2) return { database: segments[0], schema: segments[1], table };
  if (segments.length === 1)
    return namespaceKind === "DATABASE"
      ? { database: segments[0], table }
      : { schema: segments[0], table };
  return { table };
}

function required(value: ScalarMap, key: string): string {
  const entry = value[key];
  if (typeof entry !== "string" || !entry.trim())
    throw new Error("Missing V6 Pipeline option: " + key);
  return entry;
}

function optional(value: ScalarMap, key: string, defaultValue: string): string {
  const entry = value[key];
  return entry === undefined ? defaultValue : String(entry);
}

function positiveNumber(value: ScalarMap, key: string): number | undefined {
  if (value[key] === undefined) return undefined;
  const parsed = Number(value[key]);
  if (!Number.isSafeInteger(parsed) || parsed < 0) throw new Error("Invalid V6 option: " + key);
  return parsed;
}

export function encodePipelineYaml(value: DataSyncParameters): PipelineYamlParameters {
  const source: ScalarMap = {
    type: value.source.type === "MYSQL_CDC" ? "mysql" : "jdbc",
    "datasource-id": value.source.datasourceId,
    tables: value.source.tables.map(path).join(","),
    "yak.namespace-kind": value.source.tables.some((table) => Boolean(table.database))
      ? "DATABASE"
      : "SCHEMA",
  };
  if (value.source.type === "MYSQL_CDC") {
    const startup = value.source.startup;
    const modes: Record<string, string> = {
      INITIAL: "initial",
      SNAPSHOT: "snapshot",
      EARLIEST_OFFSET: "earliest-offset",
      LATEST_OFFSET: "latest-offset",
      SPECIFIC_OFFSETS: "specific-offset",
      TIMESTAMP: "timestamp",
    };
    source["scan.startup.mode"] = modes[startup?.mode ?? "INITIAL"];
    if (startup?.file) source["scan.startup.specific-offset.file"] = startup.file;
    if (startup?.pos !== undefined)
      source["scan.startup.specific-offset.pos"] = String(startup.pos);
    if (startup?.gtidSet) source["scan.startup.specific-offset.gtid-set"] = startup.gtidSet;
    if (startup?.skipEvents !== undefined)
      source["scan.startup.specific-offset.skip-events"] = String(startup.skipEvents);
    if (startup?.skipRows !== undefined)
      source["scan.startup.specific-offset.skip-rows"] = String(startup.skipRows);
    if (startup?.timestampMillis !== undefined)
      source["scan.startup.timestamp-millis"] = String(startup.timestampMillis);
  } else {
    source["yak.sync.mode"] = value.source.syncMode ?? "FULL";
  }
  for (const table of value.source.tables) {
    const prefix = "yak.table." + path(table) + ".";
    if (table.snapshot?.chunkKeyColumn)
      source[prefix + "chunk-key-column"] = table.snapshot.chunkKeyColumn;
    if (table.incremental) {
      source[prefix + "incremental.mode"] = table.incremental.mode;
      source[prefix + "incremental.column"] = table.incremental.column;
      if (table.incremental.initialValue)
        source[prefix + "incremental.initial-value"] = table.incremental.initialValue;
      if (table.incremental.delaySeconds !== undefined)
        source[prefix + "incremental.delay-seconds"] = String(table.incremental.delaySeconds);
      if (table.incremental.lookbackSeconds !== undefined)
        source[prefix + "incremental.lookback-seconds"] = String(table.incremental.lookbackSeconds);
    }
  }
  const sink: ScalarMap = {
    type: "jdbc",
    "datasource-id": value.sink.datasourceId,
    "yak.namespace-kind": value.sink.tables.some((table) => Boolean(table.database))
      ? "DATABASE"
      : "SCHEMA",
    "yak.schema.initial": value.sink.schemaPolicy.initial,
    "yak.schema.evolution.enabled": String(value.sink.schemaPolicy.evolution.enabled),
    "yak.schema.evolution.allow-drop-columns": String(
      value.sink.schemaPolicy.evolution.allowDropColumns,
    ),
  };
  for (const table of value.sink.tables) {
    const prefix = "yak.table." + path(table) + ".";
    sink[prefix + "write.mode"] = table.write.mode;
    if (table.write.primaryKeys)
      sink[prefix + "write.primary-keys"] = table.write.primaryKeys.join(",");
    if (table.write.transaction)
      sink[prefix + "write.transaction.mode"] = table.write.transaction.mode;
  }
  const document: PipelineDocument = {
    source,
    sink,
    route: value.route,
    pipeline: {
      "execution.runtime-mode": value.pipeline["execution.runtime-mode"],
      "yak.table-scope":
        value.pipeline["pipeline.table-scope"] ??
        (value.source.tables.length > 1 ? "DATABASE" : "SINGLE"),
    },
  };
  return { pipelineYaml: stringify(document, { lineWidth: 0 }) };
}

/** Project a persisted V6 YAML definition into the existing visual editor state. */
export function decodePipelineYaml(content: unknown): DataSyncParameters {
  if (!content || typeof content !== "object" || Array.isArray(content))
    throw new Error("Invalid V6 Pipeline envelope");
  const envelope = content as Partial<PipelineYamlParameters>;
  if (typeof envelope.pipelineYaml !== "string") throw new Error("Missing V6 pipelineYaml");
  const parsed: unknown = parse(envelope.pipelineYaml, { uniqueKeys: true });
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
    throw new Error("Invalid V6 YAML document");
  const document = parsed as Partial<PipelineDocument>;
  if (
    !document.source ||
    !document.sink ||
    !Array.isArray(document.route) ||
    !document.pipeline ||
    document.source.type === undefined ||
    document.sink.type !== "jdbc"
  )
    throw new Error("Invalid V6 Pipeline shape");

  const source = document.source;
  const sink = document.sink;
  const realtime = source.type === "mysql";
  if (!realtime && source.type !== "jdbc") throw new Error("Unsupported V6 Source");
  const sourceTables = required(source, "tables")
    .split(",")
    .map((item) => item.trim());
  const sourceKind = optional(source, "yak.namespace-kind", realtime ? "DATABASE" : "SCHEMA");
  const sinkKind = optional(sink, "yak.namespace-kind", "SCHEMA");
  const sourceDefinitions = sourceTables.map((name) => {
    const table = tablePath(name, sourceKind);
    const prefix = "yak.table." + name + ".";
    const chunkKey = source[prefix + "chunk-key-column"];
    const mode = source[prefix + "incremental.mode"];
    return {
      ...table,
      ...(chunkKey ? { snapshot: { chunkKeyColumn: chunkKey } } : {}),
      ...(mode
        ? {
            incremental: {
              mode: mode as "TIMESTAMP" | "INCREASING",
              column: required(source, prefix + "incremental.column"),
              ...(source[prefix + "incremental.initial-value"]
                ? { initialValue: source[prefix + "incremental.initial-value"] }
                : {}),
              ...(source[prefix + "incremental.delay-seconds"] !== undefined
                ? { delaySeconds: positiveNumber(source, prefix + "incremental.delay-seconds") }
                : {}),
              ...(source[prefix + "incremental.lookback-seconds"] !== undefined
                ? {
                    lookbackSeconds: positiveNumber(
                      source,
                      prefix + "incremental.lookback-seconds",
                    ),
                  }
                : {}),
            },
          }
        : {}),
    };
  });
  const sinkDefinitions = document.route.map((route) => {
    const name = route["sink-table"];
    const prefix = "yak.table." + name + ".";
    const primaryKeys = sink[prefix + "write.primary-keys"];
    const transaction = sink[prefix + "write.transaction.mode"];
    return {
      ...tablePath(name, sinkKind),
      write: {
        mode: optional(sink, prefix + "write.mode", optional(sink, "write.mode", "UPSERT")) as
          | "APPEND"
          | "UPSERT"
          | "RETRACT",
        ...(primaryKeys ? { primaryKeys: primaryKeys.split(",") } : {}),
        ...(transaction ? { transaction: { mode: "CHECKPOINT_XA" as const } } : {}),
      },
    };
  });
  const startupMode: Record<string, DataSyncStartupOptions["mode"]> = {
    initial: "INITIAL",
    snapshot: "SNAPSHOT",
    "earliest-offset": "EARLIEST_OFFSET",
    "latest-offset": "LATEST_OFFSET",
    "specific-offset": "SPECIFIC_OFFSETS",
    timestamp: "TIMESTAMP",
  };
  const startup = optional(source, "scan.startup.mode", "initial");
  if (realtime && !startupMode[startup]) throw new Error("Unsupported V6 startup mode");
  const result: DataSyncParameters = {
    source: {
      type: realtime ? "MYSQL_CDC" : "JDBC",
      datasourceId: required(source, "datasource-id"),
      ...(realtime
        ? {
            startup: {
              mode: startupMode[startup],
              ...(source["scan.startup.specific-offset.file"] !== undefined
                ? { file: source["scan.startup.specific-offset.file"] }
                : {}),
              ...(source["scan.startup.specific-offset.gtid-set"] !== undefined
                ? { gtidSet: source["scan.startup.specific-offset.gtid-set"] }
                : {}),
              ...(positiveNumber(source, "scan.startup.specific-offset.pos") !== undefined
                ? { pos: positiveNumber(source, "scan.startup.specific-offset.pos") }
                : {}),
              ...(positiveNumber(source, "scan.startup.timestamp-millis") !== undefined
                ? { timestampMillis: positiveNumber(source, "scan.startup.timestamp-millis") }
                : {}),
              ...(positiveNumber(source, "scan.startup.specific-offset.skip-events") !== undefined
                ? { skipEvents: positiveNumber(source, "scan.startup.specific-offset.skip-events") }
                : {}),
              ...(positiveNumber(source, "scan.startup.specific-offset.skip-rows") !== undefined
                ? { skipRows: positiveNumber(source, "scan.startup.specific-offset.skip-rows") }
                : {}),
            },
          }
        : { syncMode: optional(source, "yak.sync.mode", "FULL") as "FULL" }),
      tables: sourceDefinitions,
    },
    sink: {
      type: "JDBC",
      datasourceId: required(sink, "datasource-id"),
      schemaPolicy: {
        initial: optional(sink, "yak.schema.initial", "VERIFY") as "VERIFY",
        evolution: {
          enabled: optional(sink, "yak.schema.evolution.enabled", "false") === "true",
          allowDropColumns:
            optional(sink, "yak.schema.evolution.allow-drop-columns", "false") === "true",
        },
      },
      tables: sinkDefinitions,
    },
    route: document.route,
    transform: [],
    pipeline: {
      "execution.runtime-mode": required(document.pipeline, "execution.runtime-mode") as
        | "BATCH"
        | "STREAMING",
      "pipeline.table-scope": optional(document.pipeline, "yak.table-scope", "SINGLE") as
        | "SINGLE"
        | "DATABASE",
    },
  };
  return result;
}
