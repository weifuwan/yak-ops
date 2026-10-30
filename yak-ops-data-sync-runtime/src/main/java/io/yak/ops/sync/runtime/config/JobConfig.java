package io.yak.ops.sync.runtime.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.sync.runtime.solution.SolutionType;
import io.yak.ops.sync.runtime.solution.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated internal execution plan derived exclusively from V5 PipelineDef. */
public final class JobConfig {
    public static final int MAX_TABLES = 100;

    private static final Option<Map<String, Object>> STARTUP =
            Options.key("startup").mapObjectType().noDefaultValue();
    private static final Option<String> MODE = Options.key("mode").stringType().noDefaultValue();
    private static final Option<String> TABLE_SCOPE =
            Options.key("pipeline.table-scope").stringType().noDefaultValue();
    private static final Option<String> STARTUP_FILE =
            Options.key("file").stringType().noDefaultValue();
    private static final Option<Long> STARTUP_POSITION =
            Options.key("pos").longType().noDefaultValue();
    private static final Option<String> STARTUP_GTID =
            Options.key("gtidSet").stringType().noDefaultValue();
    private static final Option<Long> STARTUP_TIMESTAMP =
            Options.key("timestampMillis").longType().noDefaultValue();
    private static final Option<Long> STARTUP_SKIP_EVENTS =
            Options.key("skipEvents").longType().noDefaultValue();
    private static final Option<Long> STARTUP_SKIP_ROWS =
            Options.key("skipRows").longType().noDefaultValue();
    private static final OptionRule STARTUP_RULE = OptionRule.builder()
            .required(MODE)
            .optional(
                    STARTUP_FILE,
                    STARTUP_POSITION,
                    STARTUP_GTID,
                    STARTUP_TIMESTAMP,
                    STARTUP_SKIP_EVENTS,
                    STARTUP_SKIP_ROWS)
            .build();

    private static final Option<List<Object>> TABLES =
            Options.key("tables").listType(Object.class).noDefaultValue();
    private static final Option<Map<String, Object>> SCHEMA_POLICY =
            Options.key("schemaPolicy").mapObjectType().noDefaultValue();
    private static final Option<String> INITIAL =
            Options.key("initial").stringType().noDefaultValue();
    private static final Option<Map<String, Object>> EVOLUTION =
            Options.key("evolution").mapObjectType().noDefaultValue();
    private static final Option<Boolean> ENABLED =
            Options.key("enabled").booleanType().noDefaultValue();
    private static final Option<Boolean> ALLOW_DROP_COLUMNS =
            Options.key("allowDropColumns").booleanType().noDefaultValue();

    private static final OptionRule SCHEMA_RULE =
            OptionRule.builder().required(INITIAL, EVOLUTION).build();
    private static final OptionRule EVOLUTION_RULE =
            OptionRule.builder().required(ENABLED, ALLOW_DROP_COLUMNS).build();

    private final ReadonlyConfig parameters;
    private final SolutionType solutionType;
    private final List<TableConfig> tables;
    private final List<Step> steps;
    private final boolean schemaChangesEnabled;
    private final boolean allowDropColumns;

    private JobConfig(
            ReadonlyConfig parameters,
            SolutionType solutionType,
            List<TableConfig> tables,
            List<Step> steps,
            boolean schemaChangesEnabled,
            boolean allowDropColumns) {
        this.parameters = parameters;
        this.solutionType = solutionType;
        this.tables = List.copyOf(tables);
        this.steps = List.copyOf(steps);
        this.schemaChangesEnabled = schemaChangesEnabled;
        this.allowDropColumns = allowDropColumns;
    }

