package io.yak.ops.composer.definition;

import io.yak.ops.composer.execution.ExecutionPhase;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.flink.cdc.common.event.SchemaChangeEventType;
import org.apache.flink.cdc.common.pipeline.ExistingTableSchemaExpansionMode;
import org.apache.flink.cdc.common.route.RouteRule;
import org.apache.flink.cdc.common.route.TableIdRouter;

/**
 * Validated, non-secret endpoint bindings derived from a Pipeline definition.
 *
 * <p>YAML keys are Flink CDC 3.7-style flat connector options. Only yak.* keys are
 * platform extensions; no legacy source/sink table arrays or startup translation is used.
 */
public final class PipelineOptions {
    public static final int MAX_TABLES = 100;
    private final String sourceIdentifier;
    private final String sinkIdentifier;
    private final String runtimeMode;
    private final String tableScope;
    private final List<TableConfig> tables;
    private final List<ExecutionPhase> steps;
    private final boolean schemaChangesEnabled;
    private final boolean allowDropColumns;
    private final DynamicTableSelection dynamicSelection;

    private PipelineOptions(
            String sourceIdentifier,
            String sinkIdentifier,
            String runtimeMode,
            String tableScope,
            List<TableConfig> tables,
            List<ExecutionPhase> steps,
            boolean schemaChangesEnabled,
            boolean allowDropColumns,
            DynamicTableSelection dynamicSelection) {
        this.sourceIdentifier = sourceIdentifier;
        this.sinkIdentifier = sinkIdentifier;
        this.runtimeMode = runtimeMode;
        this.tableScope = tableScope;
        this.tables = List.copyOf(tables);
        this.steps = List.copyOf(steps);
        this.schemaChangesEnabled = schemaChangesEnabled;
        this.allowDropColumns = allowDropColumns;
        this.dynamicSelection = dynamicSelection;
    }

