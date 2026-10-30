package io.yak.ops.sync.runtime.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.sync.runtime.solution.SolutionType;
import io.yak.ops.sync.runtime.solution.Step;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates one target route's write identity and transactional intent. */
public final class SinkConfig {
    private final ReadonlyConfig options;
    private final List<String> targetPrimaryKeys;

    @JsonCreator
    public SinkConfig(
            @JsonProperty("options") ReadonlyConfig options,
            @JsonProperty("targetPrimaryKeys") List<String> targetPrimaryKeys) {
        if (targetPrimaryKeys != null) targetPrimaryKeys = List.copyOf(targetPrimaryKeys);
        this.options = options;
        this.targetPrimaryKeys = targetPrimaryKeys;
    }

    @JsonProperty("options")
    public ReadonlyConfig options() {
        return options;
    }

    @JsonProperty("targetPrimaryKeys")
    public List<String> targetPrimaryKeys() {
        return targetPrimaryKeys;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof SinkConfig other)) return false;
        return java.util.Objects.equals(options, other.options)
                && java.util.Objects.equals(targetPrimaryKeys, other.targetPrimaryKeys);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(options, targetPrimaryKeys);
    }

    public static final Option<String> INPUT = Options.key("input").stringType().noDefaultValue();
    private static final Option<Map<String, Object>> WRITE =
            Options.key("write").mapObjectType().noDefaultValue();
    private static final Option<String> MODE = Options.key("mode").stringType().noDefaultValue();
    private static final Option<List<String>> PRIMARY_KEYS =
            Options.key("primaryKeys").listType(String.class).noDefaultValue();
    private static final Option<Map<String, Object>> TRANSACTION =
            Options.key("transaction").mapObjectType().noDefaultValue();

    public static final OptionRule TABLE_RULE = OptionRule.builder()
            .required(INPUT, Conditions.notBlank(INPUT))
            .required(JobOptions.TABLE, Conditions.notBlank(JobOptions.TABLE))
            .required(WRITE)
            .optional(JobOptions.DATABASE, JobOptions.SCHEMA)
            .build();
    private static final OptionRule WRITE_RULE = OptionRule.builder()
            .required(MODE)
            .optional(PRIMARY_KEYS, TRANSACTION)
            .build();
    private static final OptionRule TRANSACTION_RULE =
            OptionRule.builder().required(MODE).build();

    public static SinkConfig from(
            ReadonlyConfig target,
            String datasourceId,
            SolutionType type,
            List<Step> steps,
            boolean schemaChangesEnabled) {
        validate(target, TABLE_RULE, "sink.tables");
        ReadonlyConfig write = ReadonlyConfig.fromMap(target.get(WRITE));
        validate(write, WRITE_RULE, "sink.tables.write");
        JdbcWriteMode mode = JdbcWriteMode.valueOf(write.get(MODE));
        List<String> keys = write.getSourceMap().containsKey(PRIMARY_KEYS.key()) ? write.get(PRIMARY_KEYS) : null;
        if (keys != null
                && (schemaChangesEnabled
                        || keys.isEmpty()
                        || keys.stream().anyMatch(k -> k == null || k.isBlank())
                        || new HashSet<>(keys).size() != keys.size())) {
            throw new IllegalArgumentException("Target primary keys must be unique and schema evolution disabled");
        }
        Map<String, Object> transaction = write.get(TRANSACTION);
        boolean xa = false;
        if (transaction != null) {
            ReadonlyConfig options = ReadonlyConfig.fromMap(transaction);
            validate(options, TRANSACTION_RULE, "sink.tables.write.transaction");
            if (!"CHECKPOINT_XA".equals(options.get(MODE))) {
                throw new IllegalArgumentException("Unsupported Sink transaction protocol");
            }
            xa = true;
        }
        if (mode == JdbcWriteMode.RETRACT
                && (!xa
                        || keys != null
                        || type != SolutionType.SINGLE_TABLE_REALTIME
                        || !steps.contains(Step.INCREMENTAL)
                        || schemaChangesEnabled)) {
            throw new IllegalArgumentException(
                    "RETRACT requires realtime checkpoint XA, no target keys and no schema evolution");
        }
        if (xa && mode != JdbcWriteMode.RETRACT) {
            throw new IllegalArgumentException("Checkpoint XA requires RETRACT write mode");
        }
        if ((!type.supportsScheduling() || steps.contains(Step.INCREMENTAL))
                && mode != JdbcWriteMode.UPSERT
                && mode != JdbcWriteMode.RETRACT) {
            throw new IllegalArgumentException("Changelog and offline incremental require UPSERT");
        }
        Map<String, Object> physical = new LinkedHashMap<>(target.getSourceMap());
        physical.remove(INPUT.key());
        physical.remove(WRITE.key());
        physical.put(JobOptions.DATASOURCE_ID.key(), datasourceId);
        physical.put(JobOptions.TYPE.key(), "JDBC");
        physical.put(JdbcSinkOptions.WRITE_MODE.key(), mode.name());
        if (xa) physical.put(JdbcSinkOptions.XA_ENABLED.key(), true);
        return new SinkConfig(ReadonlyConfig.fromMap(physical), keys);
    }

    private static void validate(ReadonlyConfig settings, OptionRule rule, String component) {
        ConfigValidator.of(settings).validate(rule);
        ConfigValidator.validateUnknownKeys(settings, rule, component);
    }
}