    /** Converts the one persisted V5 pipeline into typed, per-table execution inputs. */
    public static JobConfig from(ReadonlyConfig parameters) {
        Objects.requireNonNull(parameters, "parameters");
        io.yak.ops.core.definition.PipelineDef definition = new io.yak.ops.core.parser.JsonPipelineDefinitionParser()
                .parse(io.yak.ops.configuration.ConfigParser.toJson(parameters));
        String sourceType = definition.getSource().getType();
        String sinkType = definition.getSink().getType();
        if (!"JDBC".equals(sinkType) || (!"JDBC".equals(sourceType) && !"MYSQL_CDC".equals(sourceType))) {
            throw new IllegalArgumentException("Unsupported pipeline connector combination");
        }
        boolean realtime = "MYSQL_CDC".equals(sourceType);
        ReadonlyConfig from = definition.getSource().getConfig();
        ReadonlyConfig to = definition.getSink().getConfig();
        String fromId = from.get(JobOptions.DATASOURCE_ID);
        String toId = to.get(JobOptions.DATASOURCE_ID);
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            throw new IllegalArgumentException("Source and Sink datasource references are required");
        }
        List<Object> rawSources = from.get(TABLES);
        List<Object> rawSinks = to.get(TABLES);
        if (rawSources.isEmpty()
                || rawSources.size() > MAX_TABLES
                || rawSources.size() != rawSinks.size()
                || rawSources.size() != definition.getRoute().size()) {
            throw new IllegalArgumentException("Invalid pipeline table mappings");
        }
        // Product scope is explicit in V5 and must not change when tables are added or removed.
        // An omitted scope is accepted only for earlier V5 definitions, never for V4 input.
        String scope = definition.getConfig().get(TABLE_SCOPE);
        if (scope == null) scope = rawSources.size() == 1 ? "SINGLE" : "DATABASE";
        if (!"SINGLE".equals(scope) && !"DATABASE".equals(scope)) {
            throw new IllegalArgumentException("Unsupported pipeline.table-scope");
        }
        if ("SINGLE".equals(scope) && rawSources.size() != 1) {
            throw new IllegalArgumentException("SINGLE table scope requires exactly one table");
        }
        SolutionType type = "SINGLE".equals(scope)
                ? (realtime ? SolutionType.SINGLE_TABLE_REALTIME : SolutionType.SINGLE_TABLE_OFFLINE)
                : (realtime ? SolutionType.DATABASE_REALTIME : SolutionType.DATABASE_OFFLINE);
        List<Step> dataSteps;
        StartupOptions startupOptions = null;
        Map<String, Object> startupSettings = from.get(STARTUP);
        if (realtime) {
            if (from.getSourceMap().containsKey("syncMode")) {
                throw new IllegalArgumentException("CDC pipelines use startup, not syncMode");
            }
            String requested = startupSettings == null ? "INITIAL" : String.valueOf(startupSettings.get("mode"));
            String mode =
                    switch (requested) {
                        case "INITIAL" -> "initial";
                        case "SNAPSHOT" -> "snapshot";
                        case "EARLIEST_OFFSET" -> "earliest-offset";
                        case "LATEST_OFFSET" -> "latest-offset";
                        case "SPECIFIC_OFFSETS" -> "specific-offset";
                        case "TIMESTAMP" -> "timestamp";
                        default -> throw new IllegalArgumentException("Unsupported CDC startup mode");
                    };
            Map<String, Object> normalized = startupSettings == null
                    ? new java.util.LinkedHashMap<>()
                    : new java.util.LinkedHashMap<>(startupSettings);
            normalized.put("mode", mode);
            ReadonlyConfig startup = ReadonlyConfig.fromMap(normalized);
            validate(startup, STARTUP_RULE, "source.startup");
            startupOptions = StartupOptions.from(
                    mode,
                    startup.get(STARTUP_FILE),
                    startup.get(STARTUP_POSITION),
                    startup.get(STARTUP_GTID),
                    startup.get(STARTUP_TIMESTAMP),
                    startup.get(STARTUP_SKIP_EVENTS),
                    startup.get(STARTUP_SKIP_ROWS));
            dataSteps = switch (requested) {
                case "INITIAL" -> List.of(Step.FULL, Step.INCREMENTAL);
                case "SNAPSHOT" -> List.of(Step.FULL);
                default -> List.of(Step.INCREMENTAL);
            };
        } else {
            if (startupSettings != null) throw new IllegalArgumentException("JDBC does not accept CDC startup");
            String syncMode = String.valueOf(from.getSourceMap().getOrDefault("syncMode", "FULL"));
            dataSteps = switch (syncMode) {
                case "FULL" -> List.of(Step.FULL);
                case "INCREMENTAL" -> List.of(Step.INCREMENTAL);
                case "FULL_AND_INCREMENTAL" -> List.of(Step.FULL, Step.INCREMENTAL);
                default -> throw new IllegalArgumentException("Unsupported JDBC syncMode");
            };
        }
        Object runtimeMode = definition.getConfig().getSourceMap().get("execution.runtime-mode");
        String expected = realtime && dataSteps.contains(Step.INCREMENTAL) ? "STREAMING" : "BATCH";
        if (runtimeMode != null && !expected.equals(runtimeMode)) {
            throw new IllegalArgumentException("Pipeline runtime mode conflicts with Source execution mode");
        }