    public static PipelineOptions from(PipelineDef definition) {
        Map<String, Object> from =
                new LinkedHashMap<>(definition.getSource().getConfig().toMap());
        Map<String, Object> to =
                new LinkedHashMap<>(definition.getSink().getConfig().toMap());
        String sourceType = definition.getSource().getType();
        String sinkType = definition.getSink().getType();
        String runtime = definition
                .getConfig()
                .get(org.apache.flink.cdc.common.pipeline.PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE)
                .name();
        boolean realtime = "STREAMING".equals(runtime) || from.containsKey("scan.startup.mode");
        if (!definition.getUdfs().isEmpty() || !definition.getModels().isEmpty()) {
            throw new IllegalArgumentException("YakFlow does not support Pipeline UDF or AI model operators");
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
        TableIdRouter router = new TableIdRouter(
                definition.getRoute().stream()
                        .map(route -> new RouteRule(
                                route.getSourceTable(),
                                route.getSinkTable(),
                                route.getReplaceSymbol().orElse(null)))
                        .toList(),
                definition.getRouteMode());
        List<RouteDef> routes = new ArrayList<>();
        Set<String> mappedSink = new HashSet<>();
        for (String sourceTable : sourceTables) {
            List<org.apache.flink.cdc.common.event.TableId> destinations =
                    router.route(org.apache.flink.cdc.common.event.TableId.parse(sourceTable));
            if (destinations.size() != 1) {
                throw new UnsupportedOperationException(
                        "YakFlow requires exactly one admitted destination per source table");
            }
            String target = destinations.getFirst().identifier();
            if (!mappedSink.add(target)) {
                throw new UnsupportedOperationException(
                        "Merging source table schemas into one target is not supported by the selected metadata appliers");
            }
            routes.add(new RouteDef(sourceTable, target, null, null));
        }
        validateExtensionKeys(
                from,
                Set.of(
                        "yak.namespace-kind",
                        "yak.sync.mode",
                        "yak.selection.mode",
                        "yak.selection.database-pattern",
                        "yak.selection.table-pattern",
                        "yak.selection.snapshot.chunk-key-column",
                        "yak.selection.snapshot.skip",
                        "yak.selection.snapshot.where",
                        "yak.selection.dml.insert",
                        "yak.selection.dml.update",
                        "yak.selection.dml.delete"),
                sourceTables,
                Set.of(
                        "chunk-key-column",
                        "snapshot.skip",
                        "snapshot.where",
                        "dml.insert",
                        "dml.update",
                        "dml.delete",
                        "read.where",
                        "read.split-key",
                        "read.fetch-size",
                        "read.query-timeout-seconds",
                        "incremental.mode",
                        "incremental.column",
                        "incremental.initial-value",
                        "incremental.delay-seconds",
                        "incremental.lookback-seconds"),
                "source");
        validateExtensionKeys(
                to,
                Set.of(
                        "yak.namespace-kind",
                        "yak.schema.initial",
                        "yak.schema.evolution.enabled",
                        "yak.schema.evolution.allow-drop-columns",
                        "yak.type-mappings",
                        "yak.mapping.database",
                        "yak.mapping.schema",
                        "yak.mapping.table",
                        "yak.mapping.write.mode",
                        "yak.mapping.write.primary-keys",
                        "yak.mapping.write.transaction.mode",
                        "yak.mapping.write.batch-size"),
                mappedSink,
                Set.of(
                        "write.mode",
                        "write.primary-keys",
                        "write.transaction.mode",
                        "write.pre-sql",
                        "write.post-sql",
                        "write.batch-size"),
                "sink");
        validateNamespaceKind(from);
        validateNamespaceKind(to);
        if (realtime && from.containsKey("yak.sync.mode")) {
            throw new IllegalArgumentException(
                    "JDBC sync mode cannot be combined with streaming or CDC startup options");
        }

        Map<String, Object> pipelineOptions =
                new LinkedHashMap<>(definition.getConfig().toMap());
        Set<String> supportedPipelineOptions = Set.of(
                "execution.runtime-mode",
                "local-time-zone",
                "yak.table-scope",
                "name",
                "parallelism",
                "schema.change.behavior",
                "schema.operator.uid",
                "route-mode",
                "transform.decimal.precision.mode");
        for (String key : pipelineOptions.keySet()) {
            if (!supportedPipelineOptions.contains(key)) {
                throw new IllegalArgumentException("Unsupported YakFlow Pipeline option: " + key);
            }
        }
        long parallelism = numeric(pipelineOptions, "parallelism", 1L);
        if (parallelism < 1 || parallelism > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Pipeline parallelism must be a positive integer");
        }
        String scope = optional(pipelineOptions, "yak.table-scope", sourceTables.size() == 1 ? "SINGLE" : "DATABASE");
        if (!"SINGLE".equals(scope) && !"DATABASE".equals(scope)) {
            throw new IllegalArgumentException("Invalid yak.table-scope");
        }
        if ("SINGLE".equals(scope) && sourceTables.size() != 1) {
            throw new IllegalArgumentException("SINGLE requires exactly one table");
        }
        List<ExecutionPhase> dataSteps;
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
                case "initial" -> List.of(ExecutionPhase.FULL, ExecutionPhase.INCREMENTAL);
                case "snapshot" -> List.of(ExecutionPhase.FULL);
                case "earliest-offset", "latest-offset", "specific-offset", "timestamp" ->
                    List.of(ExecutionPhase.INCREMENTAL);
                default -> throw new IllegalArgumentException("Unsupported Flink CDC startup mode");
            };
        } else {
            String syncMode = optional(from, "yak.sync.mode", "FULL");
            dataSteps = switch (syncMode) {
                case "FULL" -> List.of(ExecutionPhase.FULL);
                case "INCREMENTAL" -> List.of(ExecutionPhase.INCREMENTAL);
                case "FULL_AND_INCREMENTAL" -> List.of(ExecutionPhase.FULL, ExecutionPhase.INCREMENTAL);
                default -> throw new IllegalArgumentException("Unsupported JDBC sync mode");
            };
        }

