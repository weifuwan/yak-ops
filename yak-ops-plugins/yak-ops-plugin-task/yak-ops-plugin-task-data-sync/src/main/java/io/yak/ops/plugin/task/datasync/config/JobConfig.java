package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The single version-four public job contract and its admitted Source/Sink routes. */
public final class JobConfig {
    public static final int MAX_TABLES = 100;

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

    private static final OptionRule SOURCE_RULE =
            OptionRule.builder().required(JobOptions.DATASOURCE_ID, TABLES).build();
    private static final OptionRule SINK_RULE = OptionRule.builder()
            .required(JobOptions.DATASOURCE_ID, TABLES, SCHEMA_POLICY)
            .build();
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

    public static JobConfig from(ReadonlyConfig parameters) {
        Objects.requireNonNull(parameters, "parameters");
        validate(parameters, SolutionOptions.RULE, "DATA_SYNC");
        SolutionType type = parameters.get(SolutionOptions.SOLUTION_TYPE);
        List<String> requested = parameters.get(SolutionOptions.STEPS);
        if (requested.isEmpty() || requested.size() != new HashSet<>(requested).size()) {
            throw new IllegalArgumentException("Invalid synchronization steps");
        }
        List<Step> dataSteps = new ArrayList<>();
        for (String name : requested) {
            Step step = Step.valueOf(name);
            if (step == Step.SCHEMA) {
                throw new IllegalArgumentException("Initial schema preparation belongs to Sink");
            }
            dataSteps.add(step);
        }
        if (!type.supportsScheduling() && !dataSteps.equals(List.of(Step.FULL, Step.INCREMENTAL))) {
            throw new IllegalArgumentException("Realtime requires FULL and INCREMENTAL");
        }
        if (type.supportsScheduling()
                && !List.of(List.of(Step.FULL), List.of(Step.INCREMENTAL), List.of(Step.FULL, Step.INCREMENTAL))
                        .contains(dataSteps)) {
            throw new IllegalArgumentException("Unsupported offline data steps");
        }
        if (!parameters.get(SolutionOptions.TRANSFORM).isEmpty()
                || !parameters.get(SolutionOptions.ENV).isEmpty()) {
            throw new IllegalArgumentException("Transform and Env must be empty until explicitly supported");
        }

        ReadonlyConfig from = ReadonlyConfig.fromMap(parameters.get(SolutionOptions.SOURCE));
        ReadonlyConfig to = ReadonlyConfig.fromMap(parameters.get(SolutionOptions.SINK));
        validate(from, SOURCE_RULE, "source");
        validate(to, SINK_RULE, "sink");
        String fromId = from.get(JobOptions.DATASOURCE_ID);
        String toId = to.get(JobOptions.DATASOURCE_ID);
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            throw new IllegalArgumentException("Source and Sink datasource references are required");
        }
        ReadonlyConfig schema = ReadonlyConfig.fromMap(to.get(SCHEMA_POLICY));
        validate(schema, SCHEMA_RULE, "sink.schemaPolicy");
        ReadonlyConfig evolution = ReadonlyConfig.fromMap(schema.get(EVOLUTION));
        validate(evolution, EVOLUTION_RULE, "sink.schemaPolicy.evolution");
        boolean evolve = evolution.get(ENABLED);
        boolean drop = evolution.get(ALLOW_DROP_COLUMNS);
        if (drop && !evolve) {
            throw new IllegalArgumentException("DROP COLUMN requires Sink schema evolution");
        }
        if (type.supportsScheduling() && (evolve || drop)) {
            throw new IllegalArgumentException("Runtime schema evolution requires realtime");
        }
        String initial = schema.get(INITIAL);
        if (!"VERIFY".equals(initial) && !"CREATE_OR_ALTER".equals(initial)) {
            throw new IllegalArgumentException("Unsupported initial target schema preparation");
        }
        boolean prepare = "CREATE_OR_ALTER".equals(initial);
        if (prepare && !dataSteps.contains(Step.FULL)) {
            throw new IllegalArgumentException("Initial schema preparation requires FULL");
        }

        List<Object> sources = from.get(TABLES);
        List<Object> sinks = to.get(TABLES);
        if (sources.isEmpty()
                || sources.size() > MAX_TABLES
                || sources.size() != sinks.size()
                || (!type.supportsMultipleTables() && sources.size() != 1)) {
            throw new IllegalArgumentException("Unsupported explicit table count");
        }
        Map<String, ReadonlyConfig> targets = new HashMap<>();
        for (Object entry : sinks) {
            ReadonlyConfig target = asConfig(entry);
            String input = target.get(SinkConfig.INPUT);
            if (input == null || input.isBlank() || targets.putIfAbsent(input, target) != null) {
                throw new IllegalArgumentException("Duplicate or missing Sink input");
            }
        }

        Set<List<String>> sourceTables = new HashSet<>();
        Set<List<String>> sinkTables = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<TableConfig> routes = new ArrayList<>();
        for (Object entry : sources) {
            ReadonlyConfig source = asConfig(entry);
            String id = source.get(SourceConfig.ID);
            if (id == null || id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("Source table IDs must be unique and nonempty");
            }
            ReadonlyConfig target = targets.remove(id);
            if (target == null) throw new IllegalArgumentException("Sink input must reference a Source table");
            SourceConfig input = SourceConfig.from(source, fromId, type, dataSteps, evolve);
            SinkConfig output = SinkConfig.from(target, toId, input, type, dataSteps, evolve);
            if (!sourceTables.add(tableKey(source)) || !sinkTables.add(tableKey(target))) {
                throw new IllegalArgumentException("Duplicate physical table route");
            }
            routes.add(new TableConfig(
                    input.options(), output.options(), input.incremental(), output.targetPrimaryKeys()));
        }
        if (!targets.isEmpty()) throw new IllegalArgumentException("Unknown Sink input");
        List<Step> stages = new ArrayList<>();
        if (prepare) stages.add(Step.SCHEMA);
        stages.addAll(dataSteps);
        return new JobConfig(parameters, type, routes, stages, evolve, drop);
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

    private static List<String> tableKey(ReadonlyConfig config) {
        return java.util.Arrays.asList(
                config.get(JobOptions.DATABASE), config.get(JobOptions.SCHEMA), config.get(JobOptions.TABLE));
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

    public ReadonlyConfig source() {
        if (tables.size() != 1) throw new IllegalStateException("Multiple Source routes");
        return tables.getFirst().source();
    }

    public ReadonlyConfig sink() {
        if (tables.size() != 1) throw new IllegalStateException("Multiple Sink routes");
        return tables.getFirst().sink();
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
