package io.yak.ops.sync.runtime.config;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.RouteDef;
import io.yak.ops.sync.runtime.solution.SolutionType;
import io.yak.ops.sync.runtime.solution.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal, non-secret connector execution inputs derived from one Flink CDC 3.7 PipelineDef.
 *
 * <p>YAML keys are Flink CDC 3.7-style flat connector options. Only yak.* keys are
 * platform extensions; no legacy source/sink table arrays or startup translation is used.
 */
public final class PipelineExecutionPlan {
    public static final int MAX_TABLES = 100;
    private final SolutionType solutionType;
    private final List<TableConfig> tables;
    private final List<Step> steps;
    private final boolean schemaChangesEnabled;
    private final boolean allowDropColumns;

    private PipelineExecutionPlan(
            SolutionType solutionType,
            List<TableConfig> tables,
            List<Step> steps,
            boolean schemaChangesEnabled,
            boolean allowDropColumns) {
        this.solutionType = solutionType;
        this.tables = List.copyOf(tables);
        this.steps = List.copyOf(steps);
        this.schemaChangesEnabled = schemaChangesEnabled;
        this.allowDropColumns = allowDropColumns;
    }

    public static PipelineExecutionPlan from(PipelineDef definition) {
        Map<String, Object> from =
                new LinkedHashMap<>(definition.getSource().getConfig().toMap());
        Map<String, Object> to =
                new LinkedHashMap<>(definition.getSink().getConfig().toMap());
        String sourceType = definition.getSource().getType();
        String sinkType = definition.getSink().getType();
        boolean realtime = "mysql".equals(sourceType);
        if ((!realtime && !"jdbc".equals(sourceType)) || !"jdbc".equals(sinkType)) {
            throw new IllegalArgumentException("Unsupported V6 pipeline connector combination");
        }
        if (!definition.getUdfs().isEmpty() || !definition.getModels().isEmpty()) {
            throw new IllegalArgumentException("YakFlow does not support Pipeline UDF or AI model operators");
        }
        if (!definition.getTransforms().isEmpty()) {
            throw new IllegalArgumentException("YakFlow does not yet support Flink CDC transform operators");
        }
        String sourceId = required(from, "datasource-id");
        String sinkId = required(to, "datasource-id");
        String selection = required(from, "tables");
        List<String> sourceTables = new ArrayList<>();
        for (String table : selection.split(",", -1)) {
            String name = table.trim();
            if (name.isEmpty() || !name.matches("[A-Za-z0-9_$]+(\\.[A-Za-z0-9_$]+){0,2}")) {
                throw new IllegalArgumentException("YakFlow currently requires explicit source table names");
            }
            sourceTables.add(name);
        }
        if (sourceTables.isEmpty()
                || sourceTables.size() > MAX_TABLES
                || new HashSet<>(sourceTables).size() != sourceTables.size()) {
            throw new IllegalArgumentException("Invalid or duplicate source table selection");
        }
        List<RouteDef> routes = definition.getRoute().isEmpty() ? defaultRoutes(sourceTables) : definition.getRoute();
        if (routes.size() != sourceTables.size()) {
            throw new IllegalArgumentException("YakFlow currently requires a one-to-one route per source table");
        }
        Set<String> mappedSource = new HashSet<>();
        Set<String> mappedSink = new HashSet<>();
        for (RouteDef route : routes) {
            if (route.getReplaceSymbol().isPresent()
                    || !sourceTables.contains(route.getSourceTable())
                    || !mappedSource.add(route.getSourceTable())
                    || !mappedSink.add(route.getSinkTable())) {
                throw new IllegalArgumentException("Unsupported, duplicate or non-explicit route");
            }
        }

        Map<String, Object> pipelineOptions =
                new LinkedHashMap<>(definition.getConfig().toMap());
        // The upstream parser recognizes more options than YakFlow can currently execute.
        // Never accept an option and silently discard its execution semantics.
        for (String key : pipelineOptions.keySet()) {
            if (!"execution.runtime-mode".equals(key)
                    && !"local-time-zone".equals(key)
                    && !"yak.table-scope".equals(key)) {
                throw new IllegalArgumentException("Unsupported YakFlow Pipeline option: " + key);
            }
        }
        String localTimeZone = optional(pipelineOptions, "local-time-zone", null);
        if (localTimeZone != null
                && !localTimeZone.equals(java.time.ZoneId.systemDefault().toString())) {
            throw new IllegalArgumentException("YakFlow does not yet support custom pipeline time zones");
        }

        String scope = optional(pipelineOptions, "yak.table-scope", sourceTables.size() == 1 ? "SINGLE" : "DATABASE");
        if (!"SINGLE".equals(scope) && !"DATABASE".equals(scope)) {
            throw new IllegalArgumentException("Invalid yak.table-scope");
        }
        if ("SINGLE".equals(scope) && sourceTables.size() != 1) {
            throw new IllegalArgumentException("SINGLE requires exactly one table");
        }
        SolutionType solution = "SINGLE".equals(scope)
                ? (realtime ? SolutionType.SINGLE_TABLE_REALTIME : SolutionType.SINGLE_TABLE_OFFLINE)
                : (realtime ? SolutionType.DATABASE_REALTIME : SolutionType.DATABASE_OFFLINE);

        List<Step> dataSteps;
        if (realtime) {
            String startup = optional(from, MySqlSourceOptions.SCAN_STARTUP_MODE.key(), "initial");
            long timestampMillis = numeric(from, MySqlSourceOptions.SCAN_STARTUP_TIMESTAMP_MILLIS.key(), 0L);
            StartupOptions.from(
                    startup,
                    optional(from, MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_FILE.key(), null),
                    nullableLong(from, MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_POS.key()),
                    optional(from, MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_GTID_SET.key(), null),
                    timestampMillis == 0 ? null : timestampMillis,
                    nullableLong(from, MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_EVENTS.key()),
                    nullableLong(from, MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_ROWS.key()));
            dataSteps = switch (startup) {
                case "initial" -> List.of(Step.FULL, Step.INCREMENTAL);
                case "snapshot" -> List.of(Step.FULL);
                case "earliest-offset", "latest-offset", "specific-offset", "timestamp" -> List.of(Step.INCREMENTAL);
                default -> throw new IllegalArgumentException("Unsupported Flink CDC startup mode");
            };
        } else {
            String syncMode = optional(from, "yak.sync.mode", "FULL");
            dataSteps = switch (syncMode) {
                case "FULL" -> List.of(Step.FULL);
                case "INCREMENTAL" -> List.of(Step.INCREMENTAL);
                case "FULL_AND_INCREMENTAL" -> List.of(Step.FULL, Step.INCREMENTAL);
                default -> throw new IllegalArgumentException("Unsupported JDBC sync mode");
            };
        }

        String expectedRuntime = realtime && dataSteps.contains(Step.INCREMENTAL) ? "STREAMING" : "BATCH";
        String runtime = optional(pipelineOptions, "execution.runtime-mode", expectedRuntime);
        if (!expectedRuntime.equals(runtime)) {
            throw new IllegalArgumentException("Pipeline runtime mode conflicts with source mode");
        }
        String initial = optional(to, "yak.schema.initial", "VERIFY");
        if (!"VERIFY".equals(initial) && !"CREATE_OR_ALTER".equals(initial)) {
            throw new IllegalArgumentException("Unsupported target schema preparation");
        }
        boolean evolve = flag(to, "yak.schema.evolution.enabled", false);
        boolean drop = flag(to, "yak.schema.evolution.allow-drop-columns", false);
        if (drop && !evolve) {
            throw new IllegalArgumentException("DROP COLUMN requires Schema Evolution");
        }
        if (evolve && (!realtime || !dataSteps.contains(Step.INCREMENTAL))) {
            throw new IllegalArgumentException("Schema Evolution requires continuous CDC");
        }
        if ("CREATE_OR_ALTER".equals(initial) && !dataSteps.contains(Step.FULL)) {
            throw new IllegalArgumentException("Initial schema preparation requires FULL");
        }
        List<Step> stages = new ArrayList<>();
        if ("CREATE_OR_ALTER".equals(initial)) {
            stages.add(Step.SCHEMA);
        }
        stages.addAll(dataSteps);

        List<TableConfig> mappings = new ArrayList<>();
        for (RouteDef route : routes) {
            String sourceTable = route.getSourceTable();
            String sinkTable = route.getSinkTable();
            Map<String, Object> sourceOptions = new LinkedHashMap<>();
            copyConnectorOptions(from, sourceOptions, "scan.");
            sourceOptions.put(JobOptions.TYPE.key(), realtime ? "MYSQL_CDC" : "JDBC");
            sourceOptions.put(JobOptions.DATASOURCE_ID.key(), sourceId);
            addTablePath(
                    sourceOptions, sourceTable, optional(from, "yak.namespace-kind", realtime ? "DATABASE" : "SCHEMA"));
            String sourcePrefix = "yak.table." + sourceTable + ".";
            String chunkKey = optional(
                    from,
                    sourcePrefix + "chunk-key-column",
                    optional(from, MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(), null));
            if (chunkKey != null) {
                if (!realtime || !dataSteps.contains(Step.FULL)) {
                    throw new IllegalArgumentException("Snapshot Chunk Key requires initial CDC snapshot");
                }
                sourceOptions.put(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(), chunkKey);
            }
            IncrementalConfig incremental =
                    incremental(from, sourcePrefix, !realtime && dataSteps.contains(Step.INCREMENTAL));

            Map<String, Object> sinkOptions = new LinkedHashMap<>();
            copyConnectorOptions(to, sinkOptions, "connector.jdbc.sink.");
            sinkOptions.put(JobOptions.TYPE.key(), "JDBC");
            sinkOptions.put(JobOptions.DATASOURCE_ID.key(), sinkId);
            addTablePath(sinkOptions, sinkTable, optional(to, "yak.namespace-kind", "SCHEMA"));
            String sinkPrefix = "yak.table." + sinkTable + ".";
            String write = optional(to, sinkPrefix + "write.mode", optional(to, "write.mode", "UPSERT"));
            JdbcWriteMode mode = JdbcWriteMode.valueOf(write);
            sinkOptions.put(JdbcSinkOptions.WRITE_MODE.key(), mode.name());
            List<String> targetPrimaryKeys = csv(optional(to, sinkPrefix + "write.primary-keys", null));
            String transaction = optional(to, sinkPrefix + "write.transaction.mode", null);
            boolean xa = "CHECKPOINT_XA".equals(transaction);
            if (transaction != null && !xa) {
                throw new IllegalArgumentException("Unsupported transaction mode");
            }
            if (xa) {
                sinkOptions.put(JdbcSinkOptions.XA_ENABLED.key(), true);
            }
            if (targetPrimaryKeys != null
                    && (targetPrimaryKeys.isEmpty()
                            || evolve
                            || new HashSet<>(targetPrimaryKeys).size() != targetPrimaryKeys.size())) {
                throw new IllegalArgumentException("Invalid target primary key override");
            }
            if (mode == JdbcWriteMode.RETRACT
                    && (!xa
                            || targetPrimaryKeys != null
                            || solution != SolutionType.SINGLE_TABLE_REALTIME
                            || !dataSteps.contains(Step.INCREMENTAL)
                            || evolve)) {
                throw new IllegalArgumentException("RETRACT requires single-table realtime XA");
            }
            if (xa && mode != JdbcWriteMode.RETRACT) {
                throw new IllegalArgumentException("Checkpoint XA requires RETRACT");
            }
            if ((realtime || dataSteps.contains(Step.INCREMENTAL))
                    && mode != JdbcWriteMode.UPSERT
                    && mode != JdbcWriteMode.RETRACT) {
                throw new IllegalArgumentException("Incremental pipelines require UPSERT");
            }
            mappings.add(new TableConfig(
                    ReadonlyConfig.fromMap(sourceOptions),
                    ReadonlyConfig.fromMap(sinkOptions),
                    incremental,
                    targetPrimaryKeys));
        }
        return new PipelineExecutionPlan(solution, mappings, stages, evolve, drop);
    }