        String initial = optional(to, "yak.schema.initial", "VERIFY");
        if (!"VERIFY".equals(initial) && !"CREATE_OR_ALTER".equals(initial)) {
            throw new IllegalArgumentException("Unsupported target schema preparation");
        }
        String schemaBehavior = optional(pipelineOptions, "schema.change.behavior", null);
        if (schemaBehavior != null
                && !Set.of("evolve", "exception").contains(schemaBehavior.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedOperationException("YakFlow supports evolve and exception schema behavior");
        }
        boolean evolve = flag(to, "yak.schema.evolution.enabled", "evolve".equalsIgnoreCase(schemaBehavior));
        if (schemaBehavior != null && evolve != "evolve".equalsIgnoreCase(schemaBehavior)) {
            throw new IllegalArgumentException("Schema behavior conflicts with target evolution configuration");
        }
        if (definition.getSink().getExistingTableSchemaExpansionMode() != ExistingTableSchemaExpansionMode.DISABLED) {
            throw new UnsupportedOperationException(
                    "The selected metadata appliers do not support existing table schema expansion");
        }
        java.util.Set<SchemaChangeEventType> requiredEvents = EnumSet.of(SchemaChangeEventType.CREATE_TABLE);
        if (evolve)
            requiredEvents.addAll(
                    java.util.Set.of(SchemaChangeEventType.ADD_COLUMN, SchemaChangeEventType.ALTER_COLUMN_TYPE));
        if (!definition.getSink().getIncludedSchemaEvolutionTypes().containsAll(requiredEvents)) {
            throw new UnsupportedOperationException(
                    "The selected metadata appliers require the configured schema evolution events");
        }
        boolean drop = flag(to, "yak.schema.evolution.allow-drop-columns", false);
        if (drop
                && !definition
                        .getSink()
                        .getIncludedSchemaEvolutionTypes()
                        .contains(SchemaChangeEventType.DROP_COLUMN)) {
            throw new IllegalArgumentException("DROP COLUMN permission conflicts with excluded schema events");
        }
        if (drop && !evolve) {
            throw new IllegalArgumentException("DROP COLUMN requires Schema Evolution");
        }
        if (evolve && !"STREAMING".equals(runtime)) {
            throw new IllegalArgumentException("Schema Evolution requires continuous CDC");
        }
        if ("CREATE_OR_ALTER".equals(initial) && !dataSteps.contains(ExecutionPhase.FULL)) {
            throw new IllegalArgumentException("Initial schema preparation requires FULL");
        }
        List<ExecutionPhase> stages = new ArrayList<>();
        if ("CREATE_OR_ALTER".equals(initial)) {
            stages.add(ExecutionPhase.SCHEMA);
        }
        stages.addAll(dataSteps);
        DynamicTableSelection dynamic = DynamicTableSelection.from(definition);
        if (dynamic != null
                && (!"STREAMING".equals(runtime)
                        || !"DATABASE".equals(scope)
                        || !"initial".equals(optional(from, "scan.startup.mode", "initial"))
                        || !"CREATE_OR_ALTER".equals(initial)
                        || !evolve)) {
            throw new IllegalArgumentException(
                    "REGEX selection requires database streaming, INITIAL startup, CREATE_OR_ALTER and schema evolution");
        }
        if (dynamic != null && sourceId.equals(sinkId)) {
            throw new IllegalArgumentException("Dynamic source and target must use distinct datasources");
        }

        List<TableConfig> mappings = new ArrayList<>();
        for (RouteDef route : routes) {
            String sourceTable = route.getSourceTable();
            String sinkTable = route.getSinkTable();
            Map<String, Object> sourceOptions = new LinkedHashMap<>();
            copyConnectorOptions(from, sourceOptions, Set.of("datasource-id", "tables"));
            sourceOptions.put(EndpointOptions.TYPE.key(), sourceType);
            sourceOptions.put(EndpointOptions.DATASOURCE_ID.key(), sourceId);
            addTablePath(
                    sourceOptions, sourceTable, optional(from, "yak.namespace-kind", realtime ? "DATABASE" : "SCHEMA"));
            String sourcePrefix = "yak.table." + sourceTable + ".";
            if (dynamic != null) {
                sourceOptions.putAll(dynamic.sourceDefaults());
                sourceOptions.put(MySqlSourceOptions.SCAN_NEWLY_ADDED_TABLE_ENABLED.key(), true);
                sourceOptions.put(MySqlSourceOptions.DATABASE_NAME.key(), dynamic.databasePattern());
                sourceOptions.put(MySqlSourceOptions.TABLE_NAME.key(), dynamic.tablePattern());
                try {
                    sourceOptions.put(
                            MySqlSourceOptions.DISCOVERY_DEFAULTS.key(),
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(dynamic.sourceDefaults()));
                } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                    throw new IllegalArgumentException("Cannot encode discovery defaults", failure);
                }
                if (!dynamic.matches(EndpointOptions.selectedTable(ReadonlyConfig.fromMap(sourceOptions)))) {
                    throw new IllegalArgumentException("Initial source table does not match the REGEX selection");
                }
            }
            String chunkKey = optional(
                    from,
                    sourcePrefix + "chunk-key-column",
                    optional(from, MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(), (String)
                            sourceOptions.get(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key())));
            if (chunkKey != null) {
                if (!realtime || !dataSteps.contains(ExecutionPhase.FULL)) {
                    throw new IllegalArgumentException("Snapshot Chunk Key requires initial CDC snapshot");
                }
                sourceOptions.put(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(), chunkKey);
            }
            IncrementalConfig incremental =
                    incremental(from, sourcePrefix, !realtime && dataSteps.contains(ExecutionPhase.INCREMENTAL));
            copyReadOptions(from, sourceOptions, sourcePrefix, realtime, dataSteps.contains(ExecutionPhase.FULL));

            Map<String, Object> sinkOptions = new LinkedHashMap<>();
            copyConnectorOptions(
                    to,
                    sinkOptions,
                    Set.of("datasource-id", "write.mode", "write.transaction.mode", "write.primary-keys"));
            sinkOptions.put(EndpointOptions.TYPE.key(), sinkType);
            sinkOptions.put(EndpointOptions.DATASOURCE_ID.key(), sinkId);
            addTablePath(sinkOptions, sinkTable, optional(to, "yak.namespace-kind", "SCHEMA"));
            String sinkPrefix = "yak.table." + sinkTable + ".";
            String write = optional(to, sinkPrefix + "write.mode", optional(to, "write.mode", "UPSERT"));
            JdbcWriteMode mode = JdbcWriteMode.valueOf(write);
            if (to.containsKey(sinkPrefix + "write.mode") || to.containsKey("write.mode")) {
                sinkOptions.put(JdbcSinkOptions.WRITE_MODE.key(), mode.name());
            }
            List<String> targetPrimaryKeys =
                    csv(optional(to, sinkPrefix + "write.primary-keys", optional(to, "write.primary-keys", null)));
            String transaction =
                    optional(to, sinkPrefix + "write.transaction.mode", optional(to, "write.transaction.mode", null));
            boolean xa = "CHECKPOINT_XA".equals(transaction);
            if (transaction != null && !xa) {
                throw new IllegalArgumentException("Unsupported transaction mode");
            }
            for (String hook : List.of("write.pre-sql", "write.post-sql")) {
                if (to.containsKey(sinkPrefix + hook)) {
                    if (realtime || !"BATCH".equals(runtime) || !sourceType.startsWith("jdbc-")) {
                        throw new IllegalArgumentException("SQL hooks require bounded JDBC batch execution");
                    }
                    putIfPresent(to, sinkOptions, sinkPrefix + hook, hook);
                }
            }
            if (to.containsKey(sinkPrefix + "write.batch-size")) {
                long size = numeric(to, sinkPrefix + "write.batch-size", 0);
                if (size < 1 || size > 10000) throw new IllegalArgumentException("Invalid write batch size");
                sinkOptions.put("write.batch-size", size);
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
                            || !("SINGLE".equals(scope) && "STREAMING".equals(runtime))
                            || !dataSteps.contains(ExecutionPhase.INCREMENTAL)
                            || evolve)) {
                throw new IllegalArgumentException("RETRACT requires single-table realtime XA");
            }
            if (xa && mode != JdbcWriteMode.RETRACT) {
                throw new IllegalArgumentException("Checkpoint XA requires RETRACT");
            }
            if (dynamic != null && (xa || mode != JdbcWriteMode.UPSERT)) {
                throw new IllegalArgumentException("Dynamic initial tables require UPSERT without checkpoint XA");
            }
            if (mode == JdbcWriteMode.REPLACE
                    && (realtime
                            || !"BATCH".equals(runtime)
                            || !sourceType.startsWith("jdbc-")
                            || !"jdbc-mysql".equals(sinkType)
                            || !dataSteps.equals(List.of(ExecutionPhase.FULL)))) {
                throw new IllegalArgumentException("REPLACE requires a JDBC full batch with a MySQL target");
            }
            if (("STREAMING".equals(runtime) || dataSteps.contains(ExecutionPhase.INCREMENTAL))
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
        TypeMappingRules.from(definition);
        return new PipelineOptions(sourceType, sinkType, runtime, scope, mappings, stages, evolve, drop, dynamic);
    }

