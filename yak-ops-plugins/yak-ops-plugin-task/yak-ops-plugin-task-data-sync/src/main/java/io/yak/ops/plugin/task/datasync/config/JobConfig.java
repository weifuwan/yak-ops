package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable task parameters and their executable single-table Connector projection. */
public final class JobConfig {
    private final ReadonlyConfig parameters;
    private final ReadonlyConfig source;
    private final ReadonlyConfig sink;
    private final SolutionType solutionType;

    private JobConfig(
            ReadonlyConfig parameters, ReadonlyConfig source, ReadonlyConfig sink, SolutionType solutionType) {
        this.parameters = parameters;
        this.source = source;
        this.sink = sink;
        this.solutionType = solutionType;
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
        List<String> steps = parameters.get(SolutionOptions.STEPS);
        List<String> expected = type.steps().stream().map(Step::name).toList();
        if (steps.size() != new HashSet<>(steps).size() || !new HashSet<>(steps).equals(new HashSet<>(expected))) {
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
        if (mappings.size() != 1 || !(mappings.getFirst() instanceof Map<?, ?> mapping)) {
            throw new IllegalArgumentException("Single-table synchronization requires exactly one table mapping");
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
        validate(sourceTable, SolutionOptions.TABLE_RULE, "tableMappings.source");
        validate(targetTable, SolutionOptions.TABLE_RULE, "tableMappings.target");
        requireEmptyTransforms(parameters);
        Map<String, Object> input = new LinkedHashMap<>(sourceTable.getSourceMap());
        input.put("datasourceId", from.get(JobOptions.DATASOURCE_ID));
        input.put("type", type.sourceIdentifier());
        Map<String, Object> output = new LinkedHashMap<>(targetTable.getSourceMap());
        output.put("datasourceId", to.get(JobOptions.DATASOURCE_ID));
        output.put("type", "JDBC");
        output.put("writeMode", table.get(JdbcSinkOptions.WRITE_MODE).name());
        Map<String, Object> canonical = new LinkedHashMap<>(parameters.getSourceMap());
        canonical.put("steps", expected);
        return new JobConfig(
                ReadonlyConfig.fromMap(canonical), ReadonlyConfig.fromMap(input), ReadonlyConfig.fromMap(output), type);
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
        return source;
    }

    public ReadonlyConfig sink() {
        return sink;
    }

    public SolutionType solutionType() {
        return solutionType;
    }
}