    private static void copyConnectorOptions(Map<String, Object> source, Map<String, Object> target, String prefix) {
        source.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                target.put(key, value);
            }
        });
    }

    private static void addTablePath(Map<String, Object> target, String value, String namespaceKind) {
        String[] parts = value.split("\\.", -1);
        if (parts.length < 1 || parts.length > 3) {
            throw new IllegalArgumentException("Table identifier must have at most three parts");
        }
        if (parts.length == 3) {
            target.put(JobOptions.DATABASE.key(), parts[0]);
            target.put(JobOptions.SCHEMA.key(), parts[1]);
        } else if (parts.length == 2) {
            if ("DATABASE".equals(namespaceKind)) {
                target.put(JobOptions.DATABASE.key(), parts[0]);
            } else if ("SCHEMA".equals(namespaceKind)) {
                target.put(JobOptions.SCHEMA.key(), parts[0]);
            } else {
                throw new IllegalArgumentException("Invalid namespace kind");
            }
        }
        target.put(JobOptions.TABLE.key(), parts[parts.length - 1]);
    }

    private static IncrementalConfig incremental(Map<String, Object> options, String prefix, boolean enabled) {
        String mode = optional(options, prefix + "incremental.mode", null);
        if (!enabled) {
            if (mode != null) {
                throw new IllegalArgumentException("Incremental options require offline incremental");
            }
            return null;
        }
        if (mode == null) {
            throw new IllegalArgumentException("Offline incremental requires table incremental options");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mode", mode);
        values.put("column", required(options, prefix + "incremental.column"));
        putIfPresent(options, values, prefix + "incremental.initial-value", "initialValue");
        if (options.containsKey(prefix + "incremental.delay-seconds")) {
            values.put("delaySeconds", numeric(options, prefix + "incremental.delay-seconds", 0));
        }
        if (options.containsKey(prefix + "incremental.lookback-seconds")) {
            values.put("lookbackSeconds", numeric(options, prefix + "incremental.lookback-seconds", 0));
        }
        return IncrementalConfig.from(values);
    }

    private static void putIfPresent(
            Map<String, Object> source, Map<String, Object> target, String fromKey, String toKey) {
        if (source.containsKey(fromKey)) {
            target.put(toKey, source.get(fromKey));
        }
    }

    private static List<RouteDef> defaultRoutes(List<String> tables) {
        List<RouteDef> routes = new ArrayList<>();
        for (String table : tables) {
            routes.add(new RouteDef(table, table, null, null));
        }
        return routes;
    }

    private static List<String> csv(String value) {
        if (value == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String key : value.split(",", -1)) {
            if (key.isBlank()) {
                throw new IllegalArgumentException("Invalid comma-separated option");
            }
            result.add(key.trim());
        }
        return result;
    }

    private static String required(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Required Pipeline option missing: " + key);
        }
        return text;
    }

    private static String optional(Map<String, Object> options, String key, String fallback) {
        Object value = options.get(key);
        return value == null ? fallback : (String) value;
    }

    private static boolean flag(Map<String, Object> options, String key, boolean fallback) {
        String value = optional(options, key, null);
        if (value == null) {
            return fallback;
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Expected boolean option: " + key);
        }
        return Boolean.parseBoolean(value);
    }

    private static Long nullableLong(Map<String, Object> options, String key) {
        String value = optional(options, key, null);
        return value == null ? null : Long.parseLong(value);
    }

    private static long numeric(Map<String, Object> options, String key, long fallback) {
        Long value = nullableLong(options, key);
        return value == null ? fallback : value;
    }

    public List<TableConfig> tables() {
        return tables;
    }

    public String sourceDataSourceId() {
        return tables.getFirst().source().get(JobOptions.DATASOURCE_ID);
    }

    public String targetDataSourceId() {
        return tables.getFirst().sink().get(JobOptions.DATASOURCE_ID);
    }

    public List<Step> steps() {
        return steps;
    }

    public SolutionType solutionType() {
        return solutionType;
    }

    public boolean schemaChangesEnabled() {
        return schemaChangesEnabled;
    }

    public boolean allowDropColumns() {
        return allowDropColumns;
    }
}