    private static void copyReadOptions(
            Map<String, Object> from, Map<String, Object> target, String prefix, boolean cdc, boolean full) {
        for (String name : List.of("read.where", "read.split-key", "read.fetch-size", "read.query-timeout-seconds")) {
            if (!from.containsKey(prefix + name)) continue;
            if (cdc) throw new IllegalArgumentException("JDBC read options cannot configure CDC snapshot reads");
            String destination =
                    switch (name) {
                        case "read.fetch-size" -> "connector.jdbc.source.result-set-fetch-size";
                        case "read.query-timeout-seconds" -> "connector.jdbc.source.query-timeout-seconds";
                        default -> name;
                    };
            Object value = from.get(prefix + name);
            if (name.equals("read.where")) value = JdbcReadQuery.validateWhere((String) value);
            if (name.equals("read.fetch-size") || name.equals("read.query-timeout-seconds")) {
                long number = numeric(from, prefix + name, 0);
                long maximum = name.equals("read.fetch-size") ? 10000 : 300;
                if (number < 1 || number > maximum) throw new IllegalArgumentException("Invalid table read limit");
            }
            target.put(destination, value);
        }
        for (String name : List.of("snapshot.skip", "snapshot.where", "dml.insert", "dml.update", "dml.delete")) {
            if (!from.containsKey(prefix + name)) continue;
            if (!cdc) throw new IllegalArgumentException("CDC table options require a CDC source");
            if (name.startsWith("snapshot.") && !full) {
                throw new IllegalArgumentException("Snapshot options require an initial full phase");
            }
            target.put(name, from.get(prefix + name));
        }
        if (cdc) MySqlTableOptions.from(ReadonlyConfig.fromMap(target));
    }

