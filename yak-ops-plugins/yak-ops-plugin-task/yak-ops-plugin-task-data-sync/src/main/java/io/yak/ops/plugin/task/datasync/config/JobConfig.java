package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable task parameters and their executable table Connector projections. */
public final class JobConfig {
    private final ReadonlyConfig parameters;
    private final SolutionType solutionType;
    private final List<TableConfig> tables;

    /** Maximum explicit mappings admitted by one product task. */
    public static final int MAX_TABLES = 100;

    private JobConfig(
            ReadonlyConfig parameters, ReadonlyConfig source, ReadonlyConfig sink, SolutionType solutionType) {
        this.parameters = parameters;
        this.solutionType = solutionType;
        this.tables = List.of(new TableConfig(source, sink));
    }

    private JobConfig(ReadonlyConfig parameters, List<TableConfig> tables, SolutionType type) {
        this.parameters = parameters;
        this.tables = List.copyOf(tables);
        this.solutionType = type;
    }

    /** Reads validated product or stored version-two parameters without changing their persisted format. */
    public static JobConfig from(ReadonlyConfig parameters) {
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.getSourceMap().containsKey("solutionType")) {
            return solution(parameters);
        }
        return pipeline(parameters);
    }

    private static JobConfig solution(ReadonlyConfig parameters) {
        validate(parameters, SolutionOptions.RULE, "DATA_SYNC");
        SolutionType type = parameters.get(SolutionOptions.SOLUTION_TYPE);
        Object rawSchemaChanges = parameters.getSourceMap().get(SolutionOptions.SCHEMA_CHANGES_ENABLED.key());
        if (parameters.getSourceMap().containsKey(SolutionOptions.SCHEMA_CHANGES_ENABLED.key())
                && !(rawSchemaChanges instanceof Boolean)) {
            throw new IllegalArgumentException("schemaChangesEnabled must be a boolean");
        }
        if (type.supportsScheduling()
                && parameters.getSourceMap().containsKey(SolutionOptions.SCHEMA_CHANGES_ENABLED.key())) {
            throw new IllegalArgumentException("schemaChangesEnabled is available only for realtime tasks");
        }
        Object rawDrop = parameters.getSourceMap().get(SolutionOptions.ALLOW_DROP_COLUMNS.key());
        if (parameters.getSourceMap().containsKey(SolutionOptions.ALLOW_DROP_COLUMNS.key())
                && !(rawDrop instanceof Boolean)) {
            throw new IllegalArgumentException("allowDropColumns must be a boolean");
        }
        if (type.supportsScheduling()
                && parameters.getSourceMap().containsKey(SolutionOptions.ALLOW_DROP_COLUMNS.key())) {
            throw new IllegalArgumentException("allowDropColumns is available only for realtime tasks");
        }
        if (Boolean.TRUE.equals(rawDrop) && !parameters.get(SolutionOptions.SCHEMA_CHANGES_ENABLED)) {
            throw new IllegalArgumentException("allowDropColumns requires schemaChangesEnabled=true");
        }
        List<String> steps = parameters.get(SolutionOptions.STEPS);
        if (steps.size() != new HashSet<>(steps).size()) {
            throw new IllegalArgumentException("Duplicate synchronization step");
        }
        List<String> expected = new java.util.ArrayList<>();
        if (steps.contains(Step.SCHEMA.name())) expected.add(Step.SCHEMA.name());
        if (type.supportsScheduling()) {
            if (steps.contains(Step.FULL.name())) expected.add(Step.FULL.name());
            if (steps.contains(Step.INCREMENTAL.name())) expected.add(Step.INCREMENTAL.name());
            if (expected.isEmpty()
                    || expected.equals(List.of(Step.SCHEMA.name()))
                    || (expected.contains(Step.SCHEMA.name()) && !expected.contains(Step.FULL.name()))) {
                throw new IllegalArgumentException("Schema preparation requires initial full load");
            }
        } else {
            expected.addAll(type.steps().stream().map(Step::name).toList());
        }
        if (steps.size() != expected.size() || !new HashSet<>(steps).equals(new HashSet<>(expected))) {
            throw new IllegalArgumentException("Unsupported synchronization step combination");
        }
        ReadonlyConfig from = ReadonlyConfig.fromMap(parameters.get(JobOptions.SOURCE));
        ReadonlyConfig to = ReadonlyConfig.fromMap(parameters.get(JobOptions.SINK));
        validate(from, SolutionOptions.CONNECTION_RULE, "source");
        validate(to, SolutionOptions.CONNECTION_RULE, "sink");
        ReadonlyConfig selection = ReadonlyConfig.fromMap(parameters.get(SolutionOptions.TABLE_SELECTION));
        validate(selection, SolutionOptions.SELECTION_RULE, "tableSelection");
        if (!"EXPLICIT".equals(selection.get(SolutionOptions.SELECTION_MODE))) {
            throw new IllegalArgumentException("Only explicit table selection is supported");
        }
        List<Object> mappings = parameters.get(SolutionOptions.TABLE_MAPPINGS);
        if (mappings.isEmpty()
                || mappings.size() > MAX_TABLES
                || (!type.supportsMultipleTables() && mappings.size() != 1)) {
            throw new IllegalArgumentException("Unsupported explicit table mapping count");
        }
        List<TableConfig> tables = new java.util.ArrayList<>();
        java.util.Set<List<String>> sources = new HashSet<>();
        java.util.Set<List<String>> targets = new HashSet<>();
        for (Object entryValue : mappings) {
            if (!(entryValue instanceof Map<?, ?> mapping)) {
                throw new IllegalArgumentException("Table mapping must be an object");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (var entry : mapping.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Table mapping property must be a string");
                }
                values.put(key, entry.getValue());
            }
            ReadonlyConfig table = ReadonlyConfig.fromMap(values);
            validate(table, SolutionOptions.TABLE_MAPPING_RULE, "tableMappings");
            ReadonlyConfig sourceTable = ReadonlyConfig.fromMap(table.get(JobOptions.SOURCE));
            ReadonlyConfig targetTable = ReadonlyConfig.fromMap(table.get(SolutionOptions.TARGET));
            validate(sourceTable, SolutionOptions.SOURCE_TABLE_RULE, "tableMappings.source");
            validate(targetTable, SolutionOptions.TARGET_TABLE_RULE, "tableMappings.target");
            List<String> targetPrimaryKeys =
                    targetTable.getSourceMap().containsKey(SolutionOptions.TARGET_PRIMARY_KEYS.key())
                            ? targetTable.get(SolutionOptions.TARGET_PRIMARY_KEYS)
                            : null;
            if (targetPrimaryKeys != null && parameters.get(SolutionOptions.SCHEMA_CHANGES_ENABLED)) {
                throw new IllegalArgumentException("Runtime schema evolution is unsupported with custom target keys");
            }
            if (targetPrimaryKeys != null
                    && (targetPrimaryKeys.isEmpty()
                            || targetPrimaryKeys.stream().anyMatch(key -> key == null || key.isBlank())
                            || new HashSet<>(targetPrimaryKeys).size() != targetPrimaryKeys.size())) {
                throw new IllegalArgumentException("Target primary key columns must be unique and nonempty");
            }
            requireEmptyTransforms(parameters);
            Map<String, Object> input = new LinkedHashMap<>(sourceTable.getSourceMap());
            // Public mapping controls are translated to physical Connector options below.
            input.remove(SolutionOptions.KEYLESS.key());
            input.remove(SolutionOptions.CHUNK_KEY.key());
            input.remove(SolutionOptions.XA.key());
            input.put("datasourceId", from.get(JobOptions.DATASOURCE_ID));
            input.put("type", type.sourceIdentifier());
            Object rawKeyless = sourceTable.getSourceMap().get(SolutionOptions.KEYLESS.key());
            if (rawKeyless != null && !(rawKeyless instanceof Boolean)) {
                throw new IllegalArgumentException("keyless must be a boolean");
            }
            boolean keyless = sourceTable.get(SolutionOptions.KEYLESS);
            Object xa = sourceTable.getSourceMap().get(SolutionOptions.XA.key());
            String chunkKey = sourceTable.get(SolutionOptions.CHUNK_KEY);
            JdbcWriteMode mode = table.get(JdbcSinkOptions.WRITE_MODE);
            if (keyless) {
                if (type != SolutionType.SINGLE_TABLE_REALTIME
                        || !expected.containsAll(List.of(Step.FULL.name(), Step.INCREMENTAL.name()))
                        || parameters.get(SolutionOptions.SCHEMA_CHANGES_ENABLED)
                        || (mode != JdbcWriteMode.RETRACT && mode != JdbcWriteMode.UPSERT)
                        || chunkKey == null
                        || chunkKey.isBlank()) {
                    throw new IllegalArgumentException("Keyless CDC requires single-table FULL + INCREMENTAL, "
                            + "an explicit Chunk Key and disabled runtime schema changes");
                }
                if (mode == JdbcWriteMode.RETRACT && (!Boolean.TRUE.equals(xa) || targetPrimaryKeys != null)) {
                    throw new IllegalArgumentException("Keyless RETRACT requires XA and no target primary key");
                }
                if (mode == JdbcWriteMode.UPSERT && (xa != null || targetPrimaryKeys == null)) {
                    throw new IllegalArgumentException(
                            "Keyless UPSERT requires a verified target primary key without XA");
                }
                input.put(MySqlCdcSourceOptions.KEYLESS_ENABLED.key(), true);
                input.put(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN.key(), chunkKey);
            } else {
                if (xa != null
                        || mode == JdbcWriteMode.RETRACT
                        || sourceTable.getSourceMap().containsKey(SolutionOptions.CHUNK_KEY.key())) {
                    throw new IllegalArgumentException("RETRACT, XA and Chunk Key require explicit keyless CDC");
                }
            }
            Map<String, Object> output = new LinkedHashMap<>(targetTable.getSourceMap());
            output.remove(SolutionOptions.TARGET_PRIMARY_KEYS.key());
            output.put("datasourceId", to.get(JobOptions.DATASOURCE_ID));
            output.put("type", "JDBC");
            output.put("writeMode", mode.name());
            if (keyless && mode == JdbcWriteMode.RETRACT) output.put(JdbcSinkOptions.XA_ENABLED.key(), true);
            if (!sources.add(tableKey(sourceTable)) || !targets.add(tableKey(targetTable))) {
                throw new IllegalArgumentException("Source and target tables must be unique");
            }
            IncrementalConfig incremental = null;
            boolean selectedIncremental = expected.contains(Step.INCREMENTAL.name()) && type.supportsScheduling();
            if (selectedIncremental) {
                Map<String, Object> options = table.get(SolutionOptions.INCREMENTAL);
                if (options == null) throw new IllegalArgumentException("Incremental table settings are required");
                incremental = IncrementalConfig.from(options);
                if (table.get(JdbcSinkOptions.WRITE_MODE) != io.yak.ops.connector.jdbc.sink.JdbcWriteMode.UPSERT) {
                    throw new IllegalArgumentException("Offline incremental requires UPSERT");
                }
            } else if (table.getSourceMap().containsKey(SolutionOptions.INCREMENTAL.key())) {
                throw new IllegalArgumentException("Incremental settings require an offline incremental step");
            }
            tables.add(new TableConfig(
                    ReadonlyConfig.fromMap(input), ReadonlyConfig.fromMap(output), incremental, targetPrimaryKeys));
        }
        Map<String, Object> canonical = new LinkedHashMap<>(parameters.getSourceMap());
        canonical.put("steps", expected);
        return new JobConfig(ReadonlyConfig.fromMap(canonical), tables, type);
    }

    private static List<String> tableKey(ReadonlyConfig table) {
        return java.util.Arrays.asList(
                table.get(JobOptions.DATABASE), table.get(JobOptions.SCHEMA), table.get(JobOptions.TABLE));
    }

    /** Returns the complete immutable explicit table mapping collection. */
    public List<TableConfig> tables() {
        return tables;
    }

    /** Returns the shared source connection identity, independent of table cardinality. */
    public String sourceDataSourceId() {
        return tables.getFirst().source().get(JobOptions.DATASOURCE_ID);
    }

    /** Returns the shared destination connection identity. */
    public String targetDataSourceId() {
        return tables.getFirst().sink().get(JobOptions.DATASOURCE_ID);
    }

    private static JobConfig pipeline(ReadonlyConfig parameters) {
        validate(parameters, JobOptions.PIPELINE_RULE, "DATA_SYNC");
        Map<String, Object> input = parameters.get(JobOptions.SOURCE);
        Map<String, Object> output = parameters.get(JobOptions.SINK);
        requireEmptyTransforms(parameters);
        ReadonlyConfig source = ReadonlyConfig.fromMap(input);
        ReadonlyConfig sink = ReadonlyConfig.fromMap(output);
        String identifier = source.get(JobOptions.TYPE);
        if (!"JDBC".equals(sink.get(JobOptions.TYPE))) {
            throw new IllegalArgumentException("Stored DATA_SYNC Sink is not supported");
        }
        SolutionType type;
        if ("JDBC".equals(identifier)) {
            type = SolutionType.SINGLE_TABLE_OFFLINE;
        } else if ("MYSQL_CDC".equals(identifier)) {
            type = SolutionType.SINGLE_TABLE_REALTIME;
        } else {
            throw new IllegalArgumentException("Stored DATA_SYNC Source is not supported");
        }
        return new JobConfig(parameters, source, sink, type);
    }

    private static void requireEmptyTransforms(ReadonlyConfig parameters) {
        List<Object> transforms = parameters.get(JobOptions.TRANSFORM);
        if (transforms == null || !transforms.isEmpty()) {
            throw new IllegalArgumentException("DATA_SYNC requires empty transforms");
        }
    }

    private static void validate(ReadonlyConfig parameters, OptionRule rule, String component) {
        ConfigValidator.of(parameters).validate(rule);
        ConfigValidator.validateUnknownKeys(parameters, rule, component);
    }

    public ReadonlyConfig parameters() {
        return parameters;
    }

    public ReadonlyConfig source() {
        if (tables.size() != 1) throw new IllegalStateException("Use explicit table mappings for a multi-table task");
        return tables.getFirst().source();
    }

    public ReadonlyConfig sink() {
        if (tables.size() != 1) throw new IllegalStateException("Use explicit table mappings for a multi-table task");
        return tables.getFirst().sink();
    }

    /** Returns canonical requested steps, including optional target structure preparation. */
    public List<Step> steps() {
        if (!parameters.getSourceMap().containsKey("solutionType")) return solutionType.steps();
        return parameters.get(SolutionOptions.STEPS).stream().map(Step::valueOf).toList();
    }

    public SolutionType solutionType() {
        return solutionType;
    }

    /** Explicit opt-in only; previously stored tasks without this property stay disabled. */
    /** Previously saved jobs are non-destructive unless this property is explicitly true. */
    public boolean allowDropColumns() {
        return schemaChangesEnabled() && parameters.get(SolutionOptions.ALLOW_DROP_COLUMNS);
    }

    public boolean schemaChangesEnabled() {
        return !solutionType.supportsScheduling()
                && parameters.getSourceMap().containsKey(SolutionOptions.SOLUTION_TYPE.key())
                && parameters.get(SolutionOptions.SCHEMA_CHANGES_ENABLED);
    }
}