        ReadonlyConfig schema = ReadonlyConfig.fromMap(to.get(SCHEMA_POLICY));
        validate(schema, SCHEMA_RULE, "sink.schemaPolicy");
        ReadonlyConfig evolution = ReadonlyConfig.fromMap(schema.get(EVOLUTION));
        validate(evolution, EVOLUTION_RULE, "sink.schemaPolicy.evolution");
        boolean evolve = evolution.get(ENABLED);
        boolean drop = evolution.get(ALLOW_DROP_COLUMNS);
        if (drop && !evolve) throw new IllegalArgumentException("DROP COLUMN requires Schema Evolution");
        if ((!realtime || dataSteps.equals(List.of(Step.FULL))) && evolve) {
            throw new IllegalArgumentException("Schema Evolution requires continuous CDC");
        }
        String initial = schema.get(INITIAL);
        if (!"VERIFY".equals(initial) && !"CREATE_OR_ALTER".equals(initial)) {
            throw new IllegalArgumentException("Unsupported target Schema preparation");
        }
        boolean prepare = "CREATE_OR_ALTER".equals(initial);
        if (prepare && !dataSteps.contains(Step.FULL)) {
            throw new IllegalArgumentException("Initial Schema preparation requires FULL");
        }

        Map<String, ReadonlyConfig> sources = new java.util.LinkedHashMap<>();
        Map<String, ReadonlyConfig> sinks = new java.util.LinkedHashMap<>();
        for (Object entry : rawSources) {
            ReadonlyConfig table = asConfig(entry);
            if (sources.putIfAbsent(tablePath(table), table) != null) {
                throw new IllegalArgumentException("Duplicate source table");
            }
        }
        for (Object entry : rawSinks) {
            ReadonlyConfig table = asConfig(entry);
            if (sinks.putIfAbsent(tablePath(table), table) != null) {
                throw new IllegalArgumentException("Duplicate sink table");
            }
        }
        List<TableConfig> routes = new ArrayList<>();
        for (io.yak.ops.core.definition.RouteDef route : definition.getRoute()) {
            ReadonlyConfig source = sources.remove(route.getSourceTable());
            ReadonlyConfig target = sinks.remove(route.getSinkTable());
            if (source == null || target == null) {
                throw new IllegalArgumentException("Route refers to an unknown or repeated table");
            }
            Map<String, Object> sourceTable = new java.util.LinkedHashMap<>(source.getSourceMap());
            sourceTable.put("id", route.getSourceTable());
            Map<String, Object> targetTable = new java.util.LinkedHashMap<>(target.getSourceMap());
            targetTable.put("input", route.getSourceTable());
            SourceConfig input =
                    SourceConfig.from(ReadonlyConfig.fromMap(sourceTable), fromId, type, dataSteps, startupOptions);
            SinkConfig output = SinkConfig.from(ReadonlyConfig.fromMap(targetTable), toId, type, dataSteps, evolve);
            routes.add(new TableConfig(
                    input.options(), output.options(), input.incremental(), output.targetPrimaryKeys()));
        }
        if (!sources.isEmpty() || !sinks.isEmpty()) {
            throw new IllegalArgumentException("Unmapped source or sink table");
        }
        List<Step> stages = new ArrayList<>();
        if (prepare) stages.add(Step.SCHEMA);
        stages.addAll(dataSteps);
        return new JobConfig(parameters, type, routes, stages, evolve, drop);
    }

    private static String tablePath(ReadonlyConfig table) {
        List<String> parts = new ArrayList<>();
        for (io.yak.ops.configuration.Option<String> option :
                List.of(JobOptions.DATABASE, JobOptions.SCHEMA, JobOptions.TABLE)) {
            String value = table.get(option);
            if (value != null) parts.add(value);
        }
        return String.join(".", parts);
    }

    private static ReadonlyConfig asConfig(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Table definition must be an object");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) throw new IllegalArgumentException("Table property must be a string");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) map;
        return ReadonlyConfig.fromMap(attributes);
    }

    private static void validate(ReadonlyConfig options, OptionRule rule, String section) {
        ConfigValidator.of(options).validate(rule);
        ConfigValidator.validateUnknownKeys(options, rule, section);
    }

    public ReadonlyConfig parameters() {
        return parameters;
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

    /** SCHEMA is a derived execution stage, never a second persisted user setting. */
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