    private static void validateExtensionKeys(
            Map<String, Object> options,
            Set<String> globals,
            Iterable<String> tables,
            Set<String> tableOptions,
            String endpoint) {
        Set<String> supported = new HashSet<>(globals);
        for (String table : tables) {
            for (String option : tableOptions) supported.add("yak.table." + table + "." + option);
        }
        for (String option : options.keySet()) {
            if (option.startsWith("yak.") && !supported.contains(option)) {
                throw new IllegalArgumentException("Unsupported or unselected " + endpoint + " option: " + option);
            }
        }
    }

    private static void validateNamespaceKind(Map<String, Object> options) {
        String kind = optional(options, "yak.namespace-kind", null);
        if (kind != null && !Set.of("DATABASE", "SCHEMA").contains(kind)) {
            throw new IllegalArgumentException("Invalid yak.namespace-kind");
        }
    }

    private static void copyConnectorOptions(
            Map<String, Object> source, Map<String, Object> target, Set<String> consumed) {
        source.forEach((key, value) -> {
            if (!key.startsWith("yak.") && !consumed.contains(key)) {
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
            target.put(EndpointOptions.DATABASE.key(), parts[0]);
            target.put(EndpointOptions.SCHEMA.key(), parts[1]);
        } else if (parts.length == 2) {
            if ("DATABASE".equals(namespaceKind)) {
                target.put(EndpointOptions.DATABASE.key(), parts[0]);
            } else if ("SCHEMA".equals(namespaceKind)) {
                target.put(EndpointOptions.SCHEMA.key(), parts[0]);
            } else {
                throw new IllegalArgumentException("Invalid namespace kind");
            }
        }
        target.put(EndpointOptions.TABLE.key(), parts[parts.length - 1]);
    }

    private static IncrementalConfig incremental(Map<String, Object> options, String prefix, boolean enabled) {
        String mode = optional(options, prefix + "incremental.mode", null);
        if (!enabled) {
            if (options.keySet().stream().anyMatch(key -> key.startsWith(prefix + "incremental."))) {
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
        return tables.getFirst().source().get(EndpointOptions.DATASOURCE_ID);
    }

    public String targetDataSourceId() {
        return tables.getFirst().sink().get(EndpointOptions.DATASOURCE_ID);
    }

    public List<ExecutionPhase> steps() {
        return steps;
    }

    public String sourceIdentifier() {
        return sourceIdentifier;
    }

    public String sinkIdentifier() {
        return sinkIdentifier;
    }

    public String runtimeMode() {
        return runtimeMode;
    }

    public String tableScope() {
        return tableScope;
    }

    public boolean bounded() {
        return "BATCH".equals(runtimeMode);
    }

    public boolean prepareSchema() {
        return steps.contains(ExecutionPhase.SCHEMA);
    }

    public boolean incremental() {
        return steps.contains(ExecutionPhase.INCREMENTAL);
    }

    public boolean initialLoad() {
        return steps.contains(ExecutionPhase.FULL);
    }

    public boolean schemaChangesEnabled() {
        return schemaChangesEnabled;
    }

    public boolean allowDropColumns() {
        return allowDropColumns;
    }

    public DynamicTableSelection dynamicSelection() {
        return dynamicSelection;
    }
}
